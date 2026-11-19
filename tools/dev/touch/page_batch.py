"""Languages accessibility, method cards, limits and practice; no spelling changes."""
import checks
import guide_checks
from page_probe import Missing, PageUI, Unobservable, assets, practice, shown_name, translator


def probe(name, work):
    ui = PageUI(checks)
    try:
        checks.record(name, True, work(ui))
    except Missing as error:
        checks.record(name, False, str(error))
    except Unobservable as error:
        checks.record(name, None, str(error))


def check_languages_snapshot():
    """Keep the existing visual check and shot, without its guessed-row fallback."""
    try:
        ui = PageUI(checks)
        ui.open_list()
        ui.find(assets()[0]['list']['all'])
        checks.record('settings-languages', None, 'Settings > Languages: enabled languages first, then All languages',
                      guide_checks.shot('settings-languages'))
    except Missing as error:
        checks.record('settings-languages', False, str(error))
    except Unobservable as error:
        checks.record('settings-languages', None, str(error))


def check_typing_scopes():
    def work(ui):
        text, _, _ = assets()
        settings = text['settings']
        editing = text['typing']['editing']
        with ui.methods(['en'], 'en'):
            ui.open_typing()
            ui.expect_absent(editing, 'action')
            ui.find(text['typing']['fixes'], 'toggle')
            for key in ('capitals', 'commaSpace', 'doubleSpace', 'swipe'):
                ui.find(settings[key], 'toggle')
            for key in ('sentenceSpaces', 'hold', 'shorthand'):
                ui.find(settings[key], 'action')
        with ui.methods(['en', 'zh-pinyin', 'yue-yale'], 'en'):
            ui.open_typing()
            ui.open_typing_picker()
            ui.find('English', 'choice')
            ui.expect_absent('Français', 'choice')
            ui.tap(ui.find('中文', 'choice'))
            translate = translator('zh')
            ui.find(translate(text['page']['languageFullStop']).replace('{stop}', '。'), 'toggle')
            ui.find(translate(settings['hold']), 'action')
            ui.find(settings['learn'], 'toggle')
            for key in ('fixes', 'capitals', 'swipe'):
                ui.expect_absent(translate(settings[key]), 'toggle')
            if ui.state() != (['en', 'zh-pinyin', 'yue-yale'], 'en'):
                raise Missing('Editing Chinese changed the active input method')
        return 'English has no picker when alone; the enabled-only picker opens Chinese typing controls without switching keyboards'
    probe('pages-typing-scopes', work)


def check_portuguese():
    def work(ui):
        _, _, method = assets()
        pt = method('pt')
        with ui.methods(['en', 'pt'], 'en'):
            ui.variant_options('pt')
            guide_checks.shot('pages-portuguese-regions')
            ui.back()
            ui.open_guide('pt')
            practice(ui, 'pt', 'o')
            ui.back_to_list(shown_name(pt))
        return 'Português row opens; Spelling exposes both regions and its selected choice; practice types; methods restored'
    probe('pages-portuguese-accessibility', work)


def check_chinese_groups_and_limit():
    def work(ui):
        wording, pages, method = assets()
        translate = translator('zh')
        groups = pages['zh']['groups']
        ids = [identifier for group in groups for identifier in group['methods']]
        wanted = ['en', *ids[:4]]
        with ui.methods(wanted, 'en'):
            ui.open_language('中文', 'zh')
            for group in groups:
                ui.find(translate(group['title']))
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
            ui.find(translate(wording['page']['methodsFull']))
            guide_checks.shot('pages-chinese-four-limit')
        return 'Chinese groups accessible; four checked methods; fifth stays off in UI and saved readback; methods restored'
    probe('pages-chinese-groups-limit', work)


def check_method_card_and_practice():
    def work(ui):
        wording, _, method = assets()
        translate = translator('yue-yale')
        yale = shown_name(method('yue-yale'))
        details = yale + ' · ' + translate(wording['page']['how'])
        hide = yale + ' · ' + translate(wording['page']['hideHow'])
        wanted = ['en', 'ru', 'zh-pinyin', 'zh-cangjie']
        with ui.methods(wanted, 'ru'):
            ui.open_language('中文', 'zh')
            ui.tap(ui.find(details, 'action'))
            ui.find(hide, 'action')
            ui.find(translate(wording['settings']['lazySounds']), 'toggle')
            ui.expect_absent(translate(wording['settings']['keyPreview']), 'toggle')
            guide_checks.shot('pages-chinese-card-open')
            ui.tap(ui.find(hide, 'action'))
            ui.find(details, 'action')
            cangjie = shown_name(method('zh-cangjie'))
            cangjie_text = translator('zh-cangjie')
            ui.tap(ui.find(cangjie + ' · ' + cangjie_text(wording['page']['how']), 'action'))
            ui.find(cangjie_text(wording['settings']['keyPreview']), 'toggle')
            ui.open_guide('zh')
            ui.find(translator('zh')(wording['page']['keys']))
            practice(ui, 'zh-pinyin', 'ni', choose=True)
            practice(ui, 'zh-cangjie', 'q', choose=True)
            if ui.state() != (wanted, 'ru'):
                raise Missing('Practice selection changed the global typing method')
            ui.back_to_list('中文')
            ui.verify_ordinary_key('q', 'й')
        return 'Pinyin and Cangjie practice type Han output; Q types й in a normal editor afterward; original methods restored'
    probe('pages-method-card-practice', work)


BATCH = [check_languages_snapshot, check_typing_scopes, check_portuguese, check_chinese_groups_and_limit, check_method_card_and_practice]
HANDS = ['Review retained page/card screenshots for clipping, overlap and key-map scaling.']
