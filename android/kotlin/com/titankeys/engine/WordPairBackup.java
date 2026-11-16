package com.titankeys.engine;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/**
 * The learned word pairs for Back up everything (#65), read and written through the store's own helper, so a restore onto a phone
 * that never learned a pair makes the same table. Restore puts the file's pairs in, the file winning a pair in both, and leaves
 * the phone's other pairs. TitanKeys restarts right after a restore, so the keyboard reads them fresh.
 */
public final class WordPairBackup {
    private WordPairBackup() {}

    /** One pair: [next] came after [first], [count] times, last at [lastUsed]. */
    public static final class Pair {
        public final String first;
        public final String next;
        public final int count;
        public final long lastUsed;

        public Pair(String first, String next, int count, long lastUsed) {
            this.first = first; this.next = next; this.count = count; this.lastUsed = lastUsed;
        }
    }

    public static List<Pair> all(Context context) {
        List<Pair> pairs = new ArrayList<>();
        try (UserNGramStore store = new UserNGramStore(context);
             Cursor cursor = store.getReadableDatabase().query("bigrams",
                     new String[] { "prefix", "next_word", "count", "last_used" }, null, null, null, null, null)) {
            while (cursor.moveToNext())
                pairs.add(new Pair(cursor.getString(0), cursor.getString(1), cursor.getInt(2), cursor.getLong(3)));
        }
        return pairs;
    }

    public static void put(Context context, List<Pair> pairs) {
        if (pairs.isEmpty()) return;
        try (UserNGramStore store = new UserNGramStore(context)) {
            SQLiteDatabase db = store.getWritableDatabase();
            db.beginTransaction();
            try {
                for (Pair pair : pairs) {
                    ContentValues values = new ContentValues();
                    values.put("prefix", pair.first);
                    values.put("next_word", pair.next);
                    values.put("count", pair.count);
                    values.put("last_used", pair.lastUsed);
                    db.insertWithOnConflict("bigrams", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
    }
}
