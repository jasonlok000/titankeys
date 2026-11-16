#!/usr/bin/env python3
"""Removes the options that only Julian's private Ink has, in the build copy only. Safe if they are absent.
Run from the root of a build copy, never from the repo itself: it edits files in place."""
import pathlib
import re


def edit(path, change):
    file = pathlib.Path(path)
    if not file.exists():
        return
    text = file.read_text(encoding="utf-8")
    updated = change(text)
    if updated != text:
        file.write_text(updated, encoding="utf-8", newline="\n")


patch = pathlib.Path("tools/ink/titankeys.patch")
capitalise = patch.exists() and "autoCapitalise?: boolean" in patch.read_text(encoding="utf-8")

for folder in ("app", "lib"):
    for file in pathlib.Path(folder).rglob("*.tsx"):
        edit(file, lambda text: re.sub(r" wrapSubtitle\b", "", text))
        # Kept where the branch's Ink patch passes it through to the field (tools/ink/titankeys.patch, 2026-11-13).
        if not capitalise:
            edit(file, lambda text: re.sub(r" autoCapitalise=\{false\}", "", text))
        edit(file, lambda text: text.replace('gap="content"', "gap={24}"))
edit("theme.ts", lambda text: re.sub(r", rowGap: \d+", "", text))
edit(
    "app/index.tsx",
    lambda text: re.sub(
        r"rightActions=\{\[.*?\]\}",
        'rightAction={{ icon: settingsIcon, onPress: () => replace("/settings") }}',
        text,
        count=1,
        flags=re.S,
    ),
)
