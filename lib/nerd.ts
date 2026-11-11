/** The boring guide's nerd screen shows the first time only: once it has shown, it never shows again. */
export function nerdSplash(seen: boolean): boolean {
  return !seen;
}
