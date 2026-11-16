import { back, navigate, replace } from "ink";
import { backup } from "./titankeys";

const failed = { backup: "/settings/data/backup-failed", restore: "/settings/data/restore-failed" } as const;

/**
 * Back up everything or Restore (#65): opens the file picker. Cancelling it stays on the current page. A failure shows its page
 * with the reason, which says, for a restore, whether the file was no TitanKeys backup, a damaged one or one from a newer
 * TitanKeys. Trying again from that page shows the new reason. A whole backup's restore restarts TitanKeys on
 * /settings/data/restored, so it never comes back here.
 */
export async function transfer(mode: "backup" | "restore", from: "data" | "confirm" | "failure") {
  let done;
  try {
    done = await backup(mode);
  } catch (error) {
    const destination = { path: failed[mode], params: { reason: error instanceof Error ? error.message : "" } };
    if (from === "data") navigate(destination);
    else replace(destination);
    return;
  }
  if (!done) return;
  // The result has no way back, so it starts a new stack.
  back();
  if (from !== "data") back();
  if (done === "backup") replace("/settings/data/backed-up");
  else replace({ path: "/settings/data/imported", params: { what: done } });
}

/** Leaves a result page for Your words. */
export function returnToData() {
  replace("/settings");
  navigate("/settings/data");
}
