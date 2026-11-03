package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;

import java.util.Arrays;

/** The rows of glassGram's Inline Results settings (GlassgramSettingsActivity). */
public final class GlassgramInlineCells {

    private GlassgramInlineCells() {
    }

    /** A switch with a gray icon before its title. */
    public static final class IconCheck extends UItem.UItemFactory<TextCell> {
        static { setup(new IconCheck()); }

        @Override
        public TextCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new TextCell(context, 23, false, true, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            final TextCell cell = (TextCell) view;
            // the same row again: its switch moves rather than jumps
            final boolean same = cell.getTag() instanceof Integer && (Integer) cell.getTag() == item.id;
            final boolean was = cell.isChecked();
            cell.setTextAndCheckAndIcon(item.text, same ? was : item.checked, item.iconResId, divider);
            if (same && was != item.checked) {
                cell.setChecked(item.checked);
            }
            cell.setTag(item.id);
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            return a.id == b.id && a.checked == b.checked && a.iconResId == b.iconResId && TextUtils.equals(a.text, b.text);
        }

        public static UItem of(int id, int icon, CharSequence text, boolean checked) {
            final UItem item = UItem.ofFactory(IconCheck.class);
            item.id = id;
            item.iconResId = icon;
            item.text = text;
            item.checked = checked;
            return item;
        }
    }

    /** A row with an icon, a title and a line under it: the way into a page. */
    public static final class SubtitleButton extends UItem.UItemFactory<TextCell> {
        static { setup(new SubtitleButton()); }

        @Override
        public TextCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            final TextCell cell = new TextCell(context, resourcesProvider);
            cell.heightDp = 58;
            return cell;
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            final TextCell cell = (TextCell) view;
            cell.setTextAndIcon(item.text, item.iconResId, divider);
            cell.setSubtitle(item.subtext);
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            return a.id == b.id;
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            return a.id == b.id && a.iconResId == b.iconResId && TextUtils.equals(a.text, b.text) && TextUtils.equals(a.subtext, b.subtext);
        }

