"""Accessible Settings probes. No coordinates or private settings are guessed."""
from contextlib import contextmanager
from pathlib import Path
import json
import re
import time
import xml.etree.ElementTree as ET

APP = 'com.titankeys.debug'
RECEIVER = APP + '/com.titankeys.keyboard.InputMethodsSet'
UI_PATH = '/data/local/tmp/tk-touch-ui.xml'


class Unobservable(Exception):
    """Focus, dump or test precondition was lost: never an app failure."""


class Missing(AssertionError):
    """A valid own-app hierarchy was searched without its expected node."""


def parse_nodes(xml):
    try:
        root = ET.fromstring(xml[xml.index('<'):])
    except (ValueError, ET.ParseError) as error:
        raise Unobservable('UI dump missing or malformed') from error
    nodes = [dict(n.attrib) for n in root.iter('node') if n.get('package') == APP]
    if not nodes:
        raise Unobservable('UI dump contains no Debug-owned nodes')
    return nodes


def matches(node, label=None, kind=None):
    description = node.get('content-desc', '')
    if label is not None and label not in (node.get('text', ''), description) and not description.startswith(label + ', '):
        return False
    if kind == 'toggle':
        return node.get('class') == 'android.widget.Switch' and node.get('checkable') == 'true'
    if kind == 'button':
        return node.get('class') in ('android.widget.Button', 'android.widget.RadioButton')
    if kind == 'choice':
        return node.get('class') == 'android.widget.RadioButton' and node.get('checkable') == 'true'
    if kind == 'editor':
        return node.get('class', '').endswith('EditText')
    if kind == 'action':
        return node.get('clickable') == 'true' and node.get('checkable') != 'true'
    return True


def bounds(node):
    found = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
    if not found:
        raise Unobservable('Accessible node has no usable bounds')
    rect = tuple(map(int, found.groups()))
    if rect[2] <= rect[0] or rect[3] <= rect[1]:
        raise Unobservable('Accessible node is clipped off screen')
    return rect


def method_state(reply):
    found = re.search(r'(?:data=")?on ([a-z0-9,-]+); typing ([a-z0-9-]+)(?:"|$|\r?\n)', reply)
    if not found:
        raise Unobservable('InputMethodsSet readback is unavailable')
    on, current = found.group(1).split(','), found.group(2)
    if current not in on:
        raise Unobservable('Input-method readback is inconsistent')
    return on, current


