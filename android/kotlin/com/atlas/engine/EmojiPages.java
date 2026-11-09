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
 * The items that the Sym panel shows, in groups of pages. The recent emoji come first, at the start of the first group.
 * Up and down swipes turn pages inside a group; sideways swipes change the group. Symbols are one group, with no recent items.
 */
public final class EmojiPages {
    /** One item for each letter key. */
    public static final int PER_PAGE = 26;
    public static final int RECENT_LIMIT = 8;

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

    /** Back to the first page, with the recent emoji as they are now. */
    public void reset() {
        shown.clear();
        shown.addAll(groups);
        if (!shown.isEmpty() && !recents.isEmpty()) {
            List<String> first = new ArrayList<>(recents);
            first.addAll(shown.get(0).emoji());
            shown.set(0, new Group(shown.get(0).name(), List.copyOf(first)));
        }
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
        if (Math.abs(down) > Math.abs(across)) turn(down < 0 ? 1 : -1);
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
