package com.titankeys.reference;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * Prompts the owner through gestures and typing while TitanKeys Debug's raw input log records them. Each button adds a
 * mark to that log and a line to this app's timeline, so a tool can cut the log into steps. All navigation is on the
 * screen and nothing but the field can take focus, because the physical keys are what is being recorded.
 */
public final class ReferenceActivity extends Activity {
    private static final String MARK_ACTION = "com.titankeys.rawlog.MARK";
    private static final String REFERENCE_OPTION = "com.titankeys.rawlog.reference";
    private static final String KEYBOARD_PACKAGE = "com.titankeys.debug";
    private static final String KEYBOARD_ACTIVITY = "com.vandam.ink.MainActivity";
    private static final String HREF_EXTRA = "com.vandam.ink.notification.HREF";
    private static final String LOG_SETTINGS_HREF = "/settings/rawlog";

    private static final int FIRST_SCREEN = 0;
    private static final int FIELD_ID = 1;
    private static final int FIELD_LINES = 3;
    private static final int MAX_STYLE_LENGTH = 64;
    private static final int NOT_FOCUSED = -1;
    private static final long TICK_MILLIS = 1000;
    /** A window that has just gained focus needs a moment before the keyboard has started on its field. */
    private static final long FOCUS_SETTLE_MILLIS = 200;
    private static final long MARK_RETRY_MILLIS = 50;
    private static final long MARK_WAIT_MILLIS = 3000;
    /** Longer than the keyboard waits before it brings its own strip back after Back. */
    private static final long KEYBOARD_RETURN_MILLIS = 600;
    private static final int MUTED = Color.rgb(142, 142, 147);

    private static final class Mark {
        final String kind;
        final String label;
        final int step;
        final int passage;
        final long time;

        Mark(String kind, String label, int step, int passage, long time) {
            this.kind = kind;
            this.label = label;
            this.step = step;
            this.passage = passage;
            this.time = time;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Mark> marks = new ArrayDeque<>();
    private final Runnable deliver = this::deliverMarks;
    private final Runnable tick = this::onTick;
    private final Runnable reopenKeyboard = this::openKeyboard;

    private InputMethodManager keyboard;
    private SharedPreferences preferences;
    private Timeline timeline;

    private TextView heading;
    private TextView progress;
    private TextView instruction;
    private TextView passage;
    private TextView status;
    private ScrollView scroll;
    private EditText field;
    private Button start;
    private Button done;
    private Button redo;
    private Button skip;
    private Button previous;
    private Button next;
    private Button finishStyle;
    private Button typeAnything;
    private Button openSettings;
    private Button newStyle;

    private int screen = FIRST_SCREEN;
    private boolean running;
    private long startedAt;
    private int backPushes;
    private String style;
    private int stylePassage;
    private ArrayList<String> styles = new ArrayList<>();
    private boolean keyboardOpen;
    private long focusedAt = NOT_FOCUSED;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Android 15 and later always do this. Android 14 needs the call so that the layout is the same there.
        getWindow().setDecorFitsSystemWindows(false);
        keyboard = getSystemService(InputMethodManager.class);
        preferences = getSharedPreferences("reference", MODE_PRIVATE);
        setContentView(buildScreen());
        String timelineName = Timeline.newName();
        if (saved != null) {
            screen = saved.getInt("screen");
            running = saved.getBoolean("running");
            startedAt = saved.getLong("startedAt");
            backPushes = saved.getInt("backPushes");
            style = saved.getString("style");
            stylePassage = saved.getInt("stylePassage");
            styles = saved.getStringArrayList("styles");
            timelineName = saved.getString("timeline");
        }
        timeline = new Timeline(getExternalFilesDir("reference"), timelineName);
        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("screen", screen);
        out.putBoolean("running", running);
        out.putLong("startedAt", startedAt);
        out.putInt("backPushes", backPushes);
        out.putString("style", style);
        out.putInt("stylePassage", stylePassage);
        out.putStringArrayList("styles", styles);
        out.putString("timeline", timeline.name());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        focusedAt = hasFocus ? SystemClock.uptimeMillis() : NOT_FOCUSED;
        if (!hasFocus) return;
        // The keyboard picker is a separate window, so this also runs after the owner changes the keyboard.
        if (screen == FIRST_SCREEN && style == null) render();
        openKeyboard();
        deliverMarks();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        while (!marks.isEmpty()) write(marks.poll(), false);
        super.onDestroy();
    }

