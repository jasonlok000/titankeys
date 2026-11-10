import { snippetStarts } from "../../../lib/atlas";
import { ChoicePage } from "../../../lib/choice";

const options = snippetStarts.map(value => ({ value, label: value }));

export default function SnippetStart() {
  return <ChoicePage title="Start of each shortcut"
    subtitle="Every shortcut starts with this, like ;addr. Real words don't use it, so a shortcut never gets in the way of one."
    options={options}
    read={({ snippetStart }) => snippetStart}
    write={snippetStart => ({ snippetStart })} />;
}
