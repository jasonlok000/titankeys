package com.atlas.engine;

// Copied from Numen (GPL-3.0, https://github.com/jlo-aug/numen, commit 7b1391c), which is a modified version of Pastiera.
// Numen's version is Kotlin and has a table for each language. TitanKeys is English only.

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** The word pairs that the user kept, in SQLite. */
final class UserNGramStore extends SQLiteOpenHelper implements UserNGramRepository {
    private static final String DATABASE_NAME = "user_ngrams.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_BIGRAMS = "bigrams";
    private static final String COL_PREFIX = "prefix";
    private static final String COL_NEXT_WORD = "next_word";
    private static final String COL_COUNT = "count";
    private static final String COL_LAST_USED = "last_used";

    /** Android does not back up this folder, so the pairs never leave the phone. */
    UserNGramStore(Context context) {
        super(context, new File(context.getNoBackupFilesDir(), DATABASE_NAME).getPath(), null, DATABASE_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_BIGRAMS + " ("
                + COL_PREFIX + " TEXT NOT NULL, "
                + COL_NEXT_WORD + " TEXT NOT NULL, "
                + COL_COUNT + " INTEGER NOT NULL, "
                + COL_LAST_USED + " INTEGER NOT NULL, "
                + "PRIMARY KEY (" + COL_PREFIX + ", " + COL_NEXT_WORD + "))");
        db.execSQL("CREATE INDEX " + TABLE_BIGRAMS + "_lookup ON " + TABLE_BIGRAMS
                + " (" + COL_PREFIX + ", " + COL_COUNT + " DESC, " + COL_LAST_USED + " DESC)");
    }

    /** This is the first version, so there is nothing to upgrade from. */
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    @Override public void learn(String prefix, String nextWord, long nowMs) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put(COL_PREFIX, prefix);
            values.put(COL_NEXT_WORD, nextWord);
            values.put(COL_COUNT, 0);
            values.put(COL_LAST_USED, nowMs);
            db.insertWithOnConflict(TABLE_BIGRAMS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
            db.execSQL("UPDATE " + TABLE_BIGRAMS + " SET " + COL_COUNT + " = " + COL_COUNT + " + 1, " + COL_LAST_USED + " = ?"
                    + " WHERE " + COL_PREFIX + " = ? AND " + COL_NEXT_WORD + " = ?", new Object[] { nowMs, prefix, nextWord });
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override public List<Prediction> predict(String prefix, int limit) {
        List<Prediction> results = new ArrayList<>();
        if (limit <= 0) return results;
        try (Cursor cursor = getReadableDatabase().query(TABLE_BIGRAMS,
                new String[] { COL_NEXT_WORD, COL_COUNT, COL_LAST_USED }, COL_PREFIX + " = ?", new String[] { prefix },
                null, null, COL_COUNT + " DESC, " + COL_LAST_USED + " DESC", Integer.toString(limit))) {
            int word = cursor.getColumnIndexOrThrow(COL_NEXT_WORD);
            int count = cursor.getColumnIndexOrThrow(COL_COUNT);
            int lastUsed = cursor.getColumnIndexOrThrow(COL_LAST_USED);
            while (cursor.moveToNext())
                results.add(new Prediction(cursor.getString(word), cursor.getInt(count), cursor.getLong(lastUsed)));
        }
        return results;
    }

    @Override public java.util.Map<String, Integer> keptStarting(String letters, int limit) {
        java.util.Map<String, Integer> results = new java.util.LinkedHashMap<>();
        if (limit <= 0 || letters.isEmpty()) return results;
        String like = letters.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT LOWER(" + COL_NEXT_WORD + ") AS word, SUM(" + COL_COUNT + ") AS kept FROM "
                + TABLE_BIGRAMS + " WHERE " + COL_NEXT_WORD + " LIKE ? ESCAPE '\\' GROUP BY word ORDER BY kept DESC LIMIT ?",
                new String[] { like, Integer.toString(limit) })) {
            while (cursor.moveToNext()) results.put(cursor.getString(0), cursor.getInt(1));
        }
        return results;
    }

    @Override public int deleteWord(String prefix, String nextWord) {
        return getWritableDatabase().delete(TABLE_BIGRAMS, COL_NEXT_WORD + " = ? COLLATE NOCASE OR " + COL_PREFIX + " = ?",
                new String[] { nextWord, prefix });
    }

    @Override public void clearAll() {
        getWritableDatabase().delete(TABLE_BIGRAMS, null, null);
    }
}