class PageUI:
    def __init__(self, checks, sleep=time.sleep):
        self.c, self.sleep = checks, sleep
        self.navigation_notes = []

    def own(self):
        window = self.c.adb('shell', 'dumpsys', 'window')
        front = next((line for line in window.splitlines() if 'mCurrentFocus' in line), '')
        if APP + '/' not in front or any(value in window for value in ('isKeyguardShowing=true', 'mShowingLockscreen=true')):
            raise Unobservable('Debug lost the front or the screen locked; no input sent')

    def snapshot(self):
        self.own()
        try:
            self.c.adb('shell', 'uiautomator', 'dump', UI_PATH)
            self.own()
            xml = self.c.adb('shell', 'cat', UI_PATH)
            self.own()
            return parse_nodes(xml)
        finally:
            self.c.adb('shell', 'rm', '-f', UI_PATH)

    def tap(self, node):
        if node.get('enabled', 'true') != 'true':
            raise Unobservable('Expected action is disabled')
        x0, y0, x1, y1 = bounds(node)
        self.own()
        self.c.adb('shell', 'input', 'tap', str((x0 + x1) // 2), str((y0 + y1) // 2))
        self.sleep(.5)

    def back(self):
        self.own()
        self.c.adb('shell', 'input', 'keyevent', 'BACK')
        self.sleep(.5)

    def open_list(self):
        self.own()
        self.c.adb('shell', 'am', 'start', '-W', '-n', APP + '/com.vandam.ink.MainActivity',
                   '--es', 'com.vandam.ink.notification.HREF', '/settings/languages')
        self.sleep(1)

    def scroll(self, nodes, down):
        scrolls = [n for n in nodes if n.get('scrollable') == 'true']
        if not scrolls:
            return False
        x0, y0, x1, y1 = bounds(max(scrolls, key=lambda n: (bounds(n)[2] - bounds(n)[0]) * (bounds(n)[3] - bounds(n)[1])))
        x = (x0 + x1) // 2
        high, low = y0 + (y1 - y0) // 4, y0 + (y1 - y0) * 3 // 4
        self.own()
        self.c.adb('shell', 'input', 'swipe', str(x), str(low if down else high), str(x), str(high if down else low), '350')
        self.sleep(.35)
        return True

    def find(self, label=None, kind=None):
        for down in (True, False):
            seen = set()
            for _ in range(24):
                nodes = self.snapshot()
                found = next((n for n in nodes if matches(n, label, kind)), None)
                if found:
                    return found
                fingerprint = tuple((n.get('text'), n.get('content-desc'), n.get('bounds')) for n in nodes)
                if fingerprint in seen or not self.scroll(nodes, down):
                    break
                seen.add(fingerprint)
            else:
                raise Unobservable('Bounded page search did not reach its end')
        raise Missing(f'Expected accessible node absent: {label or kind}')

    def state(self):
        return method_state(self.c.adb('shell', 'am', 'broadcast', '-n', RECEIVER, '--ez', 'read', 'true'))

    def set_methods(self, on, current):
        result = method_state(self.c.adb('shell', 'am', 'broadcast', '-n', RECEIVER, '--es', 'on', ','.join(on), '--es', 'current', current))
        if result != (on, current) or self.state() != (on, current):
            raise Unobservable('Input-method setup/readback did not agree')

    @contextmanager
    def methods(self, on, current):
        before = self.state()
        try:
            self.set_methods(on, current)
            yield
        finally:
            self.set_methods(*before)

    def open_language(self, label):
        self.open_list()
        self.tap(self.find(label, 'action'))
        self.find(label)
        # The page itself acknowledges its one-time onboarding note on entry.
        # Record ordinary navigation behavior; do not edit that preference.
        moved = assets()[0]['page']['moved']
        if any(matches(n, moved) for n in self.snapshot()):
            self.navigation_notes.append('one-time typing-settings note displayed and acknowledged by the page')

    def binding(self, method_id):
        self.own()
        info = '\n'.join(self.c.adb('shell', 'dumpsys', 'input_method').partition('mCurrentEditorInfo')[2].splitlines()[:14])
        return bool(re.search(r'com\.titankeys\.practiceMethod=' + re.escape(method_id) + r'(?:[\s;,]|$)', info)
                    and re.search(r'packageName=' + re.escape(APP) + r'(?:\s|$)', info))

    def back_to_list(self, label):
        title = assets()[0]['list']['title']
        for _ in range(3):
            self.back()
            nodes = self.snapshot()
            if any(matches(n, title) for n in nodes) and any(matches(n, label, 'action') for n in nodes):
                return
        raise Missing('Back did not return from the language page to the accessible Languages list')


def assets():
    root = Path(__file__).resolve().parents[3]
    text = json.loads((root / 'tools/im/page-text.json').read_text(encoding='utf-8'))
    pages = json.loads((root / 'android/assets/im/languages.json').read_text(encoding='utf-8'))
    def method(identifier):
        return json.loads((root / 'android/assets/im' / identifier / 'im.json').read_text(encoding='utf-8'))
    return text, pages, method


def shown_name(method):
    """Same script fallback as app/settings/languages/index.tsx shownName."""
    return method['nameEnglish'] if method.get('script') == 'Deva' else method['name']


def practice(ui, method_id, key, choose=False):
    text, _, method = assets()
    if choose:
        # TryIt supplies Button.selected even when false: T5 exposes both the
        # selected and unselected practice buttons as checkable RadioButtons.
        ui.tap(ui.find(shown_name(method(method_id)), 'choice'))
    editor = ui.find(kind='editor')
    if editor.get('text', '') not in ('', text['page']['typeHere']):
        raise Unobservable('Practice editor already has text; left untouched')
    ui.tap(editor)
    for _ in range(15):
        if ui.binding(method_id):
            break
        ui.sleep(.2)
    else:
        raise Missing('Practice editor does not expose its selected input method')
    ui.c.keyboard('debug')
    if not ui.binding(method_id):
        raise Unobservable('Practice focus changed while selecting Debug')
    ui.c.play(*['key:' + char for char in key], *(['key:space'] if choose else []))
    value = None
    for _ in range(60):
        if not ui.binding(method_id):
            raise Unobservable('Practice field changed during input; no cleanup input sent')
        value = ui.c.field()
        if value and value != text['page']['typeHere']:
            break
        ui.sleep(.25)
    if not value or value == text['page']['typeHere']:
        raise Unobservable('Practice produced no committed text within 15 s; loading/composition needs review')
    okay = bool(re.fullmatch(r'[\u3400-\u9fff]+\s*', value or '')) if choose else value in (key, key.upper())
    if value and len(value) <= 8 and (okay or value.strip().lower() == key):
        if not ui.binding(method_id) or ui.c.field() != value:
            raise Unobservable('Practice text/focus changed before cleanup; left untouched')
        ui.c.play(*['key:del' for _ in value])
    if not okay:
        raise Missing('Practice input did not produce the selected method output')
    return len(value)
