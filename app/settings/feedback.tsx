import { Field, Row, Screen, Section, navigate, useSnapshot } from "ink";
import { forceCrash, state } from "../../lib/titankeys";

/**
 * Settings > Send feedback: Report a bug and Suggest an idea open the report form (#97), which sends straight to us and
 * offers email if it can't (#74). No account needed.
 */
export default function Feedback() {
  const result = useSnapshot(state);
  const debug = result.status === "ready" && result.data.build === "debug";
  return <Screen title="Send feedback" subtitle="Goes straight to us from TitanKeys. A real person reads it.">
    <Row title="Report a bug" onPress={() => navigate("/settings/report")} />
    <Row title="Suggest an idea" onPress={() => navigate({ path: "/settings/report", params: { kind: "idea" } })} />
    {debug && <Section title="TitanKeys Debug">
      {/* Tests the crash offer: TitanKeys crashes, and the next open offers to send the report. */}
      <Field label="Crash TitanKeys" onPress={() => void forceCrash()}>{""}</Field>
    </Section>}
  </Screen>;
}
