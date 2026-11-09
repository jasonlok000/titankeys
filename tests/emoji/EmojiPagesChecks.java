package com.atlas.engine;

import java.io.FileReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class EmojiPagesChecks {
    public static void main(String[] args) throws Exception {
        List<String> faces = new ArrayList<>();
        for (int i = 0; i < EmojiPages.PER_PAGE + 4; i++) faces.add(new String(Character.toChars(0x1F600 + i)));
        String source = "# comment\nFaces\t" + String.join(" ", faces) + "\nFood\t🍏 🍎 🫜\nFlags\t🇬🇧 🏴‍☠️\n";
        List<EmojiPages.Group> groups = EmojiPages.parse(new StringReader(source), emoji -> !emoji.equals("🫜"));
        check(groups.size() == 3 && groups.get(0).name().equals("Faces"), "Each line must be one group, in order");
        check(groups.get(1).emoji().equals(List.of("🍏", "🍎")), "Emoji the phone cannot show must be left out");
        check(groups.get(2).emoji().equals(List.of("🇬🇧", "🏴‍☠️")), "Multi-part emoji must stay whole");

        check(EmojiPages.PER_PAGE == 26, "A page must hold one item for each letter key");
        List<String> firstFaces = faces.subList(0, EmojiPages.PER_PAGE);
        List<String> lastFaces = faces.subList(EmojiPages.PER_PAGE, faces.size());

        EmojiPages pages = new EmojiPages(groups, List.of());
        check(pages.page().equals(firstFaces), "The first page must hold 26 emoji");
        check(pages.group().equals("Faces"), "With no recent emoji the list must start at the first group");
        check(pages.pageNumber() == 1 && pages.pageCount() == 2, "The first page of two must count as 1 of 2");

        pages.turn(1);
        check(pages.page().equals(lastFaces) && pages.pageNumber() == 2, "Turning must show the rest of the group");
        pages.turn(1);
        check(pages.group().equals("Faces") && pages.page().equals(firstFaces), "Turning past a group's last page must wrap inside that group");
        pages.turn(-1);
        check(pages.group().equals("Faces") && pages.page().equals(lastFaces), "Turning back from a group's first page must wrap inside that group");

        pages.jump(1);
        check(pages.group().equals("Food") && pages.page().equals(List.of("🍏", "🍎")), "Jumping must go to the next group's first page");
        check(pages.pageNumber() == 1 && pages.pageCount() == 1, "Page numbers must count inside the open group");
        pages.turn(1);
        check(pages.group().equals("Food") && pages.pageNumber() == 1, "A group with one page must stay on that page");
        pages.jump(1);
        pages.jump(1);
        check(pages.group().equals("Faces") && pages.page().equals(firstFaces), "Jumping past the last group must wrap");
        pages.jump(-1);
        check(pages.group().equals("Flags"), "Jumping back from the first group must wrap to the last");
        pages.jump(1);
        pages.turn(1);
        pages.jump(1);
        pages.jump(-1);
        check(pages.group().equals("Faces") && pages.page().equals(firstFaces), "Jumping back must open the group's first page");

        check(pages.swipe(-1, 0) && pages.group().equals("Food"), "A swipe to the left must open the next group");
        check(pages.swipe(1, 0) && pages.group().equals("Faces"), "A swipe to the right must open the previous group");
        check(pages.swipe(0, -1) && pages.group().equals("Faces") && pages.page().equals(lastFaces), "A swipe up must open the next page of the group");
        check(pages.swipe(0, 1) && pages.group().equals("Faces") && pages.page().equals(firstFaces), "A swipe down must open the previous page of the group");
        check(pages.swipe(.2f, -1) && pages.group().equals("Faces") && pages.page().equals(lastFaces), "A swipe that is mostly up must turn the page");
        check(pages.swipe(-1, .2f) && pages.group().equals("Food"), "A swipe that is mostly sideways must change the group");
        check(!pages.swipe(0, -1) && pages.group().equals("Food"), "A swipe in a group with one page must report no change");
        pages.reset();

        List<String> symbols = new ArrayList<>();
        for (int i = 0; i < EmojiPages.PER_PAGE + 1; i++) symbols.add(String.valueOf((char) ('!' + i)));
        EmojiPages oneGroup = new EmojiPages(List.of(new EmojiPages.Group("Symbols", symbols)), List.of());
        check(oneGroup.swipe(0, -1) && oneGroup.pageNumber() == 2, "Symbols must change page with a swipe up");
        check(!oneGroup.swipe(-1, 0) && oneGroup.pageNumber() == 2, "Symbols have no groups, so a sideways swipe must change nothing");
        oneGroup.jump(1);
        check(oneGroup.pageNumber() == 2, "Jumping with one group must change nothing");
        oneGroup.reset();
        check(oneGroup.pageNumber() == 1 && oneGroup.page().equals(symbols.subList(0, EmojiPages.PER_PAGE)), "Reopening must start at the first page");

        EmojiPages none = new EmojiPages(List.of(), List.of());
        check(none.page().isEmpty() && none.group().isEmpty() && none.pageCount() == 0 && !none.swipe(1, 0) && !none.swipe(0, 1),
                "An empty list must show nothing and report no change");

        List<String> saved = new ArrayList<>();
        for (int i = 0; i < 10; i++) saved = pages.use(groups.get(0).emoji().get(i));
        saved = pages.use(faces.get(0));
        check(saved.size() == EmojiPages.RECENT_LIMIT && saved.get(0).equals(faces.get(0)) && saved.indexOf(faces.get(0)) == saved.lastIndexOf(faces.get(0)),
                "Recent emoji must hold the last 8 distinct emoji, newest first");
        check(pages.group().equals("Faces") && pages.page().equals(firstFaces), "Picking an emoji must not move the open page");
        pages.reset();
        check(pages.group().equals("Faces") && pages.page().subList(0, EmojiPages.RECENT_LIMIT).equals(saved), "Reopening must start at the recent emoji");
        pages.jump(-1);
        check(pages.group().equals("Flags"), "The recent emoji must not add a group to jump through");

        EmojiPages restored = new EmojiPages(groups, List.of("🍎", "🫜", "", "🍎"));
        List<String> expected = new ArrayList<>(List.of("🍎"));
        expected.addAll(faces.subList(0, EmojiPages.PER_PAGE - 1));
        check(restored.page().equals(expected), "A few recent emoji must share the first page with the first group");
        restored.turn(1);
        check(restored.page().equals(faces.subList(EmojiPages.PER_PAGE - 1, faces.size())), "The first group must carry on after the recent emoji");

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