        public static UItem of(int id, int icon, CharSequence text, CharSequence subtext) {
            final UItem item = UItem.ofFactory(SubtitleButton.class);
            item.id = id;
            item.iconResId = icon;
            item.text = text;
            item.subtext = subtext;
            return item;
        }
    }

    /** A capability's title: its name, a line of what it takes, and an arrow that turns as it opens. */
    public static final class CapabilityTitle extends UItem.UItemFactory<CapabilityTitle.TitleView> {
        static { setup(new CapabilityTitle()); }

        public static class TitleView extends FrameLayout {
            final TextCell cell;
            final ImageView arrow;
            int id = -1;

            public TitleView(Context context, Theme.ResourcesProvider resourcesProvider) {
                super(context);
                cell = new TextCell(context, resourcesProvider);
                cell.heightDp = 58;
                cell.setClickable(false);
                cell.setFocusable(false);
                cell.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                arrow = new ImageView(context);
                arrow.setScaleType(ImageView.ScaleType.CENTER);
                arrow.setImageResource(R.drawable.arrow_more);
                arrow.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
                addView(arrow, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 18, 0, 18, 0));
            }
        }

        @Override
        public TitleView createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new TitleView(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            final TitleView title = (TitleView) view;
            title.cell.setTextAndIcon(item.text, item.iconResId, divider);
            title.cell.setSubtitle(item.subtext);
            // leave room for the arrow
            title.cell.setPadding(0, 0, dp(36), 0);
            final float rotation = item.collapsed ? 0 : 180;
            if (title.id == item.id && title.isAttachedToWindow()) {
                title.arrow.animate().rotation(rotation).setDuration(220).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            } else {
                title.arrow.animate().cancel();
                title.arrow.setRotation(rotation);
            }
            title.id = item.id;
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            return a.id == b.id;
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            return a.id == b.id && a.collapsed == b.collapsed && TextUtils.equals(a.text, b.text) && TextUtils.equals(a.subtext, b.subtext);
        }

        public static UItem of(int id, int icon, CharSequence text, CharSequence subtext, boolean expanded) {
            final UItem item = UItem.ofFactory(CapabilityTitle.class);
            item.id = id;
            item.iconResId = icon;
            item.text = text;
            item.subtext = subtext;
            item.collapsed = !expanded;
            return item;
        }
    }

    /** A part of a capability: a line about it, then examples worked out as they are now. */
    public static final class Block {
        public final CharSequence text;
        /** A line in a fixed-width font: the names of the functions. */
        public final String code;
        public final String[] expressions;
        /** What each gives, null for one that gives nothing now (no rates yet). */
        public final String[] results;

        public Block(CharSequence text, String code, String[] expressions, String[] results) {
            this.text = text;
            this.code = code;
            this.expressions = expressions;
            this.results = results;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Block)) {
                return false;
            }
            final Block b = (Block) o;
            return TextUtils.equals(text, b.text) && TextUtils.equals(code, b.code) && Arrays.equals(expressions, b.expressions) && Arrays.equals(results, b.results);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(expressions);
        }
    }

    /** A capability opened: its blocks; a tap on an example hands it to onCopy. */
    public static final class Examples extends UItem.UItemFactory<Examples.ExamplesView> {
        static { setup(new Examples()); }

        public static class ExamplesView extends LinearLayout {
            private final Theme.ResourcesProvider resourcesProvider;

            public ExamplesView(Context context, Theme.ResourcesProvider resourcesProvider) {
                super(context);
                this.resourcesProvider = resourcesProvider;
                setOrientation(VERTICAL);
                setPadding(dp(21), dp(2), dp(21), dp(14));
            }

            void set(Block[] blocks, Utilities.Callback<String> onCopy) {
                removeAllViews();
                final int gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
                final int black = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
                for (int i = 0; i < blocks.length; i++) {
                    final Block block = blocks[i];
                    if (!TextUtils.isEmpty(block.text)) {
                        final TextView text = new TextView(getContext());
                        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                        text.setTextColor(gray);
                        text.setLineSpacing(dp(2), 1f);
                        text.setText(block.text);
                        addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, i == 0 ? 0 : 12, 0, 0));
                    }
                    if (!TextUtils.isEmpty(block.code)) {
                        final TextView code = new TextView(getContext());
                        code.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                        code.setTypeface(Typeface.MONOSPACE);
                        code.setTextColor(black);
                        code.setText(block.code);
                        addView(code, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
                    }
                    final Flow flow = new Flow(getContext());
                    for (int k = 0; k < block.expressions.length; k++) {
                        final String expression = block.expressions[k];
                        final String result = block.results[k];
                        final SpannableStringBuilder s = new SpannableStringBuilder(expression);
                        final int start = s.length();
                        s.append(" = ").append(result != null ? result : "…");
                        s.setSpan(new ForegroundColorSpan(gray), start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        final TextView chip = new TextView(getContext());
                        chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                        chip.setTextColor(black);
                        chip.setSingleLine(true);
                        chip.setEllipsize(TextUtils.TruncateAt.END);
                        chip.setText(s);
                        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
                        chip.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(10),
                            Theme.multAlpha(black, 0.06f), Theme.multAlpha(black, 0.14f)));
                        final String copy = expression + " = " + (result != null ? result : "");
                        chip.setOnClickListener(v -> {
                            if (onCopy != null && result != null) {
                                onCopy.run(copy.replace(' ', ' '));
                            }
                        });
                        flow.addView(chip);
                    }
                    if (flow.getChildCount() > 0) {
                        addView(flow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
                    }
                }
            }
        }

        @Override
        public ExamplesView createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new ExamplesView(context, resourcesProvider);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((ExamplesView) view).set((Block[]) item.object, (Utilities.Callback<String>) item.object2);
        }

        @Override
        public boolean isClickable() {
            return false;
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            // the same capability, whatever its examples give now: rebound, not taken out and put back
            return a.id == b.id;
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            return a.id == b.id && Arrays.equals((Block[]) a.object, (Block[]) b.object);
        }

        public static UItem of(int id, Block[] blocks, Utilities.Callback<String> onCopy) {
            final UItem item = UItem.ofFactory(Examples.class);
            item.id = id;
            item.object = blocks;
            item.object2 = onCopy;
            return item;
        }
    }

    /** Its children in rows, as many in a row as fit. */
    private static class Flow extends ViewGroup {
        private final int gap = dp(8);

        Flow(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            int x = 0, y = 0, rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                final View child = getChildAt(i);
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                if (x > 0 && x + child.getMeasuredWidth() > width) {
                    x = 0;
                    y += rowHeight + gap;
                    rowHeight = 0;
                }
                x += child.getMeasuredWidth() + gap;
                rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
            }
            setMeasuredDimension(width, y + rowHeight);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            final int width = r - l;
            int x = 0, y = 0, rowHeight = 0;
            final boolean rtl = LocaleController.isRTL;
            for (int i = 0; i < getChildCount(); i++) {
                final View child = getChildAt(i);
                final int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
                if (x > 0 && x + w > width) {
                    x = 0;
                    y += rowHeight + gap;
                    rowHeight = 0;
                }
                final int left = rtl ? width - x - w : x;
                child.layout(left, y, left + w, y + h);
                x += w + gap;
                rowHeight = Math.max(rowHeight, h);
            }
        }
    }
}
