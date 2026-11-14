import { useEffect } from "react";
import { Screen, replace } from "ink";
import { onAppWindow, openGuideEnd } from "../../lib/atlas";

/** How long after it opens the end again that this page counts its window coming to the front as the end having closed. */
const SETTLE_MILLIS = 1500;

/**
 * The guide's end, under each page that it opens (lib/settings-tree.ts). Back from the page uncovers this, and it opens the end
 * again, as it was left. Ink keeps a covered page's effects off, so it does nothing while the page is on top. Once that end
 * closes, the app's front page takes this one's place.
 */
export default function EndReturn() {
  useEffect(() => {
    void openGuideEnd();
    const opened = Date.now();
    return onAppWindow(() => {
      if (Date.now() - opened > SETTLE_MILLIS) replace("/");
    });
  }, []);
  return <Screen />;
}
