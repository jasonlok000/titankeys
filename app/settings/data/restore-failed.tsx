import { Row, Screen, useRouteParams } from "ink";
import { arrowOutward } from "ink/icons";
import { transfer } from "../../../lib/transfer";

/** Shown when no reason came with the failure. Restore's own reasons are in BackupFile.kt. */
const NOT_A_BACKUP = "Yeah, that's not a TitanKeys backup. Pick the right file.";

export default function RestoreFailed() {
  const { reason } = useRouteParams();
  // F: draft title.
  return <Screen title="Couldn't restore" subtitle={typeof reason === "string" && reason ? reason : NOT_A_BACKUP}>
    <Row title="Try again" endIcon={arrowOutward} onPress={() => void transfer("restore", "failure")} />
  </Screen>;
}
