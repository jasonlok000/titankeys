import { Button, Row, Screen, navigate, useAction, useSnapshot } from "ink";
import { add, close } from "ink/icons";
import { type State, beginCalibration, state, update } from "../../../lib/atlas";

const builtIn = "Atlas Calibration";

export const calibrationLabel = ({ calibration, profiles }: State) =>
  calibration === "off" ? "Off" : profiles.find(({ id }) => id === calibration)?.name ?? builtIn;

export default function CalibrationProfile() {
  const result = useSnapshot(state);
  const save = useAction(update);
  const selected = result.status === "ready" ? result.data.calibration : undefined;
  const select = (calibration: string) => save.run({ calibration });
  return <Screen title="Calibration profile" subtitle="Lines up your touches with the letters printed on the keys.">
    <Button selected={selected === "off"} onPress={() => select("off")}>Off</Button>
    <Button selected={selected === "atlas"} onPress={() => select("atlas")}>{builtIn}</Button>
    {result.status === "ready" && result.data.profiles.map(({ id, name }) =>
      <Row key={id} title={name} subtitle={selected === id ? "Selected" : undefined} onPress={() => select(id)}
        endAction={{ icon: close, onPress: () => navigate({ path: "/settings/calibration/delete/[id]", params: { id } }) }} />)}
    <Row title="New profile" endIcon={add} onPress={() => {
      void beginCalibration().then(() => navigate("/settings/calibration/new"));
    }} />
  </Screen>;
}