    /** TitanKeys passes Back on to the app after it hides its strip, so one push arrives here even when it is open. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (style != null) {
            endStyle();
        } else if (screen == Prompts.BACK_STEP) {
            backPushes++;
            updateStatus();
            handler.postDelayed(reopenKeyboard, KEYBOARD_RETURN_MILLIS);
        } else if (screen == FIRST_SCREEN) {
            confirmLeave();
        } else {
            goBack();
        }
    }

    private View buildScreen() {
        heading = text(20f, Color.WHITE);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        progress = text(14f, MUTED);
        progress.setPadding(dp(8), 0, 0, 0);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(progress);

        instruction = text(16f, Color.WHITE);
        passage = text(18f, Color.WHITE);
        passage.setPadding(0, dp(12), 0, 0);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(instruction);
        content.addView(passage);
        scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.addView(content);

        field = buildField();
        status = text(13f, MUTED);

        start = button("Start", view -> startAttempt());
        done = button("Done", view -> finishAttempt());
        redo = button("Redo", view -> redoAttempt());
        skip = button("Skip", view -> leaveStep("skip"));
        previous = button("Previous", view -> goBack());
        next = button("Next", view -> showScreen(1));
        finishStyle = button("Finish style", view -> endStyle());
        typeAnything = button("Type anything", view -> dropPassage());
        openSettings = button("Open log settings", view -> openLogSettings());
        newStyle = button("New typing style", view -> askForStyle());
        LinearLayout actions = row(start, done, redo, skip, previous, next, finishStyle, typeAnything);
        LinearLayout extras = row(openSettings, newStyle);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.addView(header);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(field);
        root.addView(status);
        root.addView(actions);
        root.addView(extras);
        // This app draws behind the system bars, so it must keep clear of them and of the keyboard itself.
        int gutter = dp(16);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets clear = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                | WindowInsets.Type.ime());
            view.setPadding(clear.left + gutter, clear.top + dp(8), clear.right + gutter, clear.bottom + dp(4));
            keyboardOpen = insets.isVisible(WindowInsets.Type.ime());
            return WindowInsets.CONSUMED;
        });
        return root;
    }

    private EditText buildField() {
        EditText view = new EditText(this);
        view.setId(FIELD_ID);
        view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        view.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING | EditorInfo.IME_FLAG_NO_EXTRACT_UI
            | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        view.setPrivateImeOptions(REFERENCE_OPTION);
        view.setMinLines(FIELD_LINES);
        view.setMaxLines(FIELD_LINES);
        view.setGravity(Gravity.TOP | Gravity.START);
        view.setVerticalScrollBarEnabled(true);
        view.setTextSize(16f);
        view.setTextColor(Color.WHITE);
        view.setHintTextColor(MUTED);
        view.setHint("Type here");
        view.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable text) { followPassage(); }
        });
        return view;
    }

    private void render() {
        boolean inStyle = style != null;
        boolean first = !inStyle && screen == FIRST_SCREEN;
        boolean onStep = !inStyle && !first;
        boolean finish = onStep && screen == Prompts.FINISH_STEP;
        setShown(onStep, start, done, redo, skip, previous);
        setShown(first, next);
        setShown(inStyle, finishStyle);
        setShown(inStyle && stylePassage > 0, typeAnything);
        setShown(!inStyle, newStyle);
        setShown(first || finish, openSettings);
        start.setEnabled(!running && !finish);
        done.setEnabled(running || finish);
        redo.setEnabled(running);
        skip.setEnabled(!finish);

        String passageText = null;
        if (inStyle) {
            heading.setText(style);
            progress.setText(Prompts.STYLE_PROGRESS);
            instruction.setText(stylePassage > 0 ? Prompts.STYLE_WITH_PASSAGE : Prompts.STYLE_NO_PASSAGE);
            if (stylePassage > 0) passageText = Passages.STYLES[stylePassage - 1];
        } else if (first) {
            heading.setText(Prompts.APP_TITLE);
            progress.setText(Prompts.BEFORE_YOU_START);
            instruction.setText((keyboardIsDebug() ? Prompts.KEYBOARD_OK : Prompts.KEYBOARD_NOT_OK) + "\n\n" + Prompts.SETUP);
        } else {
            heading.setText(step().title);
            progress.setText("Step " + screen + " of " + Prompts.STEPS.length);
            instruction.setText(finish ? finishText() : step().instruction);
            passageText = step().passage;
        }
        passage.setText(passageText);
        passage.setVisibility(passageText == null ? View.GONE : View.VISIBLE);
        updateStatus();
        handler.removeCallbacks(tick);
        if (running || inStyle) handler.postDelayed(tick, TICK_MILLIS);
    }

    private String finishText() {
        StringBuilder text = new StringBuilder(step().instruction).append("\n\n").append(Prompts.STYLES_HEADING);
        if (styles.isEmpty()) text.append(' ').append(Prompts.NO_STYLES);
        for (String name : styles) text.append('\n').append(name);
        return text.toString();
    }

    private void updateStatus() {
        StringBuilder text = new StringBuilder();
        boolean inStyle = style != null;
        if (running || inStyle) {
            text.append("Time: ").append((SystemClock.uptimeMillis() - startedAt) / 1000).append(" s. ");
        }
        if (running && !inStyle) text.append(Prompts.PUSH_DONE);
        else if (!inStyle && screen != FIRST_SCREEN && screen != Prompts.FINISH_STEP) text.append(Prompts.PUSH_START);
        if (!inStyle && screen == Prompts.BACK_STEP) text.append(" Back pushes: ").append(backPushes).append('.');
        if (timeline.failed()) text.append(' ').append(Prompts.TIMELINE_FAILED);
        status.setText(text.toString().trim());
    }

    private void onTick() {
        updateStatus();
        handler.postDelayed(tick, TICK_MILLIS);
    }

    private Prompts.Step step() {
        return Prompts.STEPS[screen - 1];
    }

    private void showScreen(int number) {
        screen = number;
        running = false;
        backPushes = 0;
        field.setText("");
        render();
        scroll.scrollTo(0, 0);
        openKeyboard();
    }

    private void startAttempt() {
        field.setText("");
        running = true;
        startedAt = SystemClock.uptimeMillis();
        backPushes = 0;
        mark("start", step().label, 0);
        render();
        openKeyboard();
    }

    private void finishAttempt() {
        if (screen != Prompts.FINISH_STEP) {
            leaveStep("done");
            return;
        }
        mark("done", step().label, 0);
        confirmLeave();
    }

    private void redoAttempt() {
        mark("redo", step().label, 0);
        running = false;
        field.setText("");
        render();
        openKeyboard();
    }

    private void leaveStep(String kind) {
        mark(kind, step().label, 0);
        showScreen(screen + 1);
    }

    private void goBack() {
        cancelAttempt();
        showScreen(screen - 1);
    }

    /** A redo mark cancels an attempt, so no attempt in the log stays open when the owner moves away from it. */
    private void cancelAttempt() {
        if (running) mark("redo", step().label, 0);
        running = false;
    }

