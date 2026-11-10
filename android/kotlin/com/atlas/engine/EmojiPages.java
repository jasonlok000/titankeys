package com.atlas.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The items that the Sym panel shows, in groups of pages. Recently used emoji are a group of their own, which comes first
 * and opens by default. Until an emoji has been used there is no such group, and the first group opens.
 * Up and down swipes turn pages inside a group; sideways swipes change the group. Symbols are one group, with no recent items.
 */
public final class EmojiPages {
    /** One item for each letter key but Q, A and P, in the columns that the touchpad cannot read well. */
    public static final int PER_PAGE = 23;
    /** The Recent group holds one page, so a used emoji stays until a full page of newer ones replaces it. */
    public static final int RECENT_LIMIT = PER_PAGE;
    public static final String RECENT = "Recent";
    /** The Recent group's mark in the category strip: a clock. The group's own first emoji changes each time an emoji is used. */
    public static final String RECENT_SAMPLE = "🕘";

    public record Group(String name, List<String> emoji) {}

    private record Page(int group, int start) {}

    private final List<Group> groups;
    private final Set<String> known = new HashSet<>();
    private final List<String> recents = new ArrayList<>();
    private final List<Group> shown = new ArrayList<>();
    private final List<Page> pages = new ArrayList<>();
    private int page;

    public EmojiPages(List<Group> groups, List<String> recents) {
        this.groups = List.copyOf(groups);
        for (Group group : groups) known.addAll(group.emoji());
        for (String emoji : new LinkedHashSet<>(recents)) {
            if (known.contains(emoji) && this.recents.size() < RECENT_LIMIT) this.recents.add(emoji);
        }
        reset();
    }

    /** Reads one group per line, a name and its emoji split by spaces, keeping only emoji the phone can show. */
    public static List<Group> parse(Reader source, Predicate<String> shown) throws IOException {
        List<Group> groups = new ArrayList<>();
        BufferedReader lines = new BufferedReader(source);
        for (String line = lines.readLine(); line != null; line = lines.readLine()) {
            int tab = line.indexOf('\t');
            if (line.startsWith("#") || tab < 0) continue;
            List<String> emoji = new ArrayList<>();
            for (String item : line.substring(tab + 1).split(" ")) if (!item.isEmpty() && shown.test(item)) emoji.add(item);
            if (!emoji.isEmpty()) groups.add(new Group(line.substring(0, tab), List.copyOf(emoji)));
        }
        return groups;
    }

    /** Adds items from {@code spare} until the last page is full, so that no key on it is blank. */
    public static List<String> fillLastPage(List<String> items, List<String> spare) {
        List<String> filled = new ArrayList<>(items);
        int missing = Math.floorMod(-items.size(), PER_PAGE);
        for (String item : spare) {
            if (missing == 0) break;
            if (filled.contains(item)) continue;
            filled.add(item);
            missing--;
        }
        return filled;
    }

    /** True if a swipe of this shape turns the page inside a group, false if it changes the group. */
    public static boolean turnsPage(float across, float down) {
        return Math.abs(down) > Math.abs(across);
    }

    /** Back to the first page, with the recent emoji as they are now. */
    public void reset() {
        shown.clear();
        if (!recents.isEmpty()) shown.add(new Group(RECENT, List.copyOf(recents)));
        shown.addAll(groups);
        pages.clear();
        for (int group = 0; group < shown.size(); group++) {
            for (int start = 0; start < shown.get(group).emoji().size(); start += PER_PAGE) pages.add(new Page(group, start));
        }
        page = 0;
    }

    public List<String> page() {
        if (pages.isEmpty()) return List.of();
        Page current = pages.get(page);
        List<String> emoji = shown.get(current.group()).emoji();
        return emoji.subList(current.start(), Math.min(emoji.size(), current.start() + PER_PAGE));
    }

    public String group() {
        return pages.isEmpty() ? "" : shown.get(pages.get(page).group()).name();
    }

    /** One emoji for each group, in order, for the category strip. */
    public List<String> samples() {
        List<String> samples = new ArrayList<>();
        for (Group group : shown) {
            if (group.name().equals(RECENT)) samples.add(RECENT_SAMPLE);
            else if (!group.emoji().isEmpty()) samples.add(group.emoji().get(0));
        }
        return samples;
    }

    /** The open group's place in {@link #samples}. */
    public int groupIndex() {
        return pages.isEmpty() ? 0 : pages.get(page).group();
    }

    /** Each group's name, in the same order as {@link #samples}. */
    public List<String> groupNames() {
        return shown.stream().map(Group::name).toList();
    }

    /** The group at a place along the category strip, from 0 at its left end to 1 at its right end. */
    public int groupAt(float across) {
        return Math.max(0, Math.min(shown.size() - 1, (int) Math.floor(across * shown.size())));
    }

    /**
     * The group for a key that covers this part of the category strip. If the open group is under the key, it stays open,
     * so that pushing a key does not move what a finger on that key has already chosen.
     */
    public int groupUnder(float left, float right) {
        int open = groupIndex();
        if ((open + 1f) / shown.size() > left && (float) open / shown.size() < right) return open;
        return groupAt((left + right) / 2);
    }

    /** Opens the first page of a group. Returns true if a different group now shows. */
    public boolean open(int group) {
        if (pages.isEmpty() || group < 0 || group >= shown.size() || group == pages.get(page).group()) return false;
        page = firstPage(group);
        return true;
    }

    /** Which page of the open group this is, counted from 1. */
    public int pageNumber() {
        return pages.isEmpty() ? 0 : page - firstPage(pages.get(page).group()) + 1;
    }

    /** How many pages the open group has. */
    public int pageCount() {
        if (pages.isEmpty()) return 0;
        int group = pages.get(page).group();
        return (int) pages.stream().filter(candidate -> candidate.group() == group).count();
    }

    /** The next or previous page of the open group, wrapping at either end of that group. */
    public void turn(int direction) {
        if (pages.isEmpty()) return;
        int first = firstPage(pages.get(page).group());
        page = first + Math.floorMod(page - first + direction, pageCount());
    }

    /** The first page of the next or previous group, wrapping at either end. With one group, nothing changes. */
    public void jump(int direction) {
        if (shown.size() > 1 && !pages.isEmpty()) page = firstPage(Math.floorMod(pages.get(page).group() + direction, shown.size()));
    }

    /**
     * A swipe, in keys across and rows down. The pages follow the finger: sideways changes the group, and up or down
     * changes the page inside it. Returns true if a different page now shows.
     */
    public boolean swipe(float across, float down) {
        int before = page;
        if (turnsPage(across, down)) turn(down < 0 ? 1 : -1);
        else jump(across > 0 ? -1 : 1);
        return page != before;
    }

    private int firstPage(int group) {
        for (int index = 0; index < pages.size(); index++) if (pages.get(index).group() == group) return index;
        return page;
    }

    /** Records a picked emoji and returns the recent emoji to save. The open page stays the same until {@link #reset}. */
    public List<String> use(String emoji) {
        if (!known.contains(emoji)) return List.copyOf(recents);
        recents.remove(emoji);
        recents.add(0, emoji);
        if (recents.size() > RECENT_LIMIT) recents.remove(RECENT_LIMIT);
        return List.copyOf(recents);
    }
}
