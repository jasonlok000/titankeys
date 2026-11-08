package com.atlas.engine;

import java.io.FileReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class EmojiPagesChecks {
    public static void main(String[] args) throws Exception {
        String source = "# comment\nFaces\t😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃\nFood\t🍏 🍎 🫜\nFlags\t🇬🇧 🏴‍☠️\n";
        List<EmojiPages.Group> groups = EmojiPages.parse(new StringReader(source), emoji -> !emoji.equals("🫜"));
        check(groups.size() == 3 && groups.get(0).name().equals("Faces"), "Each line must be one group, in order");
        check(groups.get(1).emoji().equals(List.of("🍏", "🍎")), "Emoji the phone cannot show must be left out");
        check(groups.get(2).emoji().equals(List.of("🇬🇧", "🏴‍☠️")), "Multi-part emoji must stay whole");

        EmojiPages pages = new EmojiPages(groups, List.of());
        check(pages.page().equals(List.of("😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂")), "The first page must hold 8 emoji");
        check(pages.group().equals("Faces"), "With no recent emoji the list must start at the first group");
        pages.turn(1);
        check(pages.page().equals(List.of("🙂", "🙃")), "Turning must show the rest of the group");
        pages.turn(1);
        check(pages.group().equals("Food"), "Turning past a group's last page must go on to the next group");
        pages.turn(-1);
        pages.turn(-1);
        pages.turn(-1);
        check(pages.group().equals("Flags"), "Turning back from the first page must wrap to the last");

        pages.jump(1);
        check(pages.group().equals("Faces") && pages.page().get(0).equals("😀"), "Jumping past the last group must wrap");
        pages.turn(1);
        pages.jump(1);
        check(pages.group().equals("Food"), "Jumping must go to the next group's first page");
        pages.jump(-1);
        check(pages.group().equals("Faces") && pages.page().get(0).equals("😀"), "Jumping back must open the group's first page");

        List<String> saved = new ArrayList<>();
        for (int i = 0; i < 10; i++) saved = pages.use(groups.get(0).emoji().get(i));
        saved = pages.use("😀");
        check(saved.size() == EmojiPages.RECENT_LIMIT && saved.get(0).equals("😀") && saved.indexOf("😀") == saved.lastIndexOf("😀"),
                "Recent emoji must hold the last 8 distinct emoji, newest first");
        check(pages.group().equals("Faces"), "Picking an emoji must not move the open page");
        pages.reset();
        check(pages.group().equals("Faces") && pages.page().equals(saved), "Reopening must start at the recent emoji");
        pages.jump(-1);
        check(pages.group().equals("Flags"), "The recent emoji must not add a group to jump through");

        EmojiPages restored = new EmojiPages(groups, List.of("🍎", "🫜", "", "🍎"));
        check(restored.page().equals(List.of("🍎", "😀", "😃", "😄", "😁", "😆", "😅", "🤣")),
                "A few recent emoji must share the first page with the first group");
        restored.turn(1);
        check(restored.page().equals(List.of("😂", "🙂", "🙃")), "The first group must carry on after the recent emoji");

        if (args.length > 0) {
            try (FileReader asset = new FileReader(args[0], StandardCharsets.UTF_8)) {
                List<EmojiPages.Group> all = EmojiPages.parse(asset, emoji -> true);
                int count = all.stream().mapToInt(group -> group.emoji().size()).sum();
                check(all.size() == 9 && count > 1500, "The emoji asset must hold every group: " + all.size() + ", " + count);
                check(all.stream().flatMap(group -> group.emoji().stream()).noneMatch(emoji -> emoji.contains("🏻")),
                        "The emoji asset must leave out skin tone variants");
            }
        }
        System.out.println("Emoji page checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
