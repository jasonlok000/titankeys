package com.titankeys.reference;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Runs on a plain JVM, so it can only see the parts of the reference app that have no Android code. */
public final class ReferenceChecks {
    private static final int NORMAL_WORDS = 300;
    private static final int STYLE_WORDS = 200;
    private static final int STYLE_PASSAGES = 4;
    private static final int STEPS = 15;
    /** The ASD-STE100 limit for a sentence in an instruction. */
    private static final int SENTENCE_WORDS = 20;

    private static int failures;

    private static void must(boolean condition, String message) {
        if (condition) return;
        failures++;
        System.out.println("FAIL " + message);
    }

    private static int words(String text) {
        return text.trim().split("\\s+").length;
    }

    private static void shortSentences(String text, String where) {
        for (String sentence : text.split("[.:\\n]")) {
            if (sentence.trim().isEmpty()) continue;
            must(words(sentence) <= SENTENCE_WORDS, where + " has a sentence that is too long: " + sentence.trim());
        }
    }

    private static void steps() {
        must(Prompts.STEPS.length == STEPS, "There must be " + STEPS + " steps");
        must(Prompts.FINISH_STEP == STEPS, "The last step must be the finish screen");
        must(Prompts.STEPS[Prompts.BACK_STEP - 1].label.endsWith("-back"), "BACK_STEP must point at the Back step");
        must(Prompts.STEPS[Prompts.FINISH_STEP - 1].label.endsWith("-finish"), "FINISH_STEP must point at the finish screen");
        Set<String> labels = new HashSet<>();
        for (int i = 0; i < Prompts.STEPS.length; i++) {
            Prompts.Step step = Prompts.STEPS[i];
            must(labels.add(step.label), "Labels must be unique: " + step.label);
            must(step.label.startsWith(String.format("%02d-", i + 1)), "A label must start with its step number: " + step.label);
            must(!step.title.isEmpty() && !step.instruction.isEmpty(), step.label + " needs a title and an instruction");
            shortSentences(step.instruction, step.label);
        }
    }

    private static void passages() {
        must(Prompts.STEPS[0].passage != null && words(Prompts.STEPS[0].passage) >= NORMAL_WORDS,
            "Step 1 needs a passage of at least " + NORMAL_WORDS + " words");
        must(Passages.STYLES.length >= STYLE_PASSAGES, "There must be at least " + STYLE_PASSAGES + " style passages");
        Set<String> seen = new HashSet<>();
        seen.add(Passages.NORMAL);
        for (String passage : Passages.STYLES) {
            must(words(passage) >= STYLE_WORDS, "A style passage is too short: " + words(passage) + " words");
            must(seen.add(passage), "Passages must differ");
        }
        // Each character here must be on the keys, or the owner has to stop and hunt for it.
        for (String passage : seen) {
            must(passage.chars().allMatch(c -> c >= ' ' && c <= '~'), "A passage must be plain ASCII");
        }
    }

    private static void styles() {
        List<String> picks = Arrays.asList(Prompts.STYLES);
        for (String pick : new String[] { "Keys, two thumbs", "Keys, one thumb", "Swipe only", "Keys and swipes", "Walking", "Fast" }) {
            must(picks.contains(pick), "Missing quick pick: " + pick);
        }
    }

    private static void screenText() {
        String[] texts = { Prompts.KEYBOARD_OK, Prompts.KEYBOARD_NOT_OK, Prompts.SETUP, Prompts.NOT_INSTALLED, Prompts.STYLE_WITH_PASSAGE,
            Prompts.STYLE_NO_PASSAGE, Prompts.STYLE_QUESTION, Prompts.PUSH_START, Prompts.PUSH_DONE, Prompts.TIMELINE_FAILED, Prompts.LEAVE_MESSAGE };
        for (String text : texts) shortSentences(text, "Screen text");
    }

    public static void main(String[] args) {
        steps();
        passages();
        styles();
        screenText();
        if (failures > 0) {
            System.out.println(failures + " checks failed");
            System.exit(1);
        }
        System.out.println("Reference checks passed");
    }
}
