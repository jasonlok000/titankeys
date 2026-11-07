package com.atlas.engine;

import android.content.res.AssetManager;
import android.util.Log;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

/** Verifies the bundled version once at load time, and atomically repairs stale or damaged files. */
final class ModelAssets {
    static void install(AssetManager assets, Path directory) throws IOException {
        int repaired = 0;
        Set<String> paths = new HashSet<>();
        try (BufferedReader manifest = new BufferedReader(new InputStreamReader(
                assets.open("swipe/manifest.sha256"), StandardCharsets.UTF_8))) {
            if (!"physical-swipe-models-v1".equals(manifest.readLine())) throw new IOException("Unsupported swipe model manifest");
            String line;
            while ((line = manifest.readLine()) != null) {
                String[] entry = line.split(" ", 3);
                if (entry.length != 3 || !entry[0].matches("[a-f0-9]{64}")
                        || !entry[1].matches("[0-9]{1,12}") || !entry[2].matches("[a-zA-Z0-9_./-]+")
                        || entry[2].startsWith("/") || entry[2].contains("..") || !paths.add(entry[2]))
                    throw new IOException("Invalid swipe model manifest");
                long size = Long.parseLong(entry[1]);
                Path target = directory.resolve(entry[2]);
                if (matches(target, size, entry[0])) continue;
                Files.createDirectories(target.getParent());
                Path staging = Files.createTempFile(target.getParent(), "swipe-model-", ".tmp");
                try {
                    try (InputStream input = assets.open("swipe/" + entry[2]);
                         FileOutputStream output = new FileOutputStream(staging.toFile())) {
                        byte[] buffer = new byte[65536];
                        int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                        output.getFD().sync();
                    }
                    if (!matches(staging, size, entry[0])) throw new IOException("Bundled swipe model failed integrity check");
                    Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    repaired++;
                } finally { Files.deleteIfExists(staging); }
            }
        }
        if (paths.isEmpty()) throw new IOException("Empty swipe model manifest");
        Log.d("TouchpadSwipe", "Verified " + paths.size() + " swipe model assets; repaired " + repaired);
    }

    private static boolean matches(Path file, long size, String expected) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) != size) return false;
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException error) { throw new IOException("SHA-256 unavailable", error); }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder actual = new StringBuilder(64);
        for (byte value : digest.digest()) actual.append(Character.forDigit((value & 255) >>> 4, 16))
                .append(Character.forDigit(value & 15, 16));
        return expected.contentEquals(actual);
    }
}
