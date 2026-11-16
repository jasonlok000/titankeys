import { Row, Screen, useRouteParams } from "ink";
import { arrowOutward } from "ink/icons";
import { transfer } from "../../../lib/transfer";

export default function BackupFailed() {
  const { reason } = useRouteParams();
  // The subtitle is the reason from BackupActivity.
  return <Screen title="Couldn't back up" subtitle={typeof reason === "string" && reason ? reason : "Couldn't save the file."}>
    <Row title="Try again" endIcon={arrowOutward} onPress={() => void transfer("backup", "failure")} />
  </Screen>;
}
