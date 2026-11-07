package com.atlas.engine;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** The calibration profiles, which each map where a key reads on the touchpad to where it is printed. */
public final class Calibration {
    public static final String OFF = "off", BUILT_IN = "atlas";

    public static final class Profile {
        public final String id, name;
        final TouchAlignment alignment;

        Profile(String id, String name, TouchAlignment alignment) {
            this.id = id;
            this.name = name;
            this.alignment = alignment;
        }
    }

    private static SharedPreferences preferences;
    private static TouchAlignment builtIn;
    private static final List<Profile> profiles = new ArrayList<>();
    private static String selected;

    public static void init(Context context) {
        if (preferences != null) return;
        preferences = context.getApplicationContext().getSharedPreferences("touch_alignment", Context.MODE_PRIVATE);
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                context.getAssets().open("calibration/titan-2-saved.profile"), java.nio.charset.StandardCharsets.UTF_8))) {
            builtIn = TouchAlignment.decode(reader.readLine());
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Could not load saved Titan 2 calibration", error);
        }
        try {
            JSONArray saved = new JSONArray(preferences.getString("profiles", "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject profile = saved.getJSONObject(i);
                profiles.add(new Profile(profile.getString("id"), profile.getString("name"),
                        TouchAlignment.decode(profile.getString("alignment"))));
            }
        } catch (JSONException | IllegalArgumentException error) {
            throw new IllegalStateException("Could not load calibration profiles", error);
        }
        // Versions before profiles saved only whether the built-in calibration was on.
        selected = preferences.getString("selected", preferences.getBoolean("enabled", true) ? BUILT_IN : OFF);
    }

    public static TouchAlignment selected() {
        if (selected.equals(BUILT_IN)) return builtIn;
        for (Profile profile : profiles) if (profile.id.equals(selected)) return profile.alignment;
        return TouchAlignment.IDENTITY;
    }

    public static String selectedId() { return selected; }

    public static List<Profile> profiles() { return new ArrayList<>(profiles); }

    public static void select(String id) {
        selected = id;
        preferences.edit().putString("selected", id).apply();
    }

    /** Saves a new profile and starts using it. */
    public static void add(String name, TouchAlignment alignment) {
        Profile profile = new Profile(UUID.randomUUID().toString(), name, alignment);
        profiles.add(profile);
        saveProfiles();
        select(profile.id);
    }

    /** Deleting the profile in use goes back to the built-in calibration. */
    public static void delete(String id) {
        profiles.removeIf(profile -> profile.id.equals(id));
        saveProfiles();
        if (selected.equals(id)) select(BUILT_IN);
    }

    private static void saveProfiles() {
        JSONArray saved = new JSONArray();
        try {
            for (Profile profile : profiles)
                saved.put(new JSONObject().put("id", profile.id).put("name", profile.name)
                        .put("alignment", profile.alignment.encode()));
        } catch (JSONException error) {
            throw new IllegalStateException("Could not save calibration profiles", error);
        }
        preferences.edit().putString("profiles", saved.toString()).apply();
    }
}
