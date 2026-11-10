import { snippetStarts } from "../../../lib/atlas";
import { ChoicePage } from "../../../lib/choice";

const options = snippetStarts.map(value => ({ value, label: value }));

export default function SnippetStart() {
  return <ChoicePage title="Start of each shortcut"
    subtitle="Each shortcut starts with this character, such as ;addr. Words do not use it, so a shortcut does not change a word."
    options={options}
    read={({ snippetStart }) => snippetStart}
    write={snippetStart => ({ snippetStart })} />;
}
