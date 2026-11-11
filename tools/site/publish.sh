#!/usr/bin/env bash
# Publishes the site/ folder (the privacy policy, the news feed and the webpage) to the gh-pages branch, which GitHub
# Pages serves at https://jasonlok000.github.io/titankeys/. Run it from a clean checkout of the branch to publish.
# Usage: bash tools/site/publish.sh [remote]     (the remote defaults to "fork", then "origin")
set -euo pipefail
cd "$(dirname "$0")/../.."
remote=${1:-$(git remote | grep -qx fork && echo fork || echo origin)}
[ -f site/index.html ] && [ -f site/privacy/index.html ] && [ -f site/feed.json ] || { echo "site/ is incomplete"; exit 1; }
source=$(git rev-parse --short HEAD)
work=$(mktemp -d)
trap 'git worktree remove --force "$work" >/dev/null 2>&1 || rm -rf "$work"' EXIT
if git fetch -q "$remote" gh-pages 2>/dev/null; then
  git worktree add -q --detach "$work" FETCH_HEAD
else
  git worktree add -q --detach "$work"
  git -C "$work" checkout -q --orphan gh-pages
fi
git -C "$work" rm -rqf . >/dev/null 2>&1 || true
cp -R site/. "$work/"
touch "$work/.nojekyll"
git -C "$work" add -A
if git -C "$work" diff --cached --quiet; then
  echo "The site is already up to date."
  exit 0
fi
git -C "$work" commit -q -m "publish site from $source"
git -C "$work" push -q "$remote" HEAD:refs/heads/gh-pages
echo "Published site from $source to $remote/gh-pages."
