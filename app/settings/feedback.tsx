import { Row, Screen, openURL, useSnapshot } from "ink";
import { arrowOutward } from "ink/icons";
import { state } from "../../lib/atlas";

const address = "juleshall4@icloud.com";

function draft(subject: string, body: string) {
  return `mailto:${address}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
}

export default function Feedback() {
  const result = useSnapshot(state);
  const details = result.status === "ready"
    ? `Atlas version: ${result.data.version}\nPhone: ${result.data.phone}\nAndroid: ${result.data.android}`
    : "";
  return <Screen title="Send feedback" subtitle="Send me any feedback!">
    <Row title="Report a bug" endIcon={arrowOutward} onPress={() => void openURL(draft("Atlas bug report",
      `What happened?\n\n\nWhat did you expect to happen?\n\n\n${details}`))} />
    <Row title="Suggest an idea" endIcon={arrowOutward} onPress={() => void openURL(draft("Atlas idea", "What would you like Atlas to do?\n\n"))} />
  </Screen>;
}
