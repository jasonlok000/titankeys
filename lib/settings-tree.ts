/**
 * What ← and × mean in Settings (owner's decision, 2026-11-12):
 * - ← goes back up, to the page's parent in Settings. It never jumps to the front page.
 * - × leaves Settings for the front page. Only the top of Settings has it, and it has no ←.
 *
 * Ink's ← and Android's Back both take the page under this one, so the stack must hold each page's parents. Inside
 * Settings it does, as each page opens the next. A link from outside, such as a notification, the guide or the keyboard,
 * would open a page alone, so Ink puts its parents, from [parentOf], under it. app/settings/index.tsx hands it to Ink.
 */
const TOP = "/settings";

/** Pages that sit under another page and not under Settings itself. Every other page in Settings has TOP as its parent. */
const parents: Record<string, string> = {
  "/settings/contribute-about": "/settings/contribute",
  "/settings/contribute-delete": "/settings/contribute",
  "/settings/share-policy": "/settings/contribute",
  "/settings/rawlog-delete": "/settings/rawlog",
  "/settings/enter-apps": "/settings/enter-key",
  "/settings/enter-apps-add": "/settings/enter-apps",
  "/settings/exact-apps-add": "/settings/exact-apps",
  "/settings/hidden-bar-add": "/settings/hidden-bar",
  "/settings/guide-boring": "/settings/guide",
  "/settings/whats-new": "/settings/about",
  "/settings/licences": "/settings/about",
};

/** A page's parent in Settings, null for the top of Settings, or undefined for a page outside it. */
export function parentOf(path: string): string | null | undefined {
  if (path === TOP) return null;
  if (!path.startsWith(`${TOP}/`)) return undefined;
  if (parents[path]) return parents[path];
  // A page in a folder, such as /settings/data/reset, sits under the folder's own page.
  const folder = path.slice(0, path.lastIndexOf("/"));
  return folder.length > TOP.length ? folder : TOP;
}
