import { transfer } from "../../../lib/transfer";
import { ConfirmPage } from "../../../lib/confirm";

/**
 * Restore (#65) asks first: settings take the backup's values, lists merge with the backup winning a clash, and TitanKeys
 * restarts. It deletes nothing, so it confirms with Confirm's look, and the picker opens from here.
 */
export default function Restore() {
  // Clean zone: about data, so plain.
  return <ConfirmPage title="Restore from a backup?"
    body="Your settings change to the backup's. Words, snippets, shorthand and app lists from it join yours, and nothing here is deleted. TitanKeys restarts when it's done."
    action="Pick the file" cancel="Never mind"
    run={() => transfer("restore", "confirm")} />;
}
