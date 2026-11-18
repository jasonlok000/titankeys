import { Screen, useRouteParams, useSnapshot } from "ink";
import { HowTo } from "../../../lib/how-to";
import { KeysDrawing } from "../../../lib/keys-drawing";
import { guidePracticeLabel } from "../../../lib/language-copy";
import { languagesOf, sharedKeyRoles } from "../../../lib/language-page";
import { state } from "../../../lib/titankeys";
import { TryIt } from "../../../lib/try-it";

export default function LanguageGuide() {
  const { language: id } = useRouteParams();
  const result = useSnapshot(state);
  const row = result.status === "ready" && languagesOf(result.data).find(each => each.language.id === id);
  if (!row) return <Screen title={guidePracticeLabel()} />;
  const { language, methods, on } = row;
  return <Screen title={guidePracticeLabel(language.id)} subtitle={language.name}>
    <HowTo language={language.id} methods={methods} />
    {methods.length === 1 ? <KeysDrawing im={methods[0]} /> : <KeysDrawing language={language.id} roles={sharedKeyRoles(methods)} />}
    <TryIt language={language.id} key={language.id} methods={on} />
  </Screen>;
}
