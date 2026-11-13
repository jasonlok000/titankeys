import { wipeEverything } from "../../lib/atlas";
import { playHaptic } from "../../lib/app-haptics";
import { ConfirmPage } from "../../lib/confirm";

/**
 * Settings > Reset literally everything: TitanKeys as if freshly installed (FreshStart.kt). It wipes data, so it plays Gone
 * (docs/haptics.md), first, as TitanKeys restarts on its front page right after, where it says "Fresh start. Don't waste it."
 */
export default function ResetEverything() {
  return <ConfirmPage title="Reset literally everything?"
    body={"Settings, your words, snippets, shorthand, app lists and clipboard. All gone, like a fresh install. It can't be undone. "
      + "Swipes you already shared are deleted on the Share swipes page."}
    action="Wipe it all" cancel="Never mind"
    run={async () => {
      playHaptic("app_deleted");
      await wipeEverything();
    }} />;
}
