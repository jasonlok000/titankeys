import { Row, Screen } from "ink";
import { arrowOutward } from "ink/icons";
import { transfer } from "../../../lib/transfer";

export default function ExportFailed() {
  return <Screen title="Couldn't export" subtitle="Couldn't save the file.">
    <Row title="Try again" endIcon={arrowOutward} onPress={() => void transfer("export", "failure")} />
  </Screen>;
}
