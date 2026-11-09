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

        check(pages.samples().equals(List.of(faces.get(0), "🍏", "🇬🇧")), "With no recent emoji each group must give its first emoji as a sample");
        check(pages.groupIndex() == 0, "The first group must be the open one");
        pages.jump(1);
        check(pages.groupIndex() == 1, "The open group's place must follow a jump");
        pages.jump(-1);

        List<String> saved = new ArrayList<>();
        for (String face : faces) saved = pages.use(face);
        saved = pages.use(faces.get(0));
        check(saved.size() == EmojiPages.RECENT_LIMIT && saved.get(0).equals(faces.get(0)) && saved.indexOf(faces.get(0)) == saved.lastIndexOf(faces.get(0)),
                "Recent emoji must hold one page of distinct emoji, newest first");
        check(pages.group().equals("Faces") && pages.page().equals(firstFaces), "Picking an emoji must not move the open page");
        pages.reset();
        check(pages.group().equals(EmojiPages.RECENT) && pages.page().equals(saved), "Reopening must open the Recent group, which holds only the recent emoji");
        check(pages.pageCount() == 1, "The Recent group must fit on one page");
        check(pages.samples().equals(List.of(EmojiPages.RECENT_SAMPLE, faces.get(0), "🍏", "🇬🇧")), "The Recent group's mark must be the first sample");
        check(pages.groupIndex() == 0, "The Recent group must be the first group");
        pages.jump(1);
        check(pages.group().equals("Faces") && pages.page().equals(firstFaces), "Recent emoji must not be mixed into the first group");
        check(pages.groupIndex() == 1, "The first real group must come after the Recent group");
        pages.jump(-1);
        check(pages.group().equals(EmojiPages.RECENT), "A swipe back from the first real group must open the Recent group");
        pages.jump(-1);
        check(pages.group().equals("Flags"), "A swipe back from the Recent group must wrap to the last group");

        EmojiPages restored = new EmojiPages(groups, List.of("🍎", "🫜", "", "🍎"));
        check(restored.group().equals(EmojiPages.RECENT) && restored.page().equals(List.of("🍎")), "Saved recent emoji must open as a group, without unknown or repeated emoji");
        restored.jump(1);
        check(restored.group().equals("Faces") && restored.page().equals(firstFaces), "A few recent emoji must not change the first group's pages");

        EmojiPages unused = new EmojiPages(groups, List.of());
        check(unused.group().equals("Faces") && unused.samples().size() == 3, "With no recent emoji there must be no Recent group, and the first real group must open");
        unused.use(faces.get(1));
        check(unused.group().equals("Faces"), "The Recent group must not appear while the panel is open");
        unused.reset();
        check(unused.group().equals(EmojiPages.RECENT) && unused.page().equals(List.of(faces.get(1))), "The Recent group must appear at the next opening");

        EmojiPages oneSymbolGroup = new EmojiPages(List.of(new EmojiPages.Group("Symbols", List.of("&"))), List.of());
        check(oneSymbolGroup.samples().size() == 1, "One group must give one sample, so the panel shows no category strip");

        check(EmojiPages.turnsPage(.2f, -1) && EmojiPages.turnsPage(.2f, 1), "A swipe that is mostly up or down must turn the page");
        check(!EmojiPages.turnsPage(1, .2f) && !EmojiPages.turnsPage(-1, 0), "A swipe that is mostly sideways must change the group");
        check(!EmojiPages.turnsPage(1, 1), "A diagonal swipe must change the group, as EmojiPages.swipe does");

        EmojiPages strip = new EmojiPages(groups, List.of());
        check(strip.groupAt(0) == 0 && strip.groupAt(.32f) == 0, "The left part of the strip must be the first group");
        check(strip.groupAt(.34f) == 1 && strip.groupAt(.5f) == 1, "The middle of the strip must be the middle group");
        check(strip.groupAt(.67f) == 2 && strip.groupAt(1) == 2, "The right end of the strip must be the last group");
        check(strip.groupAt(-.2f) == 0 && strip.groupAt(1.2f) == 2, "A place past either end of the strip must keep to that end");
        check(strip.open(1) && strip.group().equals("Food") && strip.pageNumber() == 1, "A chosen group must open at its first page");
        check(!strip.open(1) && strip.group().equals("Food"), "Choosing the open group must report no change");
        strip.open(0);
        strip.turn(1);
        check(strip.pageNumber() == 2 && strip.open(1) && strip.open(0) && strip.page().equals(firstFaces), "Opening a group must not keep the page from before");
        check(!strip.open(3) && !strip.open(-1) && strip.groupIndex() == 0, "A group that does not exist must change nothing");
        check(strip.groupNames().equals(List.of("Faces", "Food", "Flags")), "Each group must give its name");
        strip.open(1);
        check(strip.groupUnder(.3f, .6f) == 1, "A key over the open group must keep it open");
        check(strip.groupUnder(.8f, 1) == 2, "A key away from the open group must open the group at the key's centre");
        check(strip.groupUnder(0, .2f) == 0, "A key at the left end must open the first group");
        check(none.groupAt(.5f) == 0 && !none.open(0), "With no groups a place along the strip must change nothing");

        EmojiPages paged = new EmojiPages(groups, List.of());
        check(paged.swipe(0, -1) && paged.pageNumber() == 2, "What Backspace does, a swipe up, must open the next page");
        check(paged.swipe(0, 1) && paged.pageNumber() == 1, "What Enter does, a swipe down, must open the previous page");

        List<String> spare = new ArrayList<>();
        for (int i = 0; i < EmojiPages.PER_PAGE; i++) spare.add("s" + i);
        check(EmojiPages.fillLastPage(symbols.subList(0, 24), spare).size() == EmojiPages.PER_PAGE, "A short page of symbols must fill to a full page");
        check(EmojiPages.fillLastPage(symbols.subList(0, 24), spare).subList(0, 24).equals(symbols.subList(0, 24)), "Filling must keep the first items in order");
        check(EmojiPages.fillLastPage(symbols.subList(0, EmojiPages.PER_PAGE), spare).size() == EmojiPages.PER_PAGE, "A full page must stay as it is");
        check(EmojiPages.fillLastPage(symbols, spare).size() == EmojiPages.PER_PAGE * 2, "A second page must also fill");
        check(EmojiPages.fillLastPage(List.of("&"), List.of("&", "%")).equals(List.of("&", "%")), "Filling must not repeat an item");
        check(EmojiPages.fillLastPage(List.of("&"), List.of()).equals(List.of("&")), "With nothing spare the page must stay short");

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
