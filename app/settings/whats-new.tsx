import { Screen, useSnapshot } from "ink";
import { state } from "../../lib/titankeys";
import { WhatsNew } from "../../lib/whats-new";

export default function WhatsNewPage() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title="What's new" />;
  return <WhatsNew version={result.data.version} from={result.data.notesFrom} />;
}
