import { useState } from "react";
import { Row, Screen, openURL, useSnapshot } from "ink";
import { arrowOutward, chevronRight } from "ink/icons";
import { state } from "../../lib/atlas";

const issues = "https://github.com/jasonlok000/titankeys/issues";

function draft(title: string, body: string) {
  return `${issues}/new?title=${encodeURIComponent(title)}&body=${encodeURIComponent(body)}`;
}

export default function Feedback() {
  const result = useSnapshot(state);
  const [error, setError] = useState("");
  const compose = async (title: string, body: string) => {
    setError("");
    try {
      await openURL(draft(title, body));
    } catch {
      setError(`Couldn't open your browser. Go to ${issues}.`);
    }
  };
  const details = result.status === "ready"
    ? `TitanKeys version: ${result.data.version}\nPhone: ${result.data.phone}\nAndroid: ${result.data.android}`
    : "";
  return <Screen title="Send feedback" subtitle={error || "Opens a GitHub issue. Needs a GitHub account."}>
    <Row title="Report a bug" endIcon={arrowOutward} onPress={() => void compose("Bug report",
      `What happened?\n\n\nWhat should have happened?\n\n\n${details}`)} />
    <Row title="Record a bug" endIcon={chevronRight} href="/settings/bug" />
    <Row title="Suggest an idea" endIcon={arrowOutward} onPress={() => void compose("Idea", "What would you like TitanKeys to do?\n\n")} />
  </Screen>;
}
