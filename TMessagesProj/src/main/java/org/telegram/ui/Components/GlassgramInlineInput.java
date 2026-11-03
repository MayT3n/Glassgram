package org.telegram.ui.Components;

import android.text.Editable;
import android.text.Selection;
import android.text.TextWatcher;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.GlassgramConfig;
import org.telegram.messenger.GlassgramInlineResults;

/**
 * glassGram's Inline Results in a message field: after "=" the result shows in gray where it
 * would be written; Space writes it in, and Backspace right after that takes it back out, leaving
 * the space that was typed.
 */
public class GlassgramInlineInput implements TextWatcher {

    private final EditTextCaption field;
    /** What shows in gray, and the offset it shows at (right after the "="). */
    private String ghost;
    private int ghostAt = -1;
    /** The result written in last, while Backspace right after it can still take it back. */
    private int insertedStart = -1, insertedEnd = -1;
    /** The text as it was when a change began, while there is something to compare it to. */
    private String before;
    private boolean applying;
    private final Runnable ratesCame = this::update;

    public GlassgramInlineInput(EditTextCaption field) {
        this.field = field;
        field.addTextChangedListener(this);
        if (GlassgramConfig.inlineResults && GlassgramConfig.inlineCurrencies) {
            // the rates ready by the time an amount is typed
            GlassgramInlineResults.refresh(false, null);
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        if (applying) {
            return;
        }
        before = ghost != null || insertedStart >= 0 ? s.toString() : null;
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable text) {
        if (applying) {
            return;
        }
        final String old = before;
        before = null;
        try {
            if (old != null && ghost != null && spaceTypedAt(old, text, ghostAt) && field.getSelectionEnd() == ghostAt + 1) {
                // Space writes the result in place of the space
                final String result = ghost;
                final int at = ghostAt;
                apply(text, at, at + 1, result);
                insertedStart = at;
                insertedEnd = at + result.length();
                Selection.setSelection(text, insertedEnd);
                setGhost(null, -1);
                return;
            }
            if (old != null && insertedStart >= 0 && charDeletedAt(old, text, insertedEnd - 1) && field.getSelectionEnd() == insertedEnd - 1) {
                // Backspace right after it: as it was typed, the space too
                final int start = insertedStart, end = insertedEnd - 1;
                insertedStart = insertedEnd = -1;
                apply(text, start, end, " ");
                Selection.setSelection(text, start + 1);
                update();
                return;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        insertedStart = insertedEnd = -1;
        update();
    }

    /** The field's cursor moved. */
    public void onSelectionChanged(int start, int end) {
        if (applying) {
            return;
        }
        if (insertedStart >= 0 && (start != end || end != insertedEnd)) {
            insertedStart = insertedEnd = -1;
        }
        update();
    }

    private void apply(Editable text, int start, int end, String replacement) {
        applying = true;
        try {
            text.replace(start, end, replacement != null ? replacement : "");
        } finally {
            applying = false;
        }
    }

    /** now is old with one space more, at offset at. */
    private static boolean spaceTypedAt(String old, Editable now, int at) {
        if (at < 0 || now.length() != old.length() + 1 || at >= now.length() || now.charAt(at) != ' ') {
            return false;
        }
        return same(now, 0, old, 0, at) && same(now, at + 1, old, at, old.length() - at);
    }

    /** now is old with the character at offset at taken out. */
    private static boolean charDeletedAt(String old, Editable now, int at) {
        if (at < 0 || now.length() != old.length() - 1 || at > now.length()) {
            return false;
        }
        return same(now, 0, old, 0, at) && same(now, at, old, at + 1, old.length() - at - 1);
    }

    private static boolean same(CharSequence a, int aStart, String b, int bStart, int length) {
        if (length < 0 || aStart + length > a.length() || bStart + length > b.length()) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (a.charAt(aStart + i) != b.charAt(bStart + i)) {
                return false;
            }
        }
        return true;
    }

    /** Works out what to show for the text and cursor as they are now. */
    public void update() {
        final Editable text = field.getText();
        final int start = field.getSelectionStart(), end = field.getSelectionEnd();
        String result = null;
        if (text != null && start == end && end > 0 && GlassgramConfig.inlineResults) {
            try {
                result = GlassgramInlineResults.resultFor(text, end);
                if (result == null && GlassgramConfig.inlineCurrencies && !GlassgramInlineResults.hasRates() && text.charAt(end - 1) == '=') {
                    // an amount typed before the rates came: shown once they do
                    GlassgramInlineResults.refresh(false, ratesCame);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        setGhost(result, result != null ? end : -1);
    }

    private void setGhost(String text, int at) {
        ghost = text;
        ghostAt = at;
        field.setGlassgramGhost(text, at);
    }
}
