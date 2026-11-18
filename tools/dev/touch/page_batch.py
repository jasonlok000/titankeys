"""Languages accessibility, method cards, limits and practice; no spelling changes."""
import checks
import guide_checks
from page_probe import Missing, PageUI, Unobservable, assets, practice, shown_name


def probe(name, work):
    ui = PageUI(checks)
    def describe(detail):
        notes = getattr(ui, 'navigation_notes', [])
        return detail + ('; ' + '; '.join(notes) if notes else '')
    try:
        checks.record(name, True, describe(work(ui)))
    except Missing as error:
        checks.record(name, False, describe(str(error)))
    except Unobservable as error:
        checks.record(name, None, describe(str(error)))


def check_languages_snapshot():
    """Keep the existing visual check and shot, without its guessed-row fallback."""
    try:
        PageUI(checks).open_list()
        checks.record('settings-languages', None, 'Settings > Languages: enabled languages first, then scripts',
                      guide_checks.shot('settings-languages'))
    except Unobservable as error:
        checks.record('settings-languages', None, str(error))


def check_portuguese():
    def work(ui):
        wording, _, method = assets()
        pt = method('pt')
        with ui.methods(['en', 'pt'], 'en'):
            ui.open_language(shown_name(pt))
            ui.tap(ui.find(wording['settings']['variant'], 'action'))
            options = [ui.find(v['name'], 'choice') for v in pt['variants']]
            if sum(n.get('checked') == 'true' or n.get('selected') == 'true' for n in options) != 1:
                raise Missing('Spelling choices do not expose exactly one selected region')
            guide_checks.shot('pages-portuguese-regions')
            ui.back()
            ui.find(wording['page']['tryIt'])
            practice(ui, 'pt', 'o')
            ui.back_to_list(shown_name(pt))
        return 'Português row opens; Spelling exposes both regions and its selected choice; practice types; methods restored'
    probe('pages-portuguese-accessibility', work)


def check_chinese_groups_and_limit():
    def work(ui):
        wording, pages, method = assets()
        groups = pages['zh']['groups']
        ids = [identifier for group in groups for identifier in group['methods']]
        wanted = ['en', *ids[:4]]
        with ui.methods(wanted, 'en'):
            ui.open_language('中文')
            for group in groups:
                ui.find(group['title'])
            for identifier in ids[:4]:
                if ui.find(shown_name(method(identifier)), 'toggle').get('checked') != 'true':
                    raise Missing('Enabled method toggle is not checked: ' + identifier)
            fifth = ui.find(shown_name(method(ids[4])), 'toggle')
            if fifth.get('checked') != 'false':
                raise Missing('Fifth method is already shown checked')
            if fifth.get('enabled', 'true') == 'true':
                ui.tap(fifth)
            if ui.state() != (wanted, 'en'):
                raise Missing('A fifth method changed the saved four-method selection')
            if ui.find(shown_name(method(ids[4])), 'toggle').get('checked') != 'false':
                raise Missing('Fifth method toggle became checked')
            ui.find(wording['page']['methodsFull'])
            guide_checks.shot('pages-chinese-four-limit')
        return 'Chinese groups accessible; four checked methods; fifth stays off in UI and saved readback; methods restored'
    probe('pages-chinese-groups-limit', work)


def check_method_card_and_practice():
    def work(ui):
        wording, _, _ = assets()
        with ui.methods(['en', 'zh-pinyin', 'zh-cangjie'], 'en'):
            ui.open_language('中文')
            ui.tap(ui.find(wording['page']['how'], 'action'))
            ui.find(wording['page']['hideHow'], 'action')
            ui.find(wording['settings']['lazySounds'], 'toggle')
            guide_checks.shot('pages-chinese-card-open')
            ui.tap(ui.find(wording['page']['hideHow'], 'action'))
            ui.find(wording['page']['how'], 'action')
            practice(ui, 'zh-pinyin', 'ni', choose=True)
            practice(ui, 'zh-cangjie', 'q', choose=True)
            if ui.state() != (['en', 'zh-pinyin', 'zh-cangjie'], 'en'):
                raise Missing('Practice selection changed the global typing method')
            ui.back_to_list('中文')
        return 'Card opens/closes; Pinyin and Cangjie practice bind their editors and type Han output; global typing stays en; methods restored'
    probe('pages-method-card-practice', work)


BATCH = [check_languages_snapshot, check_portuguese, check_chinese_groups_and_limit, check_method_card_and_practice]
HANDS = ['Review retained page/card screenshots for clipping, overlap and key-map scaling.']