    private void askForStyle() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(20), dp(4), dp(20), dp(4));
        TextView question = text(14f, Color.WHITE);
        question.setText(Prompts.STYLE_QUESTION);
        column.addView(question);
        // This field has no reference option, so the log doesn't record the name as it is typed.
        EditText name = new EditText(this);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setSingleLine(true);
        name.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        name.setFilters(new InputFilter[] { new InputFilter.LengthFilter(MAX_STYLE_LENGTH) });
        name.setHint("Style name");
        column.addView(name);
        ScrollView content = new ScrollView(this);
        content.addView(column);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("New typing style")
            .setView(content)
            .setPositiveButton("Start", (shown, which) -> beginStyle(name.getText().toString().trim()))
            .setNegativeButton("Cancel", null)
            .create();
        for (String pick : Prompts.STYLES) {
            Button button = button(pick, view -> {
                dialog.dismiss();
                beginStyle(pick);
            });
            button.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            column.addView(button);
        }
        dialog.show();
        Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        confirm.setEnabled(false);
        name.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable text) { confirm.setEnabled(text.toString().trim().length() > 0); }
        });
    }

    private void beginStyle(String name) {
        cancelAttempt();
        style = name;
        stylePassage = nextPassage();
        startedAt = SystemClock.uptimeMillis();
        field.setText("");
        mark("style-start", style, stylePassage);
        render();
        scroll.scrollTo(0, 0);
        openKeyboard();
    }

    private void endStyle() {
        mark("style-end", style, 0);
        styles.add(style);
        style = null;
        showScreen(screen);
    }

    private void dropPassage() {
        stylePassage = 0;
        mark("style-type-anything", style, 0);
        render();
        scroll.scrollTo(0, 0);
    }

    /** One above the array index, as 0 means no passage. */
    private int nextPassage() {
        int count = preferences.getInt("passages", 0);
        preferences.edit().putInt("passages", count + 1).apply();
        return count % Passages.STYLES.length + 1;
    }

    private void confirmLeave() {
        new AlertDialog.Builder(this)
            .setTitle(Prompts.LEAVE_TITLE)
            .setMessage(Prompts.LEAVE_MESSAGE)
            .setPositiveButton("Leave", (shown, which) -> finish())
            .setNegativeButton("Stay", null)
            .show();
    }

    private void openLogSettings() {
        Intent intent = new Intent().setClassName(KEYBOARD_PACKAGE, KEYBOARD_ACTIVITY).putExtra(HREF_EXTRA, LOG_SETTINGS_HREF);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, Prompts.NOT_INSTALLED, Toast.LENGTH_LONG).show();
        }
    }

    private boolean keyboardIsDebug() {
        String current = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        ComponentName name = current == null ? null : ComponentName.unflattenFromString(current);
        return name != null && KEYBOARD_PACKAGE.equals(name.getPackageName());
    }

    private void openKeyboard() {
        field.requestFocus();
        if (!keyboardOpen) keyboard.showSoftInput(field, 0);
    }

    /** Keeps the line being typed in view, so the owner's hands can stay on the keys. */
    private void followPassage() {
        Layout layout = passage.getLayout();
        if (layout == null || passage.getVisibility() != View.VISIBLE) return;
        int line = layout.getLineForOffset(Math.min(field.length(), passage.length()));
        int top = passage.getTop() + layout.getLineTop(line);
        int seen = scroll.getScrollY();
        if (top < seen || top > seen + scroll.getHeight() * 2 / 3) {
            scroll.smoothScrollTo(0, Math.max(0, top - passage.getLineHeight()));
        }
    }

    private void mark(String kind, String label, int passageNumber) {
        marks.add(new Mark(kind, label, screen, passageNumber, SystemClock.uptimeMillis()));
        deliverMarks();
    }

    /** The keyboard loses a mark sent while it doesn't serve the field, such as just after a dialog closes. */
    private void deliverMarks() {
        handler.removeCallbacks(deliver);
        while (!marks.isEmpty()) {
            boolean served = fieldServed();
            if (!served && SystemClock.uptimeMillis() - marks.peek().time < MARK_WAIT_MILLIS) {
                handler.postDelayed(deliver, MARK_RETRY_MILLIS);
                return;
            }
            Mark mark = marks.poll();
            if (served) {
                Bundle extras = new Bundle();
                extras.putString("kind", mark.kind);
                extras.putString("label", mark.label);
                keyboard.sendAppPrivateCommand(field, MARK_ACTION, extras);
            }
            write(mark, served);
        }
    }

    private void write(Mark mark, boolean sent) {
        timeline.add(mark.time, mark.kind, mark.label, mark.step, mark.passage, sent);
    }

    private boolean fieldServed() {
        return focusedAt != NOT_FOCUSED && SystemClock.uptimeMillis() - focusedAt >= FOCUS_SETTLE_MILLIS
            && keyboard.isActive(field);
    }

    private static void setShown(boolean shown, View... views) {
        for (View view : views) view.setVisibility(shown ? View.VISIBLE : View.GONE);
    }

    private TextView text(float size, int colour) {
        TextView view = new TextView(this);
        view.setTextSize(size);
        view.setTextColor(colour);
        return view;
    }

    private Button button(String label, View.OnClickListener action) {
        Button view = new Button(this);
        view.setText(label);
        view.setTextSize(14f);
        view.setAllCaps(false);
        view.setSingleLine(true);
        view.setMinWidth(0);
        view.setMinimumWidth(0);
        view.setPadding(dp(2), 0, dp(2), 0);
        // Keys must never move focus onto a button, as focus leaving the field stops the recording.
        view.setFocusable(false);
        view.setOnClickListener(action);
        return view;
    }

    private LinearLayout row(Button... buttons) {
        LinearLayout row = new LinearLayout(this);
        for (Button button : buttons) {
            row.addView(button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
