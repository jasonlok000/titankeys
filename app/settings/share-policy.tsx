import { back } from "ink";
import { declineContributions, setContribute } from "../../lib/connected";
import { Disclosure } from "../../lib/contribute";

/**
 * /settings/share-policy: the short policy before Share swipes turns on. Settings opens it from the switch, and the
 * guide's end screen opens it too. Confirming turns sharing on; Not now leaves it off. Either way it goes back.
 */
export default function SharePolicy() {
  return <Disclosure onAgree={() => void setContribute(true).then(back)} onDecline={() => void declineContributions().then(back)} />;
}
