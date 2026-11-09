package com.titankeys.reference;

/** The steps of a reference session and the words on screen. It has no Android code, so tests/reference can check it. */
final class Prompts {
    private Prompts() {}

    static final class Step {
        /** Sent with each mark. Tools read it, so a step keeps its label once recordings exist. */
        final String label;
        final String title;
        final String instruction;
        final String passage;

        Step(String label, String title, String instruction, String passage) {
            this.label = label;
            this.title = title;
            this.instruction = instruction;
            this.passage = passage;
        }

        Step(String label, String title, String instruction) {
            this(label, title, instruction, null);
        }
    }

    static final Step[] STEPS = {
        new Step("01-normal-typing", "Normal typing",
            "Type all of this text on the keys. Do not stop before the end. Type at your usual speed, as in any text box. "
                + "Do not stop after each word. Correct mistakes only as you usually do.",
            Passages.NORMAL),
        new Step("02-finger-on-key", "One finger on a key",
            "Put one finger on a key. Keep it there for approximately 2 seconds. Then lift it. "
                + "Do these steps 10 times. Include A, I and Space."),
        new Step("03-grip-and-type", "Type with one finger at rest",
            "Hold the phone as usual. Keep one finger on the keys to give a better grip. Type one sentence."),
        new Step("04-grip-and-swipe", "Swipe with one finger at rest",
            "Keep one finger on the keys. Swipe a word with a different finger. Lift both fingers. "
                + "Do these steps 10 times."),
        new Step("05-two-fingers", "Two fingers together",
            "Put two fingers on the keys at almost the same time. Lift them. Do these steps 5 times."),
        new Step("06-quick-double-tap", "Quick double tap",
            "Tap two times quickly on the bottom row. Lift your finger quickly after each tap. "
                + "Use a different position each time. Do these steps 10 times."),
        new Step("07-double-tap-drag", "Double tap and move",
            "Tap two times on the bottom row. Keep your finger down after the second tap. Move it left and right. "
                + "Lift it. Do these steps 10 times."),
        new Step("08-taps-then-typing", "Single taps, then typing",
            "Tap one time on the bottom row at different positions. Then type as usual. Continue for 30 seconds."),
        new Step("09-slow-slide", "Slow slide",
            "Put one finger on the keys. Move it slowly across the keys. Lift it. Do these steps 5 times."),
        new Step("10-fn-mapper-off", "fn with Key Mapper off",
            "Turn off Key Mapper. Return to this app. Push fn 3 times."),
        new Step("11-fn-mapper-on", "fn with Key Mapper on",
            "Turn on Key Mapper. Return to this app. Push fn 3 times."),
        new Step("12-shift-and-type", "Shift and type",
            "Hold Shift. Type a word. Release Shift. Type a different word. Do these steps 5 times."),
        new Step("13-shift-and-swipe", "Shift and swipe",
            "Hold Shift. Swipe a word. Release Shift. Type a different word. Do these steps 5 times."),
        new Step("14-back", "Back with the keyboard open",
            "Make sure that the keyboard is open. Push Back. Do this 3 times. "
                + "In this step, Back does not close this app and does not change the step."),
        new Step("15-finish", "Finish",
            "Push Open log settings. Use Export log to save the log."),
    };

    /** On this step, Back must stay where it is. Back is what the step records. */
    static final int BACK_STEP = 14;
    static final int FINISH_STEP = STEPS.length;

    static final String[] STYLES = {
        "Keys, two thumbs", "Keys, one thumb", "Swipe only", "Keys and swipes", "Walking", "Fast",
    };

    static final String APP_TITLE = "TitanKeys Reference";
    static final String BEFORE_YOU_START = "Before you start";
    static final String KEYBOARD_OK = "TitanKeys Debug is the current keyboard.";
    static final String KEYBOARD_NOT_OK =
        "TitanKeys Debug is not the current keyboard. Make it the current keyboard before you continue.";
    static final String SETUP =
        "This app shows the steps. TitanKeys Debug records your input in its raw input log.\n\n"
            + "Turn on the log before you continue:\n"
            + "1. Push Open log settings.\n"
            + "2. Turn on Record my input.\n"
            + "3. Return to this app.\n"
            + "4. Push Next.";
    static final String NOT_INSTALLED = "TitanKeys Debug is not installed.";
    static final String STYLES_HEADING = "Styles recorded:";
    static final String NO_STYLES = "None.";
    static final String STYLE_PROGRESS = "Typing style";
    static final String STYLE_WITH_PASSAGE =
        "Type this text on the keys in this style. To type your own text, push Type anything. "
            + "Push Finish style when you stop.";
    static final String STYLE_NO_PASSAGE = "Type your own text in this style. Push Finish style when you stop.";
    static final String STYLE_QUESTION = "Select a style, or type a name and push Start.";
    static final String PUSH_START = "Push Start, then do this step.";
    static final String PUSH_DONE = "Push Done when you complete this step.";
    static final String TIMELINE_FAILED = "The app cannot write the timeline file.";
    static final String LEAVE_TITLE = "Leave this app?";
    static final String LEAVE_MESSAGE = "The steps stop. The timeline file stays on this phone.";
}
