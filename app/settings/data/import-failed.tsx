import { Row, Screen, useRouteParams } from "ink";
import { arrowOutward } from "ink/icons";
import { transfer } from "../../../lib/transfer";

/** Shown when no reason came with the failure. The import's own reasons are in BackupFormat.java. */
const NOT_A_BACKUP = "Yeah, that's not a TitanKeys backup. Pick the right file.";

export default function ImportFailed() {
  const { reason } = useRouteParams();
  return <Screen title="Couldn't import" subtitle={typeof reason === "string" && reason ? reason : NOT_A_BACKUP}>
    <Row title="Try again" endIcon={arrowOutward} onPress={() => void transfer("import", "failure")} />
  </Screen>;
}
