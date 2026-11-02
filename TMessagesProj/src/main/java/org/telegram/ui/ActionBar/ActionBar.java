/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.find;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.TextUtils;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Adapters.FiltersView;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EllipsizeSpanAnimator;
import org.telegram.ui.Components.FireworksEffect;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LiquidGlassButtonBackground;
import org.telegram.ui.Components.ViewBehindBackdrop;
import org.telegram.ui.Components.LiquidPressEffect;
import org.telegram.liquidglass.GlassLayer;
import org.telegram.ui.Components.SectionsScrollView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.SnowflakesEffect;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.MainTabsLayout;

import java.util.ArrayList;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.animator.ReplaceAnimator;

public class ActionBar extends FrameLayout implements FactorAnimator.Target, Theme.Colorable {

    public static class ActionBarMenuOnItemClick {
        public void onItemClick(int id) {

        }

        public boolean canOpenMenu() {
            return true;
        }
    }

    private BlurredBackgroundDrawable glassDrawable;
    private BlurredBackgroundDrawable glassDrawableBack;
    private BlurredBackgroundDrawable glassDrawableMenu;
    private INavigationLayout.BackButtonState backButtonState = INavigationLayout.BackButtonState.BACK;
    public ImageView backButtonImageView;
    private BackupImageView avatarSearchImageView;
    private Drawable backButtonDrawable;
    private final SimpleTextView[] titleTextView = new SimpleTextView[2];
    private SimpleTextView subtitleTextView;
    private SimpleTextView additionalSubtitleTextView;
    private View actionModeTop;
    private int actionModeColor;
    private int actionBarColor;
    private boolean isMenuOffsetSuppressed;
    public ActionBarMenu menu;
    private ActionBarMenu actionMode;
    private String actionModeTag;
    private boolean ignoreLayoutRequest;
    protected boolean occupyStatusBar = true;
    protected boolean actionModeVisible;
    private boolean addToContainer = true;
    private boolean clipContent;
    private boolean interceptTouches = true;
    private boolean forceSkipTouches;
    private int extraHeight;
    private AnimatorSet actionModeAnimation;
    private View actionModeExtraView;
    private View actionModeTranslationView;
    private View actionModeShowingView;
    private View[] actionModeHidingViews;

    private boolean supportsHolidayImage;
    private SnowflakesEffect snowflakesEffect;
    private FireworksEffect fireworksEffect;
    private Paint.FontMetricsInt fontMetricsInt;
    private boolean manualStart;
    private Rect rect;

    private int titleRightMargin;

    private boolean allowOverlayTitle;
    private CharSequence lastTitle;
    private Drawable lastRightDrawable;
    private OnClickListener rightDrawableOnClickListener;
    private CharSequence lastOverlayTitle;
    private Object[] overlayTitleToSet = new Object[3];
    private Runnable lastRunnable;
    private boolean titleOverlayShown;
    private Runnable titleActionRunnable;
    private boolean castShadows = true;
    private int shadowAlpha = 0xFF;

    public boolean menuOccupyBack;
    protected boolean isSearchFieldVisible;
    public float searchFieldVisibleAlpha;
    public int itemsBackgroundColor;
    protected int itemsActionModeBackgroundColor;
    protected int itemsColor;
    protected int itemsActionModeColor;
    private boolean isBackOverlayVisible;
    protected BaseFragment parentFragment;
    public ActionBarMenuOnItemClick actionBarMenuOnItemClick;
    private int titleColorToSet = 0;
    private boolean overlayTitleAnimation;
    private boolean titleAnimationRunning;
    private boolean fromBottom;
    private boolean centerScale;
    private CharSequence subtitle;
    private boolean drawBackButton;
    private boolean attached;
    private boolean resumed;
    private boolean attachState;
    private FrameLayout titlesContainer;
    private boolean useContainerForTitles;

    private View.OnTouchListener interceptTouchEventListener;
    private final Theme.ResourcesProvider resourcesProvider;

    SizeNotifierFrameLayout contentView;
    boolean blurredBackground;
    public Paint blurScrimPaint = new Paint();
    Rect rectTmp = new Rect();

    EllipsizeSpanAnimator ellipsizeSpanAnimator = new EllipsizeSpanAnimator(this);

    public ActionBar(Context context) {
        this(context, null);
    }

    public ActionBar(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setOnClickListener(v -> {
            if (isSearchFieldVisible()) {
                return;
            }
            if (titleActionRunnable != null) {
                titleActionRunnable.run();
            }
        });
    }

    private boolean glassMode;
    private boolean glassOnlyBack;
    private boolean glassModeIsForum;

    private ChatAvatarContainer chatAvatarContainer;

    /** Where the content of the glass pill ends, in this bar's coordinates. */
    public interface GlassPillContent {
        float getContentRight();

        /** How far the pill's content has moved down from the bar's row (stories pulled down with it). */
        default float getContentOffsetY() {
            return 0;
        }
    }

    private int glassTitleSize = 17;

    /** Title size in glass mode, sp: 17 like the chat's header unless a screen keeps its own. */
    public void setGlassTitleSize(int size) {
        glassTitleSize = size;
        requestLayout();
    }

    private GlassPillContent glassPillContent;
    private float glassAlpha = 1f;

    /** Fades all glass pieces of the bar, for screens that hide the bar's content (a search over it). */
    public void setGlassAlpha(float alpha) {
        if (glassAlpha != alpha) {
            glassAlpha = alpha;
            invalidate();
        }
    }
    private float glassPillAlpha = 1f;
    private float glassBackOverlay;

    /** Fades only the pill, for a screen that hides its title (the chats list with topics open beside it). */
    public void setGlassPillAlpha(float alpha) {
        if (glassPillAlpha != alpha) {
            glassPillAlpha = alpha;
            invalidate();
        }
    }

    /**
     * Shows the back button's glass circle even with this bar's back button hidden: the chats list
     * keeps it under the back button of the topics opened beside it, which has no glass of its own.
     */
    public void setGlassBackOverlay(float factor) {
        if (glassBackOverlay != factor) {
            glassBackOverlay = factor;
            invalidate();
        }
    }

    private final int[] glassTouchLocation = new int[2];

    /**
     * Presses the back button's glass circle with a touch on another view's button drawn over it (the
     * topics' back button over the chats list's glass, see {@link #setGlassBackOverlay}).
     */
    public void glassBackTouchFrom(View source, MotionEvent ev) {
        if (!glassMode || !glassShown[GLASS_BACK]) {
            return;
        }
        source.getLocationOnScreen(glassTouchLocation);
        float x = glassTouchLocation[0] + ev.getX(), y = glassTouchLocation[1] + ev.getY();
        getLocationOnScreen(glassTouchLocation);
        x -= glassTouchLocation[0];
        y -= glassTouchLocation[1];
        final RectF r = glassRect[GLASS_BACK];
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            glassUnclip();
        }
        glassPress(GLASS_BACK).onTouch(action, x - r.left, y - r.top);
    }

    private View glassPillFollower;
    private boolean glassActionModeMoved;
    private boolean glassPillFollowerMoved;

    /**
     * A sibling view whose content sits on the glass pill (the stories collapsed into the chats
     * list header): it is redrawn while the pill stretches and moves with it through
     * {@link #glassTransformPillFor}.
     */
    public void setGlassPillFollower(View view) {
        glassPillFollower = view;
    }

    /** Moves a sibling's drawing with the stretching pill; the canvas is the sibling's own. */
    public boolean glassTransformPillFor(Canvas canvas, View view) {
        final LiquidPressEffect effect = glassPress[GLASS_PILL];
        if (!glassMode || effect == null || !effect.isActive() || !glassShown[GLASS_PILL]) {
            return false;
        }
        final float dx = view.getX() - getX(), dy = view.getY() - getY();
        canvas.translate(-dx, -dy);
        glassTransform(canvas, GLASS_PILL);
        canvas.translate(dx, dy);
        return true;
    }

    /** Sizes the glass pill to its content (a title and what follows it) instead of the whole bar. */
    public void setGlassPillContent(GlassPillContent content) {
        glassPillContent = content;
        invalidate();
    }

    public void setGlassOnlyBack() {
        glassOnlyBack = true;
    }

    // glassGram: a page's glass header fades and blurs away while ActionBarLayout draws it in place
    // during a slide, as on iOS. A property of the bar's RenderNode, so it shows on a page drawn as a
    // still picture during the slide as well, without the page being drawn again
    private float glassgramAppear = 1f;

    /** 1 shows the bar as it is; less blurs it and fades it out, 0 hides it. */
    public void setGlassgramAppear(float appear) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            return;
        }
        appear = Math.max(0f, Math.min(1f, appear));
        if (glassgramAppear == appear) {
            return;
        }
        glassgramAppear = appear;
        if (appear >= 1f) {
            setRenderEffect(null);
            return;
        }
        // Hidden (ActionBarLayout draws it in place of the page): no blur to work out
        final float radius = appear <= 0f ? 0f : dp(10) * (1f - appear);
        final android.graphics.ColorMatrix fade = new android.graphics.ColorMatrix();
        fade.setScale(1f, 1f, 1f, appear);
        final android.graphics.RenderEffect fadeEffect = android.graphics.RenderEffect.createColorFilterEffect(new android.graphics.ColorMatrixColorFilter(fade));
        if (radius >= 0.5f) {
            final android.graphics.RenderEffect blur = android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.DECAL);
            setRenderEffect(android.graphics.RenderEffect.createChainEffect(blur, fadeEffect));
        } else {
            setRenderEffect(fadeEffect);
        }
    }

    // glassGram: as on iOS, the bar's glass pieces flow into each other when one is pressed and
    // swells or is pulled towards another (GlassMerge): the back button, the pill, the menu
    private BlurredBackgroundDrawableViewFactory glassFactory;
    private BlurredBackgroundColorProvider glassColorProvider;
    private org.telegram.ui.Components.GlassMerge glassMerge;
    private final BlurredBackgroundDrawable[] glassNeckDrawables = new BlurredBackgroundDrawable[3];
    private final float[] glassNeckRestGap = {-1, -1, -1};
    private final RectF glassNeckA = new RectF(), glassNeckB = new RectF();
    private static final int[][] GLASS_NECK_PAIRS = {{ActionBar.GLASS_BACK, ActionBar.GLASS_PILL}, {ActionBar.GLASS_PILL, ActionBar.GLASS_MENU}, {ActionBar.GLASS_BACK, ActionBar.GLASS_MENU}};

    /** The piece's glass as drawn now: moved by its press. */
    private boolean glassShape(int surface, RectF out) {
        if (!glassShown[surface]) {
            return false;
        }
        final RectF r = glassRect[surface];
        final LiquidPressEffect effect = glassPress[surface];
        if (effect != null && effect.isActive()) {
            effect.mapRect(r.left, r.top, r.right, r.bottom, out);
        } else {
            out.set(r);
        }
        return !out.isEmpty();
    }

    /** The menu's glass as drawn now (moved by its press), in this bar's coordinates; false without it. */
    public boolean getGlassMenuShape(RectF out) {
        return glassMode && glassShape(GLASS_MENU, out);
    }

    private void drawGlassNecks(Canvas canvas) {
        if (glassFactory == null || glassAlpha <= 0f) {
            return;
        }
        for (int i = 0; i < GLASS_NECK_PAIRS.length; i++) {
            final int a = GLASS_NECK_PAIRS[i][0], b = GLASS_NECK_PAIRS[i][1];
            // the back button and the menu join only with no pill between them
            if (i == 2 && glassShown[GLASS_PILL]) {
                continue;
            }
            if (!glassShape(a, glassNeckA) || !glassShape(b, glassNeckB)) {
                continue;
            }
            final boolean aFirst = glassNeckA.centerX() <= glassNeckB.centerX();
            final RectF left = aFirst ? glassNeckA : glassNeckB, right = aFirst ? glassNeckB : glassNeckA;
            final LiquidPressEffect pa = glassPress[a], pb = glassPress[b];
            if ((pa == null || !pa.isActive()) && (pb == null || !pb.isActive())) {
                glassNeckRestGap[i] = right.left - left.right;
                continue;
            }
            if (glassMerge == null) {
                glassMerge = new org.telegram.ui.Components.GlassMerge();
            }
            if (glassNeckDrawables[i] == null) {
                glassNeckDrawables[i] = glassFactory.create(this).setColorProvider(glassColorProvider);
            }
            glassMerge.draw(canvas, left, right, glassNeckRestGap[i], glassNeckDrawables[i], 0, 0);
        }
    }

    // glassGram: the chats list's iOS-like header. The menu's capsule moves in from the end for a
    // button of the screen's own there (new message), and the pill shows only under the selection
    // bar: the title stands in the middle of the bar on its own
    private int glassMenuEndInset;
    private boolean glassPillOnlyForActionMode;

    /** Moves the menu and its glass this far in from the end, for a button placed there. */
    public void setGlassMenuEndInset(int inset) {
        if (glassMenuEndInset != inset) {
            glassMenuEndInset = inset;
            requestLayout();
            invalidate();
        }
    }

    public int getGlassMenuEndInset() {
        return glassMenuEndInset;
    }

    // The chats list's iOS-like header has iOS's sizes too, as the chat's (iosHeaderSize): 44pt
    // pieces 16pt from the edges, back to the bar's own for the selection bar
    private boolean glassIosSizes;

    public void setGlassIosSizes(boolean value) {
        if (glassIosSizes != value) {
            glassIosSizes = value;
            requestLayout();
            invalidate();
        }
    }

    /** iOS's sizes of the iOS-like headers in pixels, for the screen's own buttons on them. */
    public int getGlassIosButtonSize() {
        return iosHeaderSize(IOS_BUTTON);
    }

    public int getGlassIosMargin() {
        return iosHeaderSize(IOS_MARGIN);
    }

    public int getGlassIosGap() {
        return iosHeaderSize(IOS_GAP);
    }

    // The iOS-like header's title fits between the buttons beside it, and a status on it (connecting)
    // has no second line under it: the proxy is the shield's on that header
    private int glassTitleMaxWidth;
    private boolean overlaySubtitleHidden;

    /** The widest the title may be, 0 for the bar's own measure. */
    public void setGlassTitleMaxWidth(int width) {
        if (glassTitleMaxWidth != width) {
            glassTitleMaxWidth = width;
            requestLayout();
        }
    }

    /** No line under a status shown in place of the title (Connecting to proxy: Proxy settings). */
    public void setOverlaySubtitleHidden(boolean hidden) {
        overlaySubtitleHidden = hidden;
    }

    /** Shows the title's glass pill only for the selection bar (and search) on it. */
    public void setGlassPillOnlyForActionMode(boolean value) {
        if (glassPillOnlyForActionMode != value) {
            glassPillOnlyForActionMode = value;
            invalidate();
        }
    }

    private float glassMenuLeftDrawn = Float.NaN;

    /**
     * Where the menu's glass starts, in this bar's coordinates, as last drawn (the title is laid out
     * before the menu's glass is drawn); where it would end without items, without one drawn yet.
     */
    public float getGlassMenuLeft() {
        return Float.isNaN(glassMenuLeftDrawn) ? getWidth() - glassMenuEndInset - dp(6) : glassMenuLeftDrawn;
    }

    /* Glassgram: the unread messages of the other chats next to the back chevron, as on iOS */

    private int glassBackCounterValue;
    private String glassBackCounterText;
    private float glassBackCounterWidth;
    private int glassBackCounterTouchExtra;
    private org.telegram.ui.Components.AnimatedFloat glassBackCounterProgress;
    private TextPaint glassBackCounterTextPaint;
    private Paint glassBackCounterPaint;

    /** Shows count (0 hides it) in a badge on the back button's glass, which widens for it. */
    public void setGlassBackCounter(int count, boolean animated) {
        if (!glassMode) {
            return;
        }
        if (glassBackCounterProgress == null) {
            glassBackCounterProgress = new org.telegram.ui.Components.AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            glassBackCounterTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            glassBackCounterTextPaint.setTypeface(AndroidUtilities.bold());
            glassBackCounterTextPaint.setTextSize(dp(14));
            glassBackCounterTextPaint.setTextAlign(Paint.Align.CENTER);
            glassBackCounterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
        if (count > 0) {
            // the text stays while the badge fades out
            glassBackCounterText = count > 999 ? "999+" : String.valueOf(count);
            glassBackCounterWidth = Math.max(dp(22), glassBackCounterTextPaint.measureText(glassBackCounterText) + dp(12));
        }
        glassBackCounterValue = count;
        if (!animated) {
            glassBackCounterProgress.set(count > 0, true);
        }
        // the back button takes the taps on the badge too
        final int touchExtra = count > 0 ? (int) (glassBackCounterWidth - dp(4)) : 0;
        if (touchExtra != glassBackCounterTouchExtra) {
            glassBackCounterTouchExtra = touchExtra;
            if (backButtonImageView != null) {
                backButtonImageView.setPadding(dp(1), 0, touchExtra, 0);
            }
            // The chat's pill has less room beside the wider back glass, and its name is cut sooner
            if (chatAvatarContainer != null) {
                chatAvatarContainer.requestLayout();
                checkAvatarContainerWidth(animated);
            }
            updateGlassBackCounterFollower();
            requestLayout();
        }
        invalidate();
    }

    private View glassBackCounterFollower;
    private int glassBackCounterFollowerMargin;

    /**
     * A title that sits beside the back button without being the bar's chat pill (a forum topic's):
     * it moves right as the back glass widens for the badge, and ends as far from the right.
     */
    public void setGlassBackCounterFollower(View view) {
        glassBackCounterFollower = view;
        if (view != null && view.getLayoutParams() instanceof MarginLayoutParams) {
            glassBackCounterFollowerMargin = ((MarginLayoutParams) view.getLayoutParams()).rightMargin;
        }
        updateGlassBackCounterFollower();
    }

    private void updateGlassBackCounterFollower() {
        final View view = glassBackCounterFollower;
        if (view == null || !(view.getLayoutParams() instanceof MarginLayoutParams)) {
            return;
        }
        // as the chat pill, it is moved by dispatchDraw (with its width kept by the pill's own rules)
        final boolean follows = view != chatAvatarContainer;
        final MarginLayoutParams lp = (MarginLayoutParams) view.getLayoutParams();
        final int margin = glassBackCounterFollowerMargin + (follows ? glassBackCounterTouchExtra : 0);
        if (lp.rightMargin != margin) {
            lp.rightMargin = margin;
            view.requestLayout();
        }
        if (follows) {
            invalidate();
        }
    }

    /**
     * How far the back button's glass is widened for the badge now, and its progress. Selection
     * turns the back button into a close button and hides the badge: the selection bar's text does
     * not move with the glass, and the glass came over it.
     */
    private float glassBackCounterProgress() {
        if (glassBackCounterProgress == null) {
            return 0f;
        }
        return glassBackCounterProgress.set(glassBackCounterValue > 0) * (1f - getActionModeFactor());
    }

    private void drawGlassBackCounter(Canvas canvas) {
        final float progress = glassBackCounterProgress == null ? 0f : glassBackCounterProgress.get() * (1f - getActionModeFactor());
        if (progress <= 0f || glassBackCounterText == null || !glassShown[GLASS_BACK] || backButtonImageView == null) {
            return;
        }
        final RectF r = glassRect[GLASS_BACK];
        final float h = dp(22);
        final float cx = r.right - dp(8) - glassBackCounterWidth / 2f;
        final float cy = r.centerY();
        final float alpha = Math.max(0f, Math.min(1f, progress)) * backButtonImageView.getAlpha() * glassAlpha;
        canvas.save();
        canvas.scale(progress, progress, cx, cy);
        glassBackCounterPaint.setColor(itemsColor);
        glassBackCounterPaint.setAlpha((int) (Color.alpha(itemsColor) * alpha));
        AndroidUtilities.rectTmp.set(cx - glassBackCounterWidth / 2f, cy - h / 2f, cx + glassBackCounterWidth / 2f, cy + h / 2f);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, h / 2f, h / 2f, glassBackCounterPaint);
        glassBackCounterTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        glassBackCounterTextPaint.setAlpha((int) (255 * alpha));
        canvas.drawText(glassBackCounterText, cx, cy - (glassBackCounterTextPaint.descent() + glassBackCounterTextPaint.ascent()) / 2f, glassBackCounterTextPaint);
        canvas.restore();
    }

    public void setChatAvatarContainer(ChatAvatarContainer chatAvatarContainer) {
        this.chatAvatarContainer = chatAvatarContainer;
        updateGlassBackCounterFollower();
    }

    public void setupGlass(BlurredBackgroundDrawableViewFactory factory, BlurredBackgroundColorProvider colorProvider) {
        setupGlass(factory, colorProvider, false);
    }

    public void setupGlass(BlurredBackgroundDrawableViewFactory factory,
                           BlurredBackgroundColorProvider colorProvider,
                           boolean isForum) {
        setBackground(null);
        setClipChildren(false);
        glassMode = true;
        glassModeIsForum = isForum;
        glassFactory = factory;
        glassColorProvider = colorProvider;

        glassDrawable = factory.create(this)
            .setColorProvider(colorProvider)
            .setPadding(dp(6));
        if (isForum) {
            glassDrawable.setRadius(dp(18.33f), dp(23), dp(23), dp(18.33f));
        } else {
            glassDrawable.setRadius(dp(23));
        }


        glassDrawableBack = factory.create(this)
            .setColorProvider(colorProvider)
            .setRadius(dp(23))
            .setPadding(dp(6));

        glassDrawableMenu = factory.create(this)
            .setColorProvider(colorProvider)
            .setRadius(dp(23))
            .setPadding(dp(6));

        if (menu != null) {
            menu.setTranslationX(-dp(10));
            menu.setGlassMode(true);
        }
        if (actionMode != null) {
            actionMode.setTranslationX(-dp(10));
            actionMode.setGlassMode(true);
            actionMode.setClipChildren(false);
            actionMode.setClipToPadding(false);
        }
        if (backButtonImageView != null) {
            backButtonImageView.setTranslationX(dp(2));
        }
    }

    public INavigationLayout.BackButtonState getBackButtonState() {
        if (backButtonDrawable instanceof INavigationLayout.IBackButtonDrawable) {
            return ((INavigationLayout.IBackButtonDrawable) backButtonDrawable).getBackButtonState();
        }
        return backButtonState;
    }

    private void createBackButtonImage() {
        if (backButtonImageView != null) {
            return;
        }
        backButtonImageView = new ImageView(getContext());
        backButtonImageView.setScaleType(ImageView.ScaleType.CENTER);
        backButtonImageView.setBackgroundDrawable(Theme.createSelectorDrawable(itemsBackgroundColor));
        backButtonImageView.setPadding(dp(1), 0, 0, 0);
        addView(backButtonImageView, LayoutHelper.createFrame(54, 54, Gravity.LEFT | Gravity.TOP));

        backButtonImageView.setOnClickListener(v -> {
            if (!actionModeVisible && isSearchFieldVisible) {
                closeSearchField();
                return;
            }
            if (actionBarMenuOnItemClick != null) {
                actionBarMenuOnItemClick.onItemClick(-1);
            }
        });
        backButtonImageView.setContentDescription(LocaleController.getString(R.string.AccDescrGoBack));
    }

    public Drawable getBackButtonDrawable() {
        return backButtonDrawable;
    }

    public void setBackButtonDrawable(Drawable drawable) {
        if (backButtonImageView == null) {
            createBackButtonImage();
        }
        backButtonImageView.setVisibility(drawable == null ? GONE : VISIBLE);
        backButtonImageView.setImageDrawable(backButtonDrawable = drawable);
        if (drawable instanceof BackDrawable) {
            BackDrawable backDrawable = (BackDrawable) drawable;
            backDrawable.setRotation(isActionModeShowed() ? 1 : 0, false);
            backDrawable.setRotatedColor(itemsActionModeColor);
            backDrawable.setColor(itemsColor);
        } else if (drawable instanceof MenuDrawable) {
            MenuDrawable menuDrawable = (MenuDrawable) drawable;
            menuDrawable.setBackColor(actionBarColor);
            menuDrawable.setIconColor(itemsColor);
        } else if (drawable instanceof BitmapDrawable || drawable instanceof VectorDrawable) {
            backButtonImageView.setColorFilter(new PorterDuffColorFilter(itemsColor, PorterDuff.Mode.SRC_IN));
        }
        if (mAlwaysApplyColorFilterToBackButton) {
            backButtonImageView.setColorFilter(new PorterDuffColorFilter(itemsColor, PorterDuff.Mode.SRC_IN));
        }

        checkBackButtonLayerType();
    }

    private void checkBackButtonLayerType() {
        if (backButtonImageView == null) {
            return;
        }

        // BackDrawable is expensive for render thread because it uses PathStencilCoverOp

        final Drawable drawable = backButtonImageView.getDrawable();
        final int layerToSet = (drawable instanceof BackDrawable || drawable instanceof MenuDrawable) ?
            View.LAYER_TYPE_HARDWARE :
            View.LAYER_TYPE_NONE;

        if (backButtonImageView.getLayerType() != layerToSet) {
            backButtonImageView.setLayerType(layerToSet, null);
            backButtonImageView.invalidate();
        }
    }

    public void setBackButtonContentDescription(CharSequence description) {
        if (backButtonImageView != null) {
            backButtonImageView.setContentDescription(description);
        }
    }

    public void setSupportsHolidayImage(boolean value) {
        supportsHolidayImage = value;
        if (supportsHolidayImage) {
            fontMetricsInt = new Paint.FontMetricsInt();
            rect = new Rect();
        }
        invalidate();
    }

    public BackupImageView getSearchAvatarImageView() {
        return avatarSearchImageView;
    }

    public void setSearchAvatarImageView(BackupImageView backupImageView) {
        if (avatarSearchImageView == backupImageView) {
            return;
        }
        if (avatarSearchImageView != null) {
            removeView(avatarSearchImageView);
        }
        avatarSearchImageView = backupImageView;
        if (avatarSearchImageView != null) {
            addView(avatarSearchImageView);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (supportsHolidayImage && !titleOverlayShown && !LocaleController.isRTL && ev.getAction() == MotionEvent.ACTION_DOWN) {
            Drawable drawable = Theme.getCurrentHolidayDrawable();
            if (drawable != null && drawable.getBounds().contains((int) ev.getX(), (int) ev.getY())) {
                manualStart = true;
                if (snowflakesEffect == null) {
                    fireworksEffect = null;
                    snowflakesEffect = new SnowflakesEffect(0);
                    titleTextView[0].invalidate();
                    invalidate();
                } else {
                    snowflakesEffect = null;
                    fireworksEffect = new FireworksEffect();
                    titleTextView[0].invalidate();
                    invalidate();
                }
            }
        }
        return interceptTouchEventListener != null && interceptTouchEventListener.onTouch(this, ev) || super.onInterceptTouchEvent(ev);
    }

    protected boolean shouldClipChild(View child) {
        return clipContent && (child == titleTextView[0] || child == titleTextView[1] || child == subtitleTextView || child == menu || child == backButtonImageView || child == additionalSubtitleTextView || child == titlesContainer);
    }

    // Liquid Glass of the bar's buttons: the pill of the chat title, the back button and the menu.
    // Each piece swells, leans towards the finger and lights up under it like the
    // Kyant0/AndroidLiquidGlass catalog's LiquidButton, together with what it carries.
    private static final int GLASS_NONE = 0, GLASS_PILL = 1, GLASS_BACK = 2, GLASS_MENU = 3;
    private final LiquidPressEffect[] glassPress = new LiquidPressEffect[4];
    private final RectF[] glassRect = {new RectF(), new RectF(), new RectF(), new RectF()};
    private final boolean[] glassShown = new boolean[4];
    private int glassPressed = GLASS_NONE;

    // Glass buttons of screens without a glass backdrop (profile, settings)
    private boolean glassButtons;
    private LiquidGlassButtonBackground glassButtonBack, glassButtonMenu;

    // Buttons added to the bar directly instead of to its menu (shared media's options and save)
    private final ArrayList<View> glassExtraButtons = new ArrayList<>();

    /** A button placed in the bar by someone else; on glass bars it gets a glass capsule like the menu. */
    public void addGlassButtonView(View view) {
        if (!glassExtraButtons.contains(view)) {
            glassExtraButtons.add(view);
        }
    }

    // The iOS-like chat header: at the end, instead of the menu, a view (the avatar) on its own glass
    // circle; the menu comes back only for search, its items live in the profile's menu
    private View glassEndView;

    /** Puts the view (42dp) on a glass circle at the end of the bar and hides the menu outside search; null brings the menu back. */
    public void setGlassEndView(View view) {
        if (glassEndView == view) {
            return;
        }
        if (glassEndView != null) {
            removeView(glassEndView);
        }
        glassEndView = view;
        if (view != null) {
            // Nearly the whole circle, as on iOS: a 2dp rim of glass around it
            final int size = dp(42);
            final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, Gravity.RIGHT | Gravity.BOTTOM);
            // Centered in the menu's glass circle: 6dp + 46dp / 2 from the end, mid-height of the bar
            lp.rightMargin = dp(6 + 23) - size / 2;
            lp.bottomMargin = (getCurrentActionBarHeight() - size) / 2;
            addView(view, lp);
        }
        updateGlassEndMenu();
        invalidate();
    }

    public View getGlassEndView() {
        return glassEndView;
    }

    /** Shows or hides the menu for the end view; call it after making the menu visible. */
    public void updateGlassEndMenu() {
        if (menu != null && menu.getVisibility() != GONE) {
            // Invisible, not gone: it keeps its place, and search opens in it as before
            menu.setVisibility(glassEndView != null && !isSearchFieldVisible ? INVISIBLE : VISIBLE);
        }
    }

    // The selection bar's close button on a bar without a back button (the chats list): it sits on
    // the back button's glass circle, and the pill with the counter starts after it, as in a chat
    private View glassActionModeCloseView;

    /**
     * Gives the selection bar's close button, a view inside the action mode, the back button's glass
     * circle. The view has to be placed over the circle: its center dp(29) from the bar's left.
     */
    public void setGlassActionModeCloseView(View view) {
        glassActionModeCloseView = view;
        invalidate();
    }

    /** Puts the back button and the menu on glass capsules, for bars over a header or a list. */
    public void setGlassButtons() {
        glassButtons = true;
        glassButtonBack = new LiquidGlassButtonBackground();
        glassButtonMenu = new LiquidGlassButtonBackground();
        glassButtonBackdrop = new ViewBehindBackdrop(this);
        if (isAttachedToWindow()) {
            watchGlassButtonBackdrop(true);
        }
        invalidate();
    }

    // The capsules are glass made of what is under the bar: the header, the photo, the list
    private ViewBehindBackdrop glassButtonBackdrop;
    private final RectF glassButtonMovedRect = new RectF();
    private android.view.ViewTreeObserver glassButtonBackdropTree;
    private final android.view.ViewTreeObserver.OnPreDrawListener glassButtonBackdropWatcher = () -> {
        // The bar is not redrawn when only what is under it changes, and its glass would show the old picture
        if (glassButtonBackdrop != null && (glassShown[GLASS_BACK] || glassShown[GLASS_MENU]) && glassButtonBackdrop.isStale()) {
            invalidate();
        }
        return true;
    };

    private void watchGlassButtonBackdrop(boolean watch) {
        if (glassButtonBackdropTree != null && glassButtonBackdropTree.isAlive()) {
            glassButtonBackdropTree.removeOnPreDrawListener(glassButtonBackdropWatcher);
        }
        glassButtonBackdropTree = null;
        if (watch && glassButtonBackdrop != null) {
            glassButtonBackdropTree = getViewTreeObserver();
            glassButtonBackdropTree.addOnPreDrawListener(glassButtonBackdropWatcher);
        }
    }

    /** Whether the bar's buttons are Liquid Glass: they open their menus on a tap, not on a hold or a drag. */
    public boolean hasLiquidGlassButtons() {
        return glassMode || glassButtons;
    }

    // While a glass piece swells and stretches it goes past the bar, so the parent must not clip the bar
    private ViewGroup glassUnclippedParent;

    private void glassUnclip() {
        if (glassUnclippedParent == null && getParent() instanceof ViewGroup) {
            final ViewGroup parent = (ViewGroup) getParent();
            if (parent.getClipChildren()) {
                parent.setClipChildren(false);
                glassUnclippedParent = parent;
            }
        }
    }

    private void glassRestoreClip() {
        if (glassUnclippedParent == null || glassPressed != GLASS_NONE) {
            return;
        }
        for (LiquidPressEffect effect : glassPress) {
            if (effect != null && (effect.isActive() || effect.isLibraryButton())) {
                return;
            }
        }
        glassUnclippedParent.setClipChildren(true);
        glassUnclippedParent.invalidate();
        glassUnclippedParent = null;
    }

    private LiquidPressEffect glassPress(int surface) {
        if (glassPress[surface] == null) {
            glassPress[surface] = new LiquidPressEffect(this);
            // The library's LiquidButton has its shadow around it, past the bar
            glassPress[surface].setUnclipParents(1);
        }
        return glassPress[surface];
    }

    private int glassSurfaceOf(View child) {
        if (!glassMode && !glassButtons) {
            return GLASS_NONE;
        }
        if (child == backButtonImageView) {
            return GLASS_BACK;
        }
        if (glassMode && child != null && child == glassEndView) {
            return GLASS_MENU;
        }
        if (child == menu) {
            // The search field lives in the menu but sits on the pill
            return glassMode && menu.searchFieldVisible() ? GLASS_PILL : GLASS_MENU;
        }
        if (glassButtons && glassExtraButtons.contains(child)) {
            return GLASS_MENU;
        }
        // The pill carries the rest of the bar: the chat's avatar and name, or the title. With the
        // selection bar on top they are hidden under it and must stay put, or they peek out
        if (glassMode && !glassOnlyBack && child != actionMode && !isActionModeShowed()) {
            return GLASS_PILL;
        }
        return GLASS_NONE;
    }

    private void glassTransform(Canvas canvas, int surface) {
        final LiquidPressEffect effect = glassPress[surface];
        if (effect == null || !effect.isActive()) {
            return;
        }
        final RectF r = glassRect[surface];
        canvas.translate(r.left, r.top);
        effect.transform(canvas, r.width(), r.height());
        canvas.translate(-r.left, -r.top);
    }

    /**
     * The selection bar's items sit on the glass pieces too (the counter on the pill, the
     * actions on the menu): each one follows the piece under it. The canvas is the one
     * menuView draws its children with.
     */
    boolean glassTransformActionModeChild(Canvas canvas, View menuView, View child) {
        if (!glassMode || menuView != actionMode) {
            return false;
        }
        final float cx = menuView.getX() + child.getX() + child.getWidth() / 2f;
        final float cy = menuView.getY() + child.getY() + child.getHeight() / 2f;
        for (int surface : new int[]{GLASS_BACK, GLASS_MENU, GLASS_PILL}) {
            final LiquidPressEffect effect = glassPress[surface];
            if (glassShown[surface] && effect != null && effect.isActive() && glassRect[surface].contains(cx, cy)) {
                canvas.translate(-menuView.getX(), -menuView.getY());
                glassTransform(canvas, surface);
                canvas.translate(menuView.getX(), menuView.getY());
                return true;
            }
        }
        return false;
    }

    private void glassGlow(Canvas canvas, int surface) {
        final LiquidPressEffect effect = glassPress[surface];
        if (effect == null || !effect.isActive()) {
            return;
        }
        final RectF r = glassRect[surface];
        canvas.save();
        canvas.translate(r.left, r.top);
        effect.drawGlow(canvas, r.width(), r.height(), Math.min(r.width(), r.height()) / 2f);
        canvas.restore();
    }

    /** Draws a glass piece of the bar with its press effect; padding is the drawable's inset around the glass. */
    private final RectF glassMovedRect = new RectF();

    private void drawGlass(Canvas canvas, Drawable drawable, int surface, int padding) {
        final Rect b = drawable.getBounds();
        final RectF r = glassRect[surface];
        r.set(b.left + padding, b.top + padding, b.right - padding, b.bottom - padding);
        glassShown[surface] = true;
        final BlurredBackgroundDrawable glass = drawable instanceof BlurredBackgroundDrawable ? (BlurredBackgroundDrawable) drawable : null;
        // glassGram: the piece is the library's LiquidButton, its glass and its highlight; what it
        // carries follows its press (glassTransform)
        if (glass != null && r.width() > 0 && r.height() > 0) {
            glass.setRadiusScale(1f);
            if (glassPress(surface).drawGlass(canvas, r.left, r.top, r.right, r.bottom, Math.min(r.width(), r.height()) / 2f, glass, glass.getAlpha())) {
                return;
            }
        }
        final LiquidPressEffect effect = glassPress[surface];
        if (effect == null || !effect.isActive()) {
            if (glass != null) {
                glass.setRadiusScale(1f);
            }
            drawable.draw(canvas);
            return;
        }
        // The glass goes where the press moved it and blurs what is there, instead of stretching
        // the picture it took in place
        effect.mapRect(r.left, r.top, r.right, r.bottom, glassMovedRect);
        // Its corners grow with it, or the round pieces turn square as they stretch
        if (glass != null) {
            glass.setRadiusScale(LiquidPressEffect.radiusScale(r.width(), r.height(), glassMovedRect));
        }
        // The bounds stay moved until the next frame sets them again: the glass is a RenderNode
        // drawn later, and putting the bounds back now would resize it under the drawn frame
        drawable.setBounds(
            Math.round(glassMovedRect.left) - padding, Math.round(glassMovedRect.top) - padding,
            Math.round(glassMovedRect.right) + padding, Math.round(glassMovedRect.bottom) + padding);
        drawable.draw(canvas);
        canvas.save();
        canvas.translate(glassMovedRect.left, glassMovedRect.top);
        effect.drawGlow(canvas, glassMovedRect.width(), glassMovedRect.height(), Math.min(glassMovedRect.width(), glassMovedRect.height()) / 2f);
        canvas.restore();
    }

    /** The glass capsule under the back button or the menu items, or false if there is nothing to put it under. */
    // glassGram: the glass buttons where the iOS-like chat header has its own (getIosHeaderMargin): the
    // profile opens from that header, and its back button and menu stood further out, on smaller circles
    private boolean glassButtonsIos;

    public void setGlassButtonsIosLayout(boolean value) {
        if (glassButtonsIos != value) {
            glassButtonsIos = value;
            requestLayout();
        }
    }

    /** How far in the back button and the menu go for the iOS-like layout, from their 27dp and 24dp centers. */
    private int glassButtonsIosShift(int center) {
        return glassButtons && glassButtonsIos ? iosHeaderSize(IOS_MARGIN) + iosHeaderSize(IOS_BUTTON) / 2 - center : 0;
    }

    private boolean glassButtonRect(View child, RectF out) {
        final float h = glassButtons && glassButtonsIos ? iosHeaderSize(IOS_BUTTON) : dp(40);
        if (child == backButtonImageView) {
            if (child.getVisibility() != VISIBLE || child.getAlpha() <= 0f) {
                return false;
            }
            final float cx = child.getLeft() + child.getTranslationX() + child.getWidth() / 2f;
            final float cy = child.getTop() + child.getTranslationY() + child.getHeight() / 2f;
            final float s = h / 2f * child.getScaleX();
            out.set(cx - s, cy - s, cx + s, cy + s);
            return s > 0;
        }
        if (child != menu && glassExtraButtons.contains(child)) {
            // Shared media's options button is only a touch area over another button's icon, and
            // stays visible without it at the top of the profile: no capsule of its own
            if (child instanceof ImageView && ((ImageView) child).getDrawable() == null) {
                return false;
            }
            if (child.getVisibility() != VISIBLE || child.getAlpha() <= 0f || child.getWidth() <= 0 || isActionModeShowed()) {
                return false;
            }
            final float cx = child.getLeft() + child.getTranslationX() + child.getWidth() / 2f;
            final float cy = child.getTop() + child.getTranslationY() + child.getHeight() / 2f;
            final float w = Math.max(h, child.getWidth() - dp(8)) * child.getScaleX();
            final float hh = h * child.getScaleY();
            out.set(cx - w / 2f, cy - hh / 2f, cx + w / 2f, cy + hh / 2f);
            return w > 0 && hh > 0;
        }
        if (child == menu) {
            if (menu.getVisibility() != VISIBLE || menu.searchFieldVisible() || isActionModeShowed()) {
                return false;
            }
            float left = Float.MAX_VALUE, right = -Float.MAX_VALUE;
            for (int i = 0; i < menu.getChildCount(); i++) {
                final View item = menu.getChildAt(i);
                if (item.getVisibility() != VISIBLE || item.getAlpha() <= 0f || item.getWidth() <= 0) {
                    continue;
                }
                final float l = menu.getX() + item.getX() + item.getWidth() * (1f - item.getScaleX()) / 2f;
                left = Math.min(left, l);
                right = Math.max(right, l + item.getWidth() * item.getScaleX());
            }
            if (left >= right) {
                return false;
            }
            final float inset = Math.min(dp(4), Math.max(0, (right - left - h) / 2f));
            left += inset;
            right -= inset;
            final float cy = menu.getY() + menu.getHeight() / 2f;
            out.set(left, cy - h / 2f, right, cy + h / 2f);
            return true;
        }
        return false;
    }

    private float glassButtonAlpha(View child) {
        float alpha = child.getAlpha();
        if (child == menu) {
            float items = 0f;
            for (int i = 0; i < menu.getChildCount(); i++) {
                final View item = menu.getChildAt(i);
                if (item.getVisibility() == VISIBLE) {
                    items = Math.max(items, item.getAlpha());
                }
            }
            alpha *= items;
        }
        return alpha;
    }

    private void glassTouch(MotionEvent ev, boolean handled) {
        if (!glassMode && !glassButtons) {
            return;
        }
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            glassPressed = GLASS_NONE;
            if (!handled) {
                return;
            }
            for (int surface : new int[]{GLASS_BACK, GLASS_MENU, GLASS_PILL}) {
                if (glassShown[surface] && glassRect[surface].contains(ev.getX(), ev.getY())) {
                    glassPressed = surface;
                    break;
                }
            }
        }
        if (glassPressed == GLASS_NONE) {
            return;
        }
        final RectF r = glassRect[glassPressed];
        if (action == MotionEvent.ACTION_DOWN) {
            glassUnclip();
        }
        glassPress(glassPressed).onTouch(action, ev.getX() - r.left, ev.getY() - r.top);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            glassPressed = GLASS_NONE;
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        final int surface = glassSurfaceOf(child);
        if (surface == GLASS_NONE) {
            return drawChildContent(canvas, child, drawingTime);
        }
        canvas.save();
        if (glassButtons && (surface == GLASS_BACK || surface == GLASS_MENU)) {
            final LiquidGlassButtonBackground glass = surface == GLASS_BACK ? glassButtonBack : glassButtonMenu;
            final boolean shown = !(drawBackButton && child == backButtonImageView) && glassButtonRect(child, glassRect[surface]);
            // A hidden extra button must not hide the menu's capsule drawn before it
            if (shown || !glassExtraButtons.contains(child)) {
                glassShown[surface] = shown;
            }
            if (shown) {
                final RectF r = glassRect[surface];
                final LiquidPressEffect effect = glassPress[surface];
                final boolean pressed = effect != null && effect.isActive();
                // Pressed, the glass goes where the press moved it and is made of what is under it
                // there, instead of the canvas stretching the picture it took in place
                final RectF g = pressed ? glassButtonMovedRect : r;
                if (pressed) {
                    effect.mapRect(r.left, r.top, r.right, r.bottom, glassButtonMovedRect);
                }
                glass.setColors(itemsColor, Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                glass.setAlpha((int) (255 * glassButtonAlpha(child)));
                // glassGram: the capsule is the library's LiquidButton over what is under the bar
                glassButtonBackdrop.setArea(r);
                final GlassLayer.Backdrop liquidBackdrop = !glassButtonBackdrop.isDrawing() && glassButtonBackdrop.isAvailable() ? glassButtonBackdrop : null;
                final boolean liquid = glassPress(surface).drawGlass(canvas, r.left, r.top, r.right, r.bottom, Math.min(r.width(), r.height()) / 2f,
                    liquidBackdrop, glass.getGlassTint(), 0, (int) (255 * glassButtonAlpha(child)));
                if (!liquid) {
                    glass.setBounds(Math.round(g.left), Math.round(g.top), Math.round(g.right), Math.round(g.bottom));
                    glassButtonBackdrop.setArea(g);
                    glass.setBackdrop(!glassButtonBackdrop.isDrawing() && glassButtonBackdrop.isAvailable() ? glassButtonBackdrop : null);
                    glass.draw(canvas);
                    if (pressed) {
                        canvas.save();
                        canvas.translate(g.left, g.top);
                        effect.drawGlow(canvas, g.width(), g.height(), Math.min(g.width(), g.height()) / 2f);
                        canvas.restore();
                    }
                }
                // The icon follows the glass through the canvas
                glassTransform(canvas, surface);
            }
        } else {
            glassTransform(canvas, surface);
        }
        final boolean result = drawChildContent(canvas, child, drawingTime);
        if (glassMode && child == backButtonImageView) {
            drawGlassBackCounter(canvas);
        }
        canvas.restore();
        return result;
    }

    private boolean drawChildContent(Canvas canvas, View child, long drawingTime) {
        if (parentFragment != null && parentFragment.getParentLayout() != null && parentFragment.getParentLayout().isActionBarInCrossfade()) {
            return false;
        }
        if (drawBackButton && child == backButtonImageView) {
            return true;
        }

        boolean clip = shouldClipChild(child);
        if (clip) {
            canvas.save();
            canvas.clipRect(0, -getTranslationY() + (occupyStatusBar ? AndroidUtilities.statusBarHeight : 0), getMeasuredWidth(), getMeasuredHeight());
        }
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (supportsHolidayImage && !titleOverlayShown && !LocaleController.isRTL && (child == titleTextView[0] || child == titleTextView[1] || child == titlesContainer && useContainerForTitles)) {
            Drawable drawable = Theme.getCurrentHolidayDrawable();
            if (drawable != null) {
                SimpleTextView titleView = child == titlesContainer ? titleTextView[0] : (SimpleTextView) child;
                if (titleView != null && titleView.getVisibility() == View.VISIBLE && titleView.getText() instanceof String) {
                    TextPaint textPaint = titleView.getTextPaint();
                    textPaint.getFontMetricsInt(fontMetricsInt);
                    textPaint.getTextBounds((String) titleView.getText(), 0, 1, rect);
                    int x = titleView.getTextStartX() + Theme.getCurrentHolidayDrawableXOffset() + (rect.width() - (drawable.getIntrinsicWidth() + Theme.getCurrentHolidayDrawableXOffset())) / 2;
                    int y = titleView.getTextStartY() + Theme.getCurrentHolidayDrawableYOffset() + (int) Math.ceil((titleView.getTextHeight() - rect.height()) / 2.0f) + (int) (dp(8) * (1f - titlesContainer.getScaleY()));
                    drawable.setBounds(x, y - drawable.getIntrinsicHeight(), x + drawable.getIntrinsicWidth(), y);
                    drawable.setAlpha((int) (255 * titlesContainer.getAlpha() * titleView.getAlpha()));
                    drawable.draw(canvas);
                    if (overlayTitleAnimationInProgress) {
                        child.invalidate();
                        invalidate();
                    }
                }

                if (Theme.canStartHolidayAnimation()) {
                    if (snowflakesEffect == null) {
                        snowflakesEffect = new SnowflakesEffect(0);
                    }
                } else if (!manualStart) {
                    if (snowflakesEffect != null) {
                        snowflakesEffect = null;
                    }
                }
                if (snowflakesEffect != null) {
                    snowflakesEffect.onDraw(this, canvas);
                } else if (fireworksEffect != null) {
                    fireworksEffect.onDraw(this, canvas);
                }
            }
        }
        if (clip) {
            canvas.restore();
        }
        return result;
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        if (clipContent) {
            invalidate();
        }
    }

    public void setBackButtonImage(int resource) {
        if (backButtonImageView == null) {
            createBackButtonImage();
        }
        backButtonImageView.setVisibility(resource == 0 ? GONE : VISIBLE);
        backButtonImageView.setImageResource(resource);
        backButtonImageView.setColorFilter(new PorterDuffColorFilter(itemsColor, PorterDuff.Mode.SRC_IN));
        checkBackButtonLayerType();
    }

    private boolean mAlwaysApplyColorFilterToBackButton;

    public void alwaysApplyColorFilterToBackButton() {
        mAlwaysApplyColorFilterToBackButton = true;
    }

    private void createSubtitleTextView() {
        if (subtitleTextView != null) {
            return;
        }
        subtitleTextView = new SimpleTextView(getContext());
        subtitleTextView.setGravity(Gravity.LEFT);
        subtitleTextView.setVisibility(GONE);
        subtitleTextView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
        addView(subtitleTextView, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
    }

    public void createAdditionalSubtitleTextView() {
        if (additionalSubtitleTextView != null) {
            return;
        }
        additionalSubtitleTextView = new SimpleTextView(getContext());
        additionalSubtitleTextView.setGravity(Gravity.LEFT);
        additionalSubtitleTextView.setVisibility(GONE);
        additionalSubtitleTextView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
        addView(additionalSubtitleTextView, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
    }

    public SimpleTextView getAdditionalSubtitleTextView() {
        return additionalSubtitleTextView;
    }

    public void setAddToContainer(boolean value) {
        addToContainer = value;
    }

    public boolean shouldAddToContainer() {
        return addToContainer;
    }

    public void setClipContent(boolean value) {
        clipContent = value;
    }

    public void setSubtitle(CharSequence value) {
        if (value != null && subtitleTextView == null) {
            createSubtitleTextView();
        }
        if (subtitleTextView != null) {
            boolean isEmpty = TextUtils.isEmpty(value);
            subtitleTextView.setVisibility(!isEmpty && !isSearchFieldVisible ? VISIBLE : GONE);
            subtitleTextView.setAlpha(1f);
            if (!isEmpty) {
                subtitleTextView.setText(value);
            }
            subtitle = value;
        }
    }

    private void createTitleTextView(int i) {
        if (titleTextView[i] != null) {
            return;
        }
        titleTextView[i] = new SimpleTextView(getContext());
        titleTextView[i].setGravity(isCenterTitle ? Gravity.CENTER : Gravity.LEFT | Gravity.CENTER_VERTICAL);
        if (titleColorToSet != 0) {
            titleTextView[i].setTextColor(titleColorToSet);
        } else {
            titleTextView[i].setTextColor(getThemedColor(Theme.key_actionBarDefaultTitle));
        }
        titleTextView[i].setEmojiColor(titleTextView[i].getTextColor());
        titleTextView[i].setTypeface(AndroidUtilities.bold());
        titleTextView[i].setDrawablePadding(dp(4));
        titleTextView[i].setPadding(0, dp(8), 0, dp(8));
        titleTextView[i].setRightDrawableTopPadding(-dp(1));
        if (useContainerForTitles) {
            titlesContainer.addView(titleTextView[i], 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        } else {
            addView(titleTextView[i], 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        }
    }

    private boolean isCenterTitle;

    public void centerTitle() {
        isCenterTitle = true;
        if (titleTextView != null) {
            for (int a = 0; a < titleTextView.length; a++) {
                if (titleTextView[a] != null) {
                    titleTextView[a].setGravity(Gravity.CENTER);
                }
            }
        }
    }

    public void setTitleRightMargin(int value) {
        titleRightMargin = value;
    }

    public void setTitle(CharSequence value) {
        setTitle(value, null);
    }

    public void setTitle(CharSequence value, Drawable rightDrawable) {
        if (value != null && titleTextView[0] == null) {
            createTitleTextView(0);
        }
        if (titleTextView[0] != null) {
            titleTextView[0].setVisibility(value != null && !isSearchFieldVisible ? VISIBLE : INVISIBLE);
            titleTextView[0].setText(lastTitle = value);
            if (attached && lastRightDrawable instanceof AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) lastRightDrawable).setParentView(null);
            }
            titleTextView[0].setRightDrawable(lastRightDrawable = rightDrawable);
            if (attached && lastRightDrawable instanceof AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) lastRightDrawable).setParentView(titleTextView[0]);
            }
            titleTextView[0].setRightDrawableOnClick(rightDrawableOnClickListener);
        }
        fromBottom = false;
    }

    public void setRightDrawableOnClick(OnClickListener onClickListener) {
        rightDrawableOnClickListener = onClickListener;
        if (titleTextView[0] != null) {
            titleTextView[0].setRightDrawableOnClick(rightDrawableOnClickListener);
        }
        if (titleTextView[1] != null) {
            titleTextView[1].setRightDrawableOnClick(rightDrawableOnClickListener);
        }
    }

    public void setTitleColor(int color) {
        if (titleTextView[0] == null) {
            createTitleTextView(0);
        }
        titleColorToSet = color;
        titleTextView[0].setTextColor(color);
        titleTextView[0].setEmojiColor(color);
        if (titleTextView[1] != null) {
            titleTextView[1].setTextColor(color);
            titleTextView[1].setEmojiColor(color);
        }
    }

    public void setSubtitleColor(int color) {
        if (subtitleTextView == null) {
            createSubtitleTextView();
        }
        subtitleTextView.setTextColor(color);
    }

    public void setTitleScrollNonFitText(boolean b) {
        titleTextView[0].setScrollNonFitText(b);
    }

    public void setPopupItemsColor(int color, boolean icon, boolean forActionMode) {
        if (forActionMode && actionMode != null) {
            actionMode.setPopupItemsColor(color, icon);
        } else if (!forActionMode && menu != null) {
            menu.setPopupItemsColor(color, icon);
        }
    }

    public void setPopupItemsSelectorColor(int color, boolean forActionMode) {
        if (forActionMode && actionMode != null) {
            actionMode.setPopupItemsSelectorColor(color);
        } else if (!forActionMode && menu != null) {
            menu.setPopupItemsSelectorColor(color);
        }
    }

    public void setPopupBackgroundColor(int color, boolean forActionMode) {
        if (forActionMode && actionMode != null) {
            actionMode.redrawPopup(color);
        } else if (!forActionMode && menu != null) {
            menu.redrawPopup(color);
        }
    }

    public SimpleTextView getSubtitleTextView() {
        return subtitleTextView;
    }

    public SimpleTextView getTitleTextView() {
        return titleTextView[0];
    }

    public Paint.FontMetricsInt getTitleFontMetricsInt() {
        if (titleTextView[0] == null) {
            TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            paint.setTextSize(dp(!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 18 : 20));
            return paint.getFontMetricsInt();
        }
        return titleTextView[0].getPaint().getFontMetricsInt();
    }

    public SimpleTextView getTitleTextView2() {
        return titleTextView[1];
    }

    public String getTitle() {
        if (titleTextView[0] == null) {
            return null;
        }
        return titleTextView[0].getText().toString();
    }

    public String getSubtitle() {
        if (subtitleTextView == null || subtitle == null) {
            return null;
        }
        return subtitle.toString();
    }

    public ActionBarMenu createMenu() {
        if (menu != null) {
            return menu;
        }
        menu = new ActionBarMenu(getContext(), this);
        addView(menu, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT));
        return menu;
    }

    public void setActionBarMenuOnItemClick(ActionBarMenuOnItemClick listener) {
        actionBarMenuOnItemClick = listener;
    }

    public ActionBarMenuOnItemClick getActionBarMenuOnItemClick() {
        return actionBarMenuOnItemClick;
    }

    public ImageView getBackButton() {
        return backButtonImageView;
    }

    public ActionBarMenu createActionMode() {
        return createActionMode(true, null);
    }

    public boolean actionModeIsExist(String tag) {
        if (actionMode != null && ((actionModeTag == null && tag == null) || (actionModeTag != null && actionModeTag.equals(tag)))) {
            return true;
        }
        return false;
    }

    private Runnable doOnActionModeFactorChanged;

    public void setOnActionModeFactorChangeListener(Runnable listener) {
        doOnActionModeFactorChanged = listener;
    }

    public float getActionModeFactor() {
        return actionMode != null ? actionMode.getAlpha() : 0;
    }

    public ActionBarMenu createActionMode(boolean needTop, String tag) {
        if (actionModeIsExist(tag)) {
            return actionMode;
        }
        if (actionMode != null) {
            removeView(actionMode);
            actionMode = null;
        }
        actionModeTag = tag;
        actionMode = new ActionBarMenu(getContext(), this) {
            @Override
            public void setBackgroundColor(int color) {
                actionModeColor = color;
                // On glass the selection bar sits on the glass pieces and has no background of its own
                if (!blurredBackground && !glassMode) {
                    super.setBackgroundColor(actionModeColor);
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                // The old bar's scrim would cover the glass drawn under the selection bar, which then
                // only showed where a press stretched it past the scrim's edge
                if (blurredBackground && drawBlur && actionModeColor != 0 && !glassMode) {
                    rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    blurScrimPaint.setColor(actionModeColor);
                    contentView.drawBlurRect(canvas, 0, rectTmp, blurScrimPaint, true);
                }
                super.dispatchDraw(canvas);
            }

            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                ActionBar.this.invalidate();
                if (doOnActionModeFactorChanged != null) {
                    doOnActionModeFactorChanged.run();
                }
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                if (contentView != null) {
                    contentView.blurBehindViews.add(this);
                }
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                if (contentView != null) {
                    contentView.blurBehindViews.remove(this);
                }
            }
        };
        actionMode.setTranslationX(glassMode ? -dp(10) : 0);
        if (glassMode) {
            // Its items stretch with the glass under them, past the selection bar's edges
            actionMode.setClipChildren(false);
            actionMode.setClipToPadding(false);
        }
        actionMode.setGlassMode(glassMode);
        actionMode.isActionMode = true;
        actionMode.setClickable(true);
        if (!glassMode) {
            actionMode.setBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefault));
        }
        addView(actionMode, indexOfChild(backButtonImageView));
        actionMode.setPadding(0, occupyStatusBar ? AndroidUtilities.statusBarHeight : 0, 0, 0);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) actionMode.getLayoutParams();
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.bottomMargin = extraHeight;
        layoutParams.gravity = Gravity.RIGHT;
        actionMode.setLayoutParams(layoutParams);
        actionMode.setVisibility(INVISIBLE);

//        if (occupyStatusBar && needTop && actionModeTop == null && !blurredBackground) {
//            actionModeTop = new View(getContext());
//            actionModeTop.setBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefaultTop));
//            addView(actionModeTop);
//            layoutParams = (FrameLayout.LayoutParams) actionModeTop.getLayoutParams();
//            layoutParams.height = AndroidUtilities.statusBarHeight;
//            layoutParams.width = LayoutHelper.MATCH_PARENT;
//            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
//            actionModeTop.setLayoutParams(layoutParams);
//            actionModeTop.setVisibility(INVISIBLE);
//        }

        return actionMode;
    }

    public void onDrawCrossfadeContent(Canvas canvas, boolean front, boolean hideBackDrawable, float progress) {
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if ((!hideBackDrawable || ch != backButtonImageView) && ch.getVisibility() == View.VISIBLE && ch instanceof ActionBarMenu) {
                canvas.save();
                canvas.translate(ch.getX(), ch.getY());
                ch.draw(canvas);
                canvas.restore();
            }
        }

        canvas.save();
        canvas.translate(front ? getWidth() * progress * 0.5f : -getWidth() * 0.4f * (1f - progress), 0);
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if ((!hideBackDrawable || ch != backButtonImageView) && ch.getVisibility() == View.VISIBLE && !(ch instanceof ActionBarMenu)) {
                canvas.save();
                canvas.translate(ch.getX(), ch.getY());
                ch.draw(canvas);
                canvas.restore();
            }
        }
        canvas.restore();
    }

    public void showActionMode() {
        showActionMode(true, null, null, null, null, null, 0);
    }

    public void showActionMode(boolean animated) {
        showActionMode(animated, null, null, null, null, null, 0);
    }

    public void showActionMode(boolean animated, View extraView, View showingView, View[] hidingViews, boolean[] hideView, View translationView, int translation) {
        if (actionMode == null || actionModeVisible) {
            return;
        }
        actionModeVisible = true;
        checkMenuItemsWidth();
        if (animated) {
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(actionMode, View.ALPHA, 0.0f, 1.0f));
            if (hidingViews != null) {
                for (int a = 0; a < hidingViews.length; a++) {
                    if (hidingViews[a] != null) {
                        animators.add(ObjectAnimator.ofFloat(hidingViews[a], View.ALPHA, 1.0f, 0.0f));
                    }
                }
            }
            if (showingView != null) {
                animators.add(ObjectAnimator.ofFloat(showingView, View.ALPHA, 0.0f, 1.0f));
            }
            if (translationView != null) {
                animators.add(ObjectAnimator.ofFloat(translationView, View.TRANSLATION_Y, translation));
                actionModeTranslationView = translationView;
            }
            actionModeExtraView = extraView;
            actionModeShowingView = showingView;
            actionModeHidingViews = hidingViews;
            if (actionModeExtraView != null) {
                animators.add(ObjectAnimator.ofFloat(actionModeExtraView, View.TRANSLATION_Y, 0));
            }
            if (actionModeColor == 0) {
                if (!isSearchFieldVisible) {
                    if (titleTextView[0] != null) {
                        animators.add(ObjectAnimator.ofFloat(titleTextView[0], View.ALPHA, 0));
                    }
                    if (subtitleTextView != null && !TextUtils.isEmpty(subtitle)) {
                        animators.add(ObjectAnimator.ofFloat(subtitleTextView, View.ALPHA, 0));
                    }
                }
                if (menu != null) {
                    animators.add(ObjectAnimator.ofFloat(menu, View.ALPHA, 0));
                }
            }
            final int color = actionModeColor == 0 ? actionBarColor : actionModeColor;
            if (color == 0 || glassMode) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors);
            } else if (ColorUtils.calculateLuminance(color) < 0.7f) {
                AndroidUtilities.setLightStatusBar((Activity) getContext(), false);
            } else {
                AndroidUtilities.setLightStatusBar((Activity) getContext(), true);
            }
            if (actionModeAnimation != null) {
                actionModeAnimation.cancel();
            }
            actionModeAnimation = new AnimatorSet();
            actionModeAnimation.playTogether(animators);
            if (backgroundUpdateListener != null) {
                ValueAnimator alphaUpdate = ValueAnimator.ofFloat(0, 1);
                alphaUpdate.addUpdateListener(anm -> {
                    if (backgroundUpdateListener != null) {
                        backgroundUpdateListener.run();
                    }
                });
                actionModeAnimation.playTogether(alphaUpdate);
            }
            actionModeAnimation.setDuration(200);
            actionModeAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    actionMode.setVisibility(VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (actionModeAnimation != null && actionModeAnimation.equals(animation)) {
                        actionModeAnimation = null;
                        if (titleTextView[0] != null) {
                            titleTextView[0].setVisibility(INVISIBLE);
                        }
                        if (subtitleTextView != null && !TextUtils.isEmpty(subtitle)) {
                            subtitleTextView.setVisibility(INVISIBLE);
                        }
                        if (menu != null) {
                            menu.setVisibility(INVISIBLE);
                        }
                        if (actionModeHidingViews != null) {
                            for (int a = 0; a < actionModeHidingViews.length; a++) {
                                if (actionModeHidingViews[a] != null) {
                                    if (hideView == null || a >= hideView.length || hideView[a]) {
                                        actionModeHidingViews[a].setVisibility(INVISIBLE);
                                    }
                                }
                            }
                        }
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (actionModeAnimation != null && actionModeAnimation.equals(animation)) {
                        actionModeAnimation = null;
                    }
                }
            });
            actionModeAnimation.start();
            if (backButtonImageView != null) {
                Drawable drawable = backButtonImageView.getDrawable();
                if (drawable instanceof BackDrawable) {
                    ((BackDrawable) drawable).setRotation(1, true);
                }
                backButtonImageView.setBackgroundDrawable(Theme.createSelectorDrawable(itemsActionModeBackgroundColor));
            }
        } else {
            actionMode.setAlpha(1.0f);
            if (hidingViews != null) {
                for (int a = 0; a < hidingViews.length; a++) {
                    if (hidingViews[a] != null) {
                        hidingViews[a].setAlpha(0.0f);
                    }
                }
            }
            if (showingView != null) {
                showingView.setAlpha(1.0f);
            }
            if (translationView != null) {
                translationView.setTranslationY(translation);
                actionModeTranslationView = translationView;
            }
            actionModeExtraView = extraView;
            if (actionModeExtraView != null) {
                actionModeExtraView.setTranslationY(0);
            }
            actionModeShowingView = showingView;
            actionModeHidingViews = hidingViews;
            final int color = actionModeColor == 0 ? actionBarColor : actionModeColor;
            if (color == 0 || glassMode) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors);
            } else if (ColorUtils.calculateLuminance(color) < 0.7f) {
                AndroidUtilities.setLightStatusBar((Activity) getContext(), false);
            } else {
                AndroidUtilities.setLightStatusBar((Activity) getContext(), true);
            }
            actionMode.setVisibility(VISIBLE);
            if (titleTextView[0] != null) {
                titleTextView[0].setVisibility(INVISIBLE);
            }
            if (subtitleTextView != null && !TextUtils.isEmpty(subtitle)) {
                subtitleTextView.setVisibility(INVISIBLE);
            }
            if (menu != null) {
                menu.setVisibility(INVISIBLE);
            }
            if (actionModeHidingViews != null) {
                for (int a = 0; a < actionModeHidingViews.length; a++) {
                    if (actionModeHidingViews[a] != null) {
                        if (hideView == null || a >= hideView.length || hideView[a]) {
                            actionModeHidingViews[a].setVisibility(INVISIBLE);
                        }
                    }
                }
            }
            if (backButtonImageView != null) {
                Drawable drawable = backButtonImageView.getDrawable();
                if (drawable instanceof BackDrawable) {
                    ((BackDrawable) drawable).setRotation(1, false);
                }
                backButtonImageView.setBackgroundDrawable(Theme.createSelectorDrawable(itemsActionModeBackgroundColor));
            }
        }
    }

    public void hideActionMode() {
        if (actionMode == null || !actionModeVisible) {
            return;
        }
        actionMode.hideAllPopupMenus();
        actionModeVisible = false;
        checkMenuItemsWidth();
        ArrayList<Animator> animators = new ArrayList<>();
        animators.add(ObjectAnimator.ofFloat(actionMode, View.ALPHA, 0.0f));
        if (actionModeHidingViews != null) {
            for (int a = 0; a < actionModeHidingViews.length; a++) {
                if (actionModeHidingViews[a] != null) {
                    actionModeHidingViews[a].setVisibility(VISIBLE);
                    animators.add(ObjectAnimator.ofFloat(actionModeHidingViews[a], View.ALPHA, 1.0f));
                }
            }
        }
        if (actionModeTranslationView != null) {
            animators.add(ObjectAnimator.ofFloat(actionModeTranslationView, View.TRANSLATION_Y, 0.0f));
            actionModeTranslationView = null;
        }
        if (actionModeShowingView != null) {
            animators.add(ObjectAnimator.ofFloat(actionModeShowingView, View.ALPHA, 0.0f));
        }
        if (actionModeExtraView != null) {
            animators.add(ObjectAnimator.ofFloat(actionModeExtraView, View.TRANSLATION_Y, actionModeExtraView.getMeasuredHeight()));
        }
        if (!isSearchFieldVisible) {
            if (titleTextView[0] != null) {
                animators.add(ObjectAnimator.ofFloat(titleTextView[0], View.ALPHA, 1));
            }
            if (subtitleTextView != null && !TextUtils.isEmpty(subtitle)) {
                animators.add(ObjectAnimator.ofFloat(subtitleTextView, View.ALPHA, 1));
            }
        }
        if (menu != null) {
            animators.add(ObjectAnimator.ofFloat(menu, View.ALPHA, 1));
        }
        if (actionBarColor == 0 || glassMode) {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors);
        } else if (ColorUtils.calculateLuminance(actionBarColor) < 0.7f) {
            AndroidUtilities.setLightStatusBar((Activity) getContext(), false);
        } else {
            AndroidUtilities.setLightStatusBar((Activity) getContext(), true);
        }
        if (actionModeAnimation != null) {
            actionModeAnimation.cancel();
        }
        actionModeAnimation = new AnimatorSet();
        actionModeAnimation.playTogether(animators);
        if (backgroundUpdateListener != null) {
            ValueAnimator alphaUpdate = ValueAnimator.ofFloat(0, 1);
            alphaUpdate.addUpdateListener(anm -> {
                if (backgroundUpdateListener != null) {
                    backgroundUpdateListener.run();
                }
            });
            actionModeAnimation.playTogether(alphaUpdate);
        }
        actionModeAnimation.setDuration(200);
        actionModeAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (actionModeAnimation != null && actionModeAnimation.equals(animation)) {
                    actionModeAnimation = null;
                    actionMode.setVisibility(INVISIBLE);
                    if (actionModeExtraView != null) {
                        actionModeExtraView.setVisibility(INVISIBLE);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (actionModeAnimation != null && actionModeAnimation.equals(animation)) {
                    actionModeAnimation = null;
                }
            }
        });
        actionModeAnimation.start();
        if (!isSearchFieldVisible) {
            if (titleTextView[0] != null) {
                titleTextView[0].setVisibility(VISIBLE);
            }
            if (subtitleTextView != null && !TextUtils.isEmpty(subtitle)) {
                subtitleTextView.setVisibility(VISIBLE);
            }
        }
        if (menu != null) {
            menu.setVisibility(VISIBLE);
            // Back from selection the menu stays hidden behind the end view
            updateGlassEndMenu();
        }
        if (backButtonImageView != null) {
            Drawable drawable = backButtonImageView.getDrawable();
            if (drawable instanceof BackDrawable) {
                ((BackDrawable) drawable).setRotation(0, true);
            }
            backButtonImageView.setBackgroundDrawable(Theme.createSelectorDrawable(itemsBackgroundColor));
        }
    }

    public void showActionModeTop() {
        if (occupyStatusBar && actionModeTop == null) {
            actionModeTop = new View(getContext());
            actionModeTop.setBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefaultTop));
            addView(actionModeTop);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) actionModeTop.getLayoutParams();
            layoutParams.height = AndroidUtilities.statusBarHeight;
            layoutParams.width = LayoutHelper.MATCH_PARENT;
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            actionModeTop.setLayoutParams(layoutParams);
        }
    }

    public void setActionModeTopColor(int color) {
        if (actionModeTop != null) {
            actionModeTop.setBackgroundColor(color);
        }
    }

    public void setSearchTextColor(int color, boolean placeholder) {
        if (menu != null) {
            menu.setSearchTextColor(color, placeholder);
        }
    }

    public void setSearchCursorColor(int color) {
        if (menu != null) {
            menu.setSearchCursorColor(color);
        }
    }

    public void setActionModeColor(int color) {
        if (actionMode != null) {
            actionMode.setBackgroundColor(color);
        }
    }

    public void setActionModeOverrideColor(int color) {
        actionModeColor = color;
    }

    @Override
    public void setBackgroundColor(int color) {
        actionBarColor = color;
        if (!blurredBackground) {
            super.setBackgroundColor(actionBarColor);
        }
        if (backButtonImageView != null) {
            Drawable drawable = backButtonImageView.getDrawable();
            if (drawable instanceof MenuDrawable) {
                ((MenuDrawable) drawable).setBackColor(color);
            }
        }
    }

    public int getBackgroundColor() {
        return actionBarColor;
    }

    public boolean isActionModeShowed() {
        return actionMode != null && actionModeVisible;
    }

    public boolean isActionModeShowed(String tag) {
        return actionMode != null && actionModeVisible && ((actionModeTag == null && tag == null) || (actionModeTag != null && actionModeTag.equals(tag)));
    }

    Runnable backgroundUpdateListener;
    AnimatorSet searchVisibleAnimator;

    public void listenToBackgroundUpdate(Runnable invalidate) {
        backgroundUpdateListener = invalidate;
    }

    protected boolean onSearchChangedIgnoreTitles() {
        return false;
    }

    public void onSearchFieldVisibilityChanged(boolean visible) {
        isSearchFieldVisible = visible;
        updateGlassEndMenu();
        checkMenuItemsWidth();
        if (searchVisibleAnimator != null) {
            searchVisibleAnimator.cancel();
        }
        searchVisibleAnimator = new AnimatorSet();
        final ArrayList<View> viewsToHide = new ArrayList<>();

        final boolean ignoreTitles = onSearchChangedIgnoreTitles();
        if (!ignoreTitles) {
            if (titleTextView[0] != null) {
                viewsToHide.add(titleTextView[0]);
            }

            if (subtitleTextView != null && !TextUtils.isEmpty(subtitle)) {
                viewsToHide.add(subtitleTextView);
                subtitleTextView.setVisibility(visible ? INVISIBLE : VISIBLE);
            }
        }

        ValueAnimator alphaUpdate = ValueAnimator.ofFloat(searchFieldVisibleAlpha, visible ? 1f : 0f);
        alphaUpdate.addUpdateListener(anm -> {
            searchFieldVisibleAlpha = (float) anm.getAnimatedValue();

            if (glassDrawable != null && glassModeIsForum) {
                final float r1 = dp(23);
                final float r2 = lerp(dp(18.33f), dp(23), searchFieldVisibleAlpha);
                glassDrawable.setRadius(r2, r1, r1, r2);
                invalidate();
            }

            if (glassMode && menu != null) {
                menu.setTranslationX(-lerp((float) dp(10), dp(5), searchFieldVisibleAlpha));
            }
            if (backgroundUpdateListener != null) {
                backgroundUpdateListener.run();
            }
        });
        searchVisibleAnimator.playTogether(alphaUpdate);

        for (int i = 0; i < viewsToHide.size(); i++) {
            View view = viewsToHide.get(i);
            if (!visible) {
                view.setVisibility(View.VISIBLE);
                view.setAlpha(0);
                view.setScaleX(0.95f);
                view.setScaleY(0.95f);
            }
            searchVisibleAnimator.playTogether(ObjectAnimator.ofFloat(view, View.ALPHA, visible ? 0f : 1f));
            searchVisibleAnimator.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_Y, visible ? 0.95f : 1f));
            searchVisibleAnimator.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, visible ? 0.95f : 1f));
        }
        if (avatarSearchImageView != null) {
            avatarSearchImageView.setVisibility(View.VISIBLE);
            searchVisibleAnimator.playTogether(ObjectAnimator.ofFloat(avatarSearchImageView, View.ALPHA, visible ? 1f : 0f));
        }
        centerScale = true;
        requestLayout();
        searchVisibleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                for (int i = 0; i < viewsToHide.size(); i++) {
                    View view = viewsToHide.get(i);
                    if (visible) {
                        view.setVisibility(View.INVISIBLE);
                        view.setAlpha(0);
                    } else {
                        view.setAlpha(1f);
                    }
                }

                if (visible && !ignoreTitles) {
                    if (titleTextView[0] != null) {
                        titleTextView[0].setVisibility(View.GONE);
                    }
                    if (titleTextView[1] != null) {
                        titleTextView[1].setVisibility(View.GONE);
                    }
                }

                if (avatarSearchImageView != null) {
                    if (!visible) {
                        avatarSearchImageView.setVisibility(View.GONE);
                    }
                }
            }
        });

        searchVisibleAnimator.setDuration(150).start();

        if (backButtonImageView != null) {
            Drawable drawable = backButtonImageView.getDrawable();
            if (drawable instanceof MenuDrawable) {
                MenuDrawable menuDrawable = (MenuDrawable) drawable;
                menuDrawable.setRotateToBack(true);
                menuDrawable.setRotation(visible ? 1 : 0, true);
            }
        }
    }

    public void setInterceptTouches(boolean value) {
        interceptTouches = value;
    }

    public void setInterceptTouchEventListener(View.OnTouchListener listener) {
        interceptTouchEventListener = listener;
    }

    public void setExtraHeight(int value) {
        extraHeight = value;
        if (actionMode != null) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) actionMode.getLayoutParams();
            layoutParams.bottomMargin = extraHeight;
            actionMode.setLayoutParams(layoutParams);
        }
    }

    public void closeSearchField() {
        closeSearchField(true);
    }

    public void closeSearchField(boolean closeKeyboard) {
        if (!isSearchFieldVisible || menu == null) {
            return;
        }
        menu.closeSearchField(closeKeyboard);
    }

    public void openSearchField(String text, boolean animated) {
        if (menu == null || text == null) {
            return;
        }
        menu.openSearchField(!isSearchFieldVisible, !isSearchFieldVisible, text, animated);
    }

    public void openSearchField(boolean animated) {
        if (menu == null) {
            return;
        }
        menu.openSearchField(!isSearchFieldVisible, false, "", animated);
    }

    public void setSearchFilter(FiltersView.MediaFilterData filter) {
        if (menu != null) {
            menu.setFilter(filter);
        }
    }

    public void clearSearchFilters() {
        if (menu != null) {
            menu.clearSearchFilters();
        }
    }

    public void setSearchFieldText(String text) {
        menu.setSearchFieldText(text);
    }

    public void onSearchPressed() {
        menu.onSearchPressed();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (backButtonImageView != null) {
            backButtonImageView.setEnabled(enabled);
        }
        if (menu != null) {
            menu.setEnabled(enabled);
        }
        if (actionMode != null) {
            actionMode.setEnabled(enabled);
        }
    }

    @Override
    public void requestLayout() {
        if (ignoreLayoutRequest) {
            return;
        }
        super.requestLayout();
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
    }

    private int additionalTextLeft;

    public void setAdditionalTextLeft(int x) {
        additionalTextLeft = x;
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int actionBarHeight = getCurrentActionBarHeight();
        int actionBarHeightSpec = MeasureSpec.makeMeasureSpec(actionBarHeight, MeasureSpec.EXACTLY);

        ignoreLayoutRequest = true;
        if (actionModeTop != null) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) actionModeTop.getLayoutParams();
            layoutParams.height = AndroidUtilities.statusBarHeight;
        }
        if (actionMode != null) {
            actionMode.setPadding(0, occupyStatusBar ? AndroidUtilities.statusBarHeight : 0, 0, 0);
        }
        ignoreLayoutRequest = false;

        setMeasuredDimension(width, actionBarHeight + (occupyStatusBar ? AndroidUtilities.statusBarHeight : 0) + extraHeight);

        int textLeft;
        if (backButtonImageView != null && backButtonImageView.getVisibility() != GONE) {
            backButtonImageView.measure(MeasureSpec.makeMeasureSpec(dp(54) + glassBackCounterTouchExtra, MeasureSpec.EXACTLY), actionBarHeightSpec);
            textLeft = dp(AndroidUtilities.isTablet() ? 80 : 72);
        } else {
            textLeft = dp(AndroidUtilities.isTablet() ? 26 : 18);
        }
        // textLeft += additionalTextLeft;

        if (menu != null && menu.getVisibility() != GONE) {
            int menuWidth;
            boolean searchFieldIsVisible = menu.searchFieldVisible();
            if (searchFieldIsVisible && !this.isSearchFieldVisible) {
                menuWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);
                menu.measure(menuWidth, actionBarHeightSpec);
                int itemsWidth = menu.getItemsMeasuredWidth(true);
                menuWidth = MeasureSpec.makeMeasureSpec(width - dp(menuOccupyBack ? 0 : AndroidUtilities.isTablet() ? 74 : 66) + menu.getItemsMeasuredWidth(true), MeasureSpec.EXACTLY);
                if (!isMenuOffsetSuppressed) {
                    menu.translateXItems(-itemsWidth);
                }
            } else if (isSearchFieldVisible) {
                menuWidth = MeasureSpec.makeMeasureSpec(width - dp(menuOccupyBack ? 0 : AndroidUtilities.isTablet() ? 74 : 66), MeasureSpec.EXACTLY);
                if (!isMenuOffsetSuppressed) {
                    menu.translateXItems(0);
                }
            } else {
                menuWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);
                if (!isMenuOffsetSuppressed) {
                    menu.translateXItems(0);
                }
            }
            menu.measure(menuWidth, actionBarHeightSpec);

        }

        for (int i = 0; i < 2; i++) {
            if (titleTextView[0] != null && titleTextView[0].getVisibility() != GONE || subtitleTextView != null && subtitleTextView.getVisibility() != GONE) {
                // A closing search field keeps filling the menu during its fade-out; don't squeeze the title for it.
                int titleMenuWidth = menu != null ? menu.getMeasuredWidth() - (isSearchFieldVisible ? 0 : menu.getClosingSearchFieldWidth()) : 0;
                int availableWidth = width - titleMenuWidth - dp(16) - textLeft - titleRightMargin;
                // Only a status in the title's place: the title itself measured short was cut ("glassGra...")
                if (glassTitleMaxWidth > 0 && titleOverlayShown) {
                    availableWidth = Math.min(availableWidth, glassTitleMaxWidth);
                }
                availableWidth = Math.max(availableWidth, 0);

                if (((fromBottom && i == 0) || (!fromBottom && i == 1)) && overlayTitleAnimation && titleAnimationRunning) {
                    titleTextView[i].setTextSize(glassMode ? glassTitleSize : !AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 18 : 20);
                } else {
                    if (titleTextView[0] != null && titleTextView[0].getVisibility() != GONE && subtitleTextView != null && subtitleTextView.getVisibility() != GONE) {
                        if (titleTextView[i] != null) {
                            titleTextView[i].setTextSize(glassMode ? glassTitleSize : AndroidUtilities.isTablet() ? 20 : 18);
                        }
                        subtitleTextView.setTextSize(AndroidUtilities.isTablet() ? 16 : 14);
                        if (additionalSubtitleTextView != null) {
                            additionalSubtitleTextView.setTextSize(AndroidUtilities.isTablet() ? 16 : 14);
                        }
                    } else {
                        if (titleTextView[i] != null && titleTextView[i].getVisibility() != GONE) {
                            titleTextView[i].setTextSize(glassMode ? glassTitleSize : !AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 18 : 20);
                        }
                        if (subtitleTextView != null && subtitleTextView.getVisibility() != GONE) {
                            subtitleTextView.setTextSize(!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 14 : 16);
                        }
                        if (additionalSubtitleTextView != null) {
                            additionalSubtitleTextView.setTextSize(!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 14 : 16);
                        }
                    }
                }

                if (titleTextView[i] != null && titleTextView[i].getVisibility() != GONE) {
                    titleTextView[i].measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(24) + titleTextView[i].getPaddingTop() + titleTextView[i].getPaddingBottom(), MeasureSpec.AT_MOST));
                    if (centerScale) {
                        CharSequence text = titleTextView[i].getText();
                        titleTextView[i].setPivotX(titleTextView[i].getTextPaint().measureText(text, 0, text.length()) / 2f);
                        titleTextView[i].setPivotY((dp(24) >> 1));
                    } else {
                        titleTextView[i].setPivotX(0);
                        titleTextView[i].setPivotY(0);
                    }
                }
                if (subtitleTextView != null && subtitleTextView.getVisibility() != GONE) {
                    subtitleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(20), MeasureSpec.AT_MOST));
                }
                if (additionalSubTitleOverlayContainer != null) {
                    additionalSubTitleOverlayContainer.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
                }
                if (additionalSubtitleTextView != null && additionalSubtitleTextView.getVisibility() != GONE) {
                    additionalSubtitleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(20), MeasureSpec.AT_MOST));
                }
            }
        }

        if (avatarSearchImageView != null) {
            avatarSearchImageView.measure(
                MeasureSpec.makeMeasureSpec(dp(42), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(42), MeasureSpec.EXACTLY)
            );
        }

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE || child == titleTextView[0] || child == titleTextView[1] || child == additionalSubTitleOverlayContainer || child == subtitleTextView || child == menu || child == backButtonImageView || child == additionalSubtitleTextView || child == avatarSearchImageView) {
                continue;
            }
            measureChildWithMargins(child, widthMeasureSpec, 0, MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY), 0);
        }
    }

    public void setMenuOffsetSuppressed(boolean menuOffsetSuppressed) {
        isMenuOffsetSuppressed = menuOffsetSuppressed;
    }

    int prevWidth;

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int additionalTop = occupyStatusBar ? AndroidUtilities.statusBarHeight : 0;
        if (prevWidth != getMeasuredWidth()) {
            prevWidth = getMeasuredWidth();
            checkAvatarContainerWidth(animatorAvatarContainerWidth.isAnimating());
        }

        int textLeft;
        if (backButtonImageView != null && backButtonImageView.getVisibility() != GONE) {
            final int backShift = glassButtonsIosShift(backButtonImageView.getMeasuredWidth() / 2);
            backButtonImageView.layout(backShift, additionalTop, backShift + backButtonImageView.getMeasuredWidth(), additionalTop + backButtonImageView.getMeasuredHeight());
            textLeft = glassMode ? dp(76) : dp(AndroidUtilities.isTablet() ? 80 : 72);
        } else {
            textLeft = glassMode ? dp(24) : dp(AndroidUtilities.isTablet() ? 26 : 18);
        }
        textLeft += additionalTextLeft;

        if (menu != null && menu.getVisibility() != GONE) {
            int menuLeft = menu.searchFieldVisible() ? dp(menuOccupyBack ? 0 : AndroidUtilities.isTablet() ? 74 : 66) : (getMeasuredWidth()) - menu.getMeasuredWidth() - glassButtonsIosShift(dp(24)) - glassMenuEndInset
                - (glassMode && glassIosSizes ? iosHeaderSize(IOS_MARGIN) - dp(6) : 0);
            menu.layout(menuLeft, additionalTop, menuLeft + menu.getMeasuredWidth(), additionalTop + menu.getMeasuredHeight());
        }

        for (int i = 0; i < 2; i++) {
            if (titleTextView[i] != null && titleTextView[i].getVisibility() != GONE) {
                int textTop;
                if (((fromBottom && i == 0) || (!fromBottom && i == 1)) && overlayTitleAnimation && titleAnimationRunning) {
                    textTop = (getCurrentActionBarHeight() - titleTextView[i].getTextHeight()) / 2;
                } else {
                    if ((subtitleTextView != null && subtitleTextView.getVisibility() != GONE)) {
                        textTop = (getCurrentActionBarHeight() / 2 - titleTextView[i].getTextHeight()) / 2 + dp(2) + dp(!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 2 : 3);
                    } else {
                        textTop = (getCurrentActionBarHeight() - titleTextView[i].getTextHeight()) / 2;
                    }
                }
                titleTextView[i].layout(textLeft, additionalTop + textTop - titleTextView[i].getPaddingTop(), textLeft + titleTextView[i].getMeasuredWidth(), additionalTop + textTop + titleTextView[i].getTextHeight() - titleTextView[i].getPaddingTop() + titleTextView[i].getPaddingBottom());
            }
        }
        if (additionalSubTitleOverlayContainer != null) {
            int textTop = getCurrentActionBarHeight() / 2 + (getCurrentActionBarHeight() / 2 - additionalSubTitleOverlayContainer.getMeasuredHeight()) / 2 - dp(2);
            additionalSubTitleOverlayContainer.layout(textLeft, additionalTop + textTop, textLeft + additionalSubTitleOverlayContainer.getMeasuredWidth(), additionalTop + textTop + additionalSubTitleOverlayContainer.getMeasuredHeight());
        }
        if (subtitleTextView != null && subtitleTextView.getVisibility() != GONE) {
            int textTop = getCurrentActionBarHeight() / 2 + (getCurrentActionBarHeight() / 2 - subtitleTextView.getTextHeight()) / 2 - dp(2);
            subtitleTextView.layout(textLeft, additionalTop + textTop, textLeft + subtitleTextView.getMeasuredWidth(), additionalTop + textTop + subtitleTextView.getTextHeight());
        }

        if (additionalSubtitleTextView != null && additionalSubtitleTextView.getVisibility() != GONE) {
            int textTop = getCurrentActionBarHeight() / 2 + (getCurrentActionBarHeight() / 2 - additionalSubtitleTextView.getTextHeight()) / 2 - dp(!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 1 : 1);
            additionalSubtitleTextView.layout(textLeft, additionalTop + textTop, textLeft + additionalSubtitleTextView.getMeasuredWidth(), additionalTop + textTop + additionalSubtitleTextView.getTextHeight());
        }

        if (avatarSearchImageView != null) {
            avatarSearchImageView.layout(
                dp(56 + 8),
                additionalTop + (getCurrentActionBarHeight() - avatarSearchImageView.getMeasuredHeight()) / 2,
                dp(56 + 8) + avatarSearchImageView.getMeasuredWidth(),
                additionalTop + (getCurrentActionBarHeight() + avatarSearchImageView.getMeasuredHeight()) / 2
            );
        }

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE || child == titleTextView[0] || child == titleTextView[1] || child == additionalSubTitleOverlayContainer || child == subtitleTextView || child == menu || child == backButtonImageView || child == additionalSubtitleTextView || child == avatarSearchImageView) {
                continue;
            }

            LayoutParams lp = (LayoutParams) child.getLayoutParams();

            int width = child.getMeasuredWidth();
            int height = child.getMeasuredHeight();
            int childLeft;
            int childTop;

            int gravity = lp.gravity;
            if (gravity == -1) {
                gravity = Gravity.TOP | Gravity.LEFT;
            }

            final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
            final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

            switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                case Gravity.CENTER_HORIZONTAL:
                    childLeft = (getMeasuredWidth() - width) / 2 + lp.leftMargin - lp.rightMargin;
                    break;
                case Gravity.RIGHT:
                    childLeft = getMeasuredWidth() - width - lp.rightMargin;
                    break;
                case Gravity.LEFT:
                default:
                    childLeft = lp.leftMargin;
            }

            switch (verticalGravity) {
                case Gravity.CENTER_VERTICAL:
                    childTop = (bottom - top - height) / 2 + lp.topMargin - lp.bottomMargin;
                    break;
                case Gravity.BOTTOM:
                    childTop = (bottom - top) - height - lp.bottomMargin;
                    break;
                default:
                    childTop = lp.topMargin;
            }
            child.layout(childLeft, childTop, childLeft + width, childTop + height);
        }
    }

    public void onMenuButtonPressed() {
        if (isActionModeShowed() || menu != null && menu.getVisibility() != VISIBLE) {
            return;
        }
        if (menu != null) {
            menu.onMenuButtonPressed();
        }
    }

    public void onResume() {
        resumed = true;
        updateAttachState();
    }

    protected void onPause() {
        resumed = false;
        updateAttachState();
        if (menu != null) {
            menu.hideAllPopupMenus();
        }
    }

    public void setAllowOverlayTitle(boolean value) {
        allowOverlayTitle = value;
    }

    public void setTitleActionRunnable(Runnable action) {
        lastRunnable = titleActionRunnable = action;
    }

    boolean overlayTitleAnimationInProgress;

    public void setTitleOverlayText(String title, int titleId, Runnable action) {
        if (!allowOverlayTitle || parentFragment.parentLayout == null) {
            return;
        }
        overlayTitleToSet[0] = title;
        overlayTitleToSet[1] = titleId;
        overlayTitleToSet[2] = action;
        if (overlayTitleAnimationInProgress) {
            return;
        }
        if (lastOverlayTitle == null && title == null || (lastOverlayTitle != null && lastOverlayTitle.equals(title))) {
            return;
        }
        lastOverlayTitle = title;

        if (additionalSubTitleOverlayContainer != null) {
            final CharSequence textToSet;
            if (titleId == R.string.ConnectingToProxyWithDots && !overlaySubtitleHidden) {
                textToSet = AndroidUtilities.replaceArrows(getString(R.string.TitleSetupProxy), true, dp(8f / 3f), dp(2));
            } else {
                textToSet = null;
            }
            additionalSubTitleOverlayContainer.setText(textToSet, true);
        }


        CharSequence textToSet = title != null ? LocaleController.getString(title, titleId) : lastTitle;
        Drawable rightDrawableToSet = title != null ? null : lastRightDrawable;
        boolean ellipsize = false;
        if (title != null) {
            int index = TextUtils.indexOf(textToSet, "...");
            if (index >= 0) {
                SpannableString spannableString = SpannableString.valueOf(textToSet);
                ellipsizeSpanAnimator.wrap(spannableString, index);
                textToSet = spannableString;
                ellipsize = true;
            }
        }
        titleOverlayShown = title != null;
        if ((textToSet != null && titleTextView[0] == null) || getMeasuredWidth() == 0 || (titleTextView[0] != null && titleTextView[0].getVisibility() != View.VISIBLE)) {
            createTitleTextView(0);
            if (supportsHolidayImage) {
                titleTextView[0].invalidate();
                invalidate();
            }
            titleTextView[0].setText(textToSet);
            titleTextView[0].setDrawablePadding(dp(4));
            titleTextView[0].setRightDrawable(rightDrawableToSet);
            titleTextView[0].setRightDrawableOnClick(rightDrawableOnClickListener);
            if (rightDrawableToSet instanceof AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) rightDrawableToSet).setParentView(titleTextView[0]);
            }
            if (ellipsize) {
                ellipsizeSpanAnimator.addView(titleTextView[0]);
            } else {
                ellipsizeSpanAnimator.removeView(titleTextView[0]);
            }
        } else if (titleTextView[0] != null) {
            titleTextView[0].animate().cancel();
            if (titleTextView[1] != null) {
                titleTextView[1].animate().cancel();
            }
            if (titleTextView[1] == null) {
                createTitleTextView(1);
            }
            titleTextView[1].setText(textToSet);
            titleTextView[1].setDrawablePadding(dp(4));
            titleTextView[1].setRightDrawable(rightDrawableToSet);
            titleTextView[1].setRightDrawableOnClick(rightDrawableOnClickListener);
            if (rightDrawableToSet instanceof AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) rightDrawableToSet).setParentView(titleTextView[1]);
            }
            if (ellipsize) {
                ellipsizeSpanAnimator.addView(titleTextView[1]);
            }
            overlayTitleAnimationInProgress = true;
            SimpleTextView tmp = titleTextView[1];
            titleTextView[1] = titleTextView[0];
            titleTextView[0] = tmp;
            titleTextView[0].setAlpha(0);
            titleTextView[0].setTranslationY(-dp(20));
            titleTextView[0].animate()
                    .alpha(adaptiveBackgroundHideTitle ? 1.0f - onTopAnimated : 1f)
                    .translationY(0)
                    .setDuration(220).start();
            ViewPropertyAnimator animator = titleTextView[1].animate()
                    .alpha(0);
            if (subtitleTextView == null) {
                animator.translationY(dp(20));
            } else {
                animator.scaleY(0.7f).scaleX(0.7f);
            }
            requestLayout();
            centerScale = true;
            animator.setDuration(220).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (titleTextView[1] != null && titleTextView[1].getParent() != null) {
                        ViewGroup viewGroup = (ViewGroup) titleTextView[1].getParent();
                        viewGroup.removeView(titleTextView[1]);
                    }
                    ellipsizeSpanAnimator.removeView(titleTextView[1]);
                    titleTextView[1] = null;
                    overlayTitleAnimationInProgress = false;
                    setTitleOverlayText((String) overlayTitleToSet[0], (int) overlayTitleToSet[1], (Runnable) overlayTitleToSet[2]);
                }
            }).start();
        }
        titleActionRunnable = action != null ? action : lastRunnable;
    }

    public boolean isSearchFieldVisible() {
        return isSearchFieldVisible;
    }

    public void setOccupyStatusBar(boolean value) {
        occupyStatusBar = value;
        if (actionMode != null) {
            actionMode.setPadding(0, occupyStatusBar ? AndroidUtilities.statusBarHeight : 0, 0, 0);
        }
    }

    public boolean getOccupyStatusBar() {
        return occupyStatusBar;
    }

    public void setItemsBackgroundColor(int color, boolean isActionMode) {
        if (isActionMode) {
            itemsActionModeBackgroundColor = color;
            if (actionModeVisible) {
                if (backButtonImageView != null) {
                    backButtonImageView.setBackgroundDrawable(Theme.createSelectorDrawable(itemsActionModeBackgroundColor));
                }
            }
            if (actionMode != null) {
                actionMode.updateItemsBackgroundColor();
            }
        } else {
            itemsBackgroundColor = color;
            if (backButtonImageView != null) {
                backButtonImageView.setBackgroundDrawable(Theme.createSelectorDrawable(itemsBackgroundColor));
            }
            if (menu != null) {
                menu.updateItemsBackgroundColor();
            }
        }
    }

    public void setItemsColor(int color, boolean isActionMode) {
        if (isActionMode) {
            itemsActionModeColor = color;
            if (actionMode != null) {
                actionMode.updateItemsColor();
            }
            if (backButtonImageView != null) {
                Drawable drawable = backButtonImageView.getDrawable();
                if (drawable instanceof BackDrawable) {
                    ((BackDrawable) drawable).setRotatedColor(color);
                } else if (drawable instanceof BitmapDrawable || drawable instanceof VectorDrawable) {
                    backButtonImageView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                }
            }
        } else {
            itemsColor = color;
            if (glassButtons) {
                // The glass capsules follow the icons' color
                invalidate();
            }
            if (backButtonImageView != null) {
                if (itemsColor != 0) {
                    Drawable drawable = backButtonImageView.getDrawable();
                    if (drawable instanceof BackDrawable) {
                        ((BackDrawable) drawable).setColor(color);
                    } else if (drawable instanceof MenuDrawable) {
                        ((MenuDrawable) drawable).setIconColor(color);
                    } else if (drawable instanceof BitmapDrawable || drawable instanceof VectorDrawable) {
                        backButtonImageView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                    }
                }
            }
            if (menu != null) {
                menu.updateItemsColor();
            }
        }

        if (backButtonImageView != null && mAlwaysApplyColorFilterToBackButton) {
            backButtonImageView.setColorFilter(new PorterDuffColorFilter(itemsColor, PorterDuff.Mode.SRC_IN));
        }
    }

    public void setCastShadows(boolean value) {
        if (castShadows != value && getParent() instanceof View) {
            ((View) getParent()).invalidate();
            invalidate();
        }
        castShadows = value;
    }

    public void setShadowAlpha(int alpha) {
        if (this.shadowAlpha == alpha) return;
        if (getParent() instanceof View) {
            ((View) getParent()).invalidate();
            invalidate();
        }
        this.shadowAlpha = alpha;
    }

    public int getShadowAlpha() {
        return shadowAlpha;
    }

    public boolean getCastShadows() {
        return castShadows;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (chatAvatarContainer != null && glassMode) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                final int x = (int) ev.getX();
                final int y = (int) ev.getY();
                View child = findChildUnder(this, x, y, chatAvatarContainer);
                if (child == null) {
                    child = findChildUnder(this, x, y, null);
                }

                boolean contains = false;
                contains |= glassDrawable != null && glassDrawable.getBounds().contains(x, y);
                if (child != null && child != chatAvatarContainer) {
                    contains |= glassDrawableBack != null && glassDrawableBack.getBounds().contains(x, y);
                    contains |= glassDrawableMenu != null && glassDrawableMenu.getBounds().contains(x, y);
                }

                if (!contains) {
                    return false;
                }
            }
        }

        final boolean handled = super.dispatchTouchEvent(ev);
        glassTouch(ev, handled);
        return handled;
    }

    public static View findChildUnder(ViewGroup parent, float x, float y, View exclude) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);

            if (child.getVisibility() != View.VISIBLE || child == exclude) continue;

            if (x >= child.getX() && x <= (child.getX() + child.getWidth())
                    && y >= child.getTop() && y <= child.getBottom()) {
                return child;
            }
        }
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (forceSkipTouches) {
            return false;
        }
        return super.onTouchEvent(event) || interceptTouches;
    }

    public static int getCurrentActionBarHeight() {
        if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
            return dp(48);
        } else {
            return dp(56);
        }
    }

    public void setTitleAnimated(CharSequence title, boolean fromBottom, long duration) {
        setTitleAnimated(title, fromBottom, duration, null);
    }

    public void setTitleAnimated(CharSequence title, boolean fromBottom, long duration, Interpolator interpolator) {
        if (titleTextView[0] == null || title == null) {
            setTitle(title);
            return;
        }
        boolean crossfade = overlayTitleAnimation && !TextUtils.isEmpty(subtitle);
        if (crossfade) {
            if (subtitleTextView.getVisibility() != View.VISIBLE) {
                subtitleTextView.setVisibility(View.VISIBLE);
                subtitleTextView.setAlpha(0);
            }
            subtitleTextView.animate().alpha(fromBottom ? 0 : 1f).setDuration(220).start();
        }
        if (titleTextView[1] != null) {
            if (titleTextView[1].getParent() != null) {
                ViewGroup viewGroup = (ViewGroup) titleTextView[1].getParent();
                viewGroup.removeView(titleTextView[1]);
            }
            titleTextView[1] = null;
        }
        titleTextView[1] = titleTextView[0];
        titleTextView[0] = null;
        setTitle(title);
        this.fromBottom = fromBottom;
        titleTextView[0].setAlpha(0);
        if (!crossfade) {
            titleTextView[0].setTranslationY(fromBottom ? dp(20) : -dp(20));
        }
        ViewPropertyAnimator a1 = titleTextView[0].animate().alpha(1f).translationY(0).setDuration(duration);
        if (interpolator != null) {
            a1.setInterpolator(interpolator);
        }
        a1.start();

        titleAnimationRunning = true;
        ViewPropertyAnimator a = titleTextView[1].animate().alpha(0);
        if (!crossfade) {
            a.translationY(fromBottom ? -dp(20) : dp(20));
        }
        if (interpolator != null) {
            a.setInterpolator(interpolator);
        }
        a.setDuration(duration).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (titleTextView[1] != null && titleTextView[1].getParent() != null) {
                    ViewGroup viewGroup = (ViewGroup) titleTextView[1].getParent();
                    viewGroup.removeView(titleTextView[1]);
                }
                titleTextView[1] = null;
                titleAnimationRunning = false;

                if (crossfade && fromBottom) {
                    subtitleTextView.setVisibility(View.GONE);
                }

                requestLayout();
            }
        }).start();
        requestLayout();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        updateAttachState();
        if (actionModeVisible) {
            final int color = actionModeColor == 0 ? actionBarColor : actionModeColor;
            if (color == 0 || glassMode) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors);
            } else if (ColorUtils.calculateLuminance(color) < 0.7f) {
                AndroidUtilities.setLightStatusBar((Activity) getContext(), false);
            } else {
                AndroidUtilities.setLightStatusBar((Activity) getContext(), true);
            }
        }
        if (lastRightDrawable instanceof AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) {
            ((AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) lastRightDrawable).setParentView(titleTextView[0]);
        }
        watchGlassButtonBackdrop(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        updateAttachState();
        if (actionModeVisible) {
            if (actionBarColor == 0 || actionModeColor == 0 || glassMode) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors);
            } else {
                if (ColorUtils.calculateLuminance(actionBarColor) < 0.7f) {
                    AndroidUtilities.setLightStatusBar((Activity) getContext(), false);
                } else {
                    AndroidUtilities.setLightStatusBar((Activity) getContext(), true);
                }
            }
        }
        if (lastRightDrawable instanceof AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) {
            ((AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) lastRightDrawable).setParentView(null);
        }
        watchGlassButtonBackdrop(false);
    }

    private void updateAttachState() {
        boolean attachState = attached && resumed;
        if (this.attachState != attachState) {
            this.attachState = attachState;
            if (attachState) {
                ellipsizeSpanAnimator.onAttachedToWindow();
            } else {
                ellipsizeSpanAnimator.onDetachedFromWindow();
            }
        }
    }

    public ActionBarMenu getActionMode() {
        return actionMode;
    }

    public void setOverlayTitleAnimation(boolean ovelayTitleAnimation) {
        this.overlayTitleAnimation = ovelayTitleAnimation;
    }

    public void beginDelayedTransition() {
        if (!LocaleController.isRTL) {
            TransitionSet transitionSet = new TransitionSet();
            transitionSet.setOrdering(TransitionSet.ORDERING_TOGETHER);
            transitionSet.addTransition(new Fade());
            transitionSet.addTransition(new ChangeBounds() {


                public void captureStartValues(TransitionValues transitionValues) {
                    super.captureStartValues(transitionValues);
                    if (transitionValues.view instanceof SimpleTextView) {
                        float textSize = ((SimpleTextView) transitionValues.view).getTextPaint().getTextSize();
                        transitionValues.values.put("text_size", textSize);
                    }
                }

                public void captureEndValues(TransitionValues transitionValues) {
                    super.captureEndValues(transitionValues);
                    if (transitionValues.view instanceof SimpleTextView) {
                        float textSize= ((SimpleTextView) transitionValues.view).getTextPaint().getTextSize();
                        transitionValues.values.put("text_size", textSize);
                    }
                }

                @Override
                public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues, TransitionValues endValues) {
                    if (startValues != null && startValues.view instanceof SimpleTextView) {
                        AnimatorSet animatorSet = new AnimatorSet();
                        if (startValues != null && endValues != null) {
                            Animator animator = super.createAnimator(sceneRoot, startValues, endValues);
                            float s = (float) startValues.values.get("text_size") / (float) endValues.values.get("text_size");
                            startValues.view.setScaleX(s);
                            startValues.view.setScaleY(s);
                            if (animator != null) {
                                animatorSet.playTogether(animator);
                            }
                        }
                        animatorSet.playTogether(ObjectAnimator.ofFloat(startValues.view, SCALE_X, 1f));
                        animatorSet.playTogether(ObjectAnimator.ofFloat(startValues.view, SCALE_Y, 1f));
                        animatorSet.addListener(new AnimatorListenerAdapter() {

                            @Override
                            public void onAnimationStart(Animator animation) {
                                super.onAnimationStart(animation);
                                startValues.view.setLayerType(LAYER_TYPE_HARDWARE, null);
                            }

                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                startValues.view.setLayerType(LAYER_TYPE_NONE, null);
                            }
                        });
                        return animatorSet;
                    } else {
                        return super.createAnimator(sceneRoot, startValues, endValues);
                    }
                }
            });
            centerScale = false;
            transitionSet.setDuration(220);
            transitionSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
            TransitionManager.beginDelayedTransition(this, transitionSet);
        }
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public void setDrawBlurBackground(SizeNotifierFrameLayout contentView) {
        blurredBackground = true;
        this.contentView = contentView;
        contentView.blurBehindViews.add(this);
        setBackground(null);
    }

    private boolean doNotDrawChild;

    public void setSkipDrawChild(boolean skip) {
        if (!skip) {
            skipDrawChildBackAlpha = 0f;
        }
        if (doNotDrawChild != skip) {
            doNotDrawChild = skip;
            invalidate();
        }
    }

    // glassGram: the back button stays while the children are skipped, at this alpha: the profile opening
    // from the chat's header fades its own back button in over it, and out over it as it closes
    private float skipDrawChildBackAlpha;

    public void setSkipDrawChildBackAlpha(float alpha) {
        alpha = Math.max(0f, Math.min(1f, alpha));
        if (skipDrawChildBackAlpha != alpha) {
            skipDrawChildBackAlpha = alpha;
            invalidate();
        }
    }

    private float searchFactor;

    public void setSearchFactor(float factor) {
        if (searchFactor != factor) {
            searchFactor = factor;
            invalidate();
        }
    }

    /**
     * The widest the chat's title pill gets: the bar between the back button and the end button,
     * less what the back button's glass widens for the unread badge. A long name was measured for
     * the whole bar and ran out of the pill, badges and all, when the badge took part of it.
     */
    public int getAvatarContainerMaxWidth() {
        final boolean hasBackButton = backButtonImageView != null && backButtonImageView.getVisibility() == View.VISIBLE;
        final int backExtra = hasBackButton ? glassBackCounterTouchExtra : 0;
        if (isIosCenteredPill()) {
            // All the room between the back button with its badge and the avatar, less the gaps from
            // them: a long name moves off the middle as on iOS; centered on the bar, the side with a
            // wide badge ("363") left room unused on the other
            return getMeasuredWidth() - 2 * (iosHeaderSize(IOS_MARGIN) + iosHeaderSize(IOS_BUTTON) + iosHeaderSize(IOS_GAP)) - backExtra;
        }
        return getMeasuredWidth() - dp(6 + 46 + 6 + 6 + 46 + 6) - backExtra;
    }

    /*
     * glassGram: in the iOS-like chat header (the avatar at the end) the title pill stays in the middle
     * of the bar, as on iOS, and a long name leaves gaps to the back button and the avatar; between them
     * it ran from one to the other.
     */
    private boolean isIosCenteredPill() {
        return glassMode && glassEndView != null && chatAvatarContainer != null;
    }

    /*
     * The iOS-like chat header has iOS's sizes, measured on an iPhone 390pt wide: 44pt for the buttons
     * and the pill, 16pt from the screen's edges, 13pt between them. They take the same part of the
     * bar's width here, and no more than as many dp: the bar's 46dp circles 6dp from the edges took
     * more of a narrower screen, and the name looked large beside the gaps. The name and the status
     * shrink with the pill (getIosHeaderScale).
     */
    private static final float IOS_WIDTH = 390, IOS_BUTTON = 44, IOS_MARGIN = 16, IOS_GAP = 13;

    private int iosHeaderSize(float pt) {
        final int width = getMeasuredWidth() > 0 ? getMeasuredWidth() : AndroidUtilities.displaySize.x;
        return Math.round(AndroidUtilities.dpf2(pt) * Math.min(1f, width / AndroidUtilities.dpf2(IOS_WIDTH)));
    }

    /** How much smaller than the bar's own (46dp) the iOS-like chat header's pill is, for the text in it. */
    public float getIosHeaderScale() {
        return iosHeaderSize(IOS_BUTTON) / (float) dp(46);
    }

    /** The iOS-like chat header's distance from the screen's edges, for the panels under it to line up; 0 without it. */
    public int getIosHeaderMargin() {
        return isIosCenteredPill() ? iosHeaderSize(IOS_MARGIN) : 0;
    }

    public void checkAvatarContainerWidth(boolean animated) {
        if (chatAvatarContainer == null) {
            return;
        }

        final boolean hasAvatar = chatAvatarContainer.hasVisibleAvatar();
        int visualWidth = chatAvatarContainer.getVisualWidth();
        if (hasAvatar) {
            //visualWidth = Math.max(visualWidth, dp(168));
        }

        final int width = Math.min(getAvatarContainerMaxWidth(), visualWidth);
        if (animated) {
            if (animatorAvatarContainerWidth.getToFactor() != width) {
                animatorAvatarContainerWidth.animateTo(width);
            }
        } else {
            animatorAvatarContainerWidth.forceFactor(width);
        }
        animatorAvatarContainerHasAvatar.setValue(hasAvatar, animated);
    }

    private final FactorAnimator animatorAvatarContainerWidth = new FactorAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, 380);
    private final BoolAnimator animatorAvatarContainerHasAvatar = new BoolAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, 380);

    private final FactorAnimator animatorMenuItemsWidth = new FactorAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320);
    private final BoolAnimator animatorHasMenuItems = new BoolAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320);

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        invalidate();
    }

    private int forcedMenuWidth;
    private int forcedMenuMinWidth;
    private boolean hasForcedMenuWidth;
    private boolean hasForcedMenuMinWidth;

    public void setForcedMenuWidth(int width) {
        hasForcedMenuWidth = true;
        if (forcedMenuWidth != width) {
            forcedMenuWidth = width;
            invalidate();
        }
    }

    public void setForcedMenuMinWidth(int width) {
        hasForcedMenuMinWidth = true;
        if (forcedMenuMinWidth != width) {
            forcedMenuMinWidth = width;
            invalidate();
        }
    }

    private boolean isAnimationsAllowed;

    public void checkMenuItemsWidth() {
        final int defaultMenuWidth = Math.max(0, menu != null ? (int) menu.getItemsWidth() - dp(1) - dp(1) : 0);
        final int actionMenuWidth = Math.max(0, actionMode != null ? actionMode.getItemsWidth() - dp(1) - dp(1) : 0);
        final int searchMenuWidth = dp(46);
        final int width = /*isSearchFieldVisible ? searchMenuWidth :*/ (actionModeVisible ? actionMenuWidth : defaultMenuWidth);

        animatorHasMenuItems.setValue(width > 0, isAnimationsAllowed);
        if (animatorMenuItemsWidth.getToFactor() != width) {
            if (isAnimationsAllowed) {
                animatorMenuItemsWidth.animateTo(width);
            } else {
                animatorMenuItemsWidth.forceFactor(width);
            }
        }
    }

    public boolean doNotDrawGlassMenu;
    private boolean drawGlassTitle = true;

    public void setDrawGlassTitle(boolean draw) {
        if (drawGlassTitle != draw) {
            drawGlassTitle = draw;
            invalidate();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final int p = dp(6);
        final int s = dp(46);

        final float actionModeFactor = getActionModeFactor();
        final int menuWidthA = hasForcedMenuWidth ? forcedMenuWidth : (int) animatorMenuItemsWidth.getFactor();
        final int menuWidthB = hasForcedMenuMinWidth ? Math.max((int) (forcedMenuMinWidth * (1f - searchFactor)), menuWidthA) : menuWidthA;
        // With an end view the menu's glass is a circle under it, until search or selection need the menu
        final float endFactor = glassMode && glassEndView != null ? (1f - actionModeFactor) * (1f - searchFieldVisibleAlpha) : 0f;
        // The iOS-like chat header's sizes, back to the bar's own for search and selection
        final float iosFactor = isIosCenteredPill() ? Math.max(0f, Math.min(1f, endFactor))
            // and back for the topics opened beside the chats list: their back button sits where the bar's own would
            : glassIosSizes ? (1f - actionModeFactor) * (1f - searchFieldVisibleAlpha) * (1f - glassBackOverlay) : 0f;
        final int si = lerp(s, iosHeaderSize(IOS_BUTTON), iosFactor);
        final int mi = lerp(p, iosHeaderSize(IOS_MARGIN), iosFactor);
        final int menuWidth = lerp(menuWidthB, si, endFactor);
        if (glassEndView != null) {
            final float alpha = Math.max(0f, Math.min(1f, endFactor));
            if (glassEndView.getAlpha() != alpha) {
                glassEndView.setAlpha(alpha);
            }
            final int visibility = alpha > 0f ? VISIBLE : INVISIBLE;
            if (glassEndView.getVisibility() != visibility) {
                glassEndView.setVisibility(visibility);
            }
        }

        final boolean hasBackButton = backButtonImageView != null && backButtonImageView.getVisibility() == View.VISIBLE;
        // The selection bar's own close button gets the back button's glass circle where the bar has none
        final float closeFactor = glassMode && !hasBackButton && glassActionModeCloseView != null
            && glassActionModeCloseView.getVisibility() == View.VISIBLE ? actionModeFactor : 0f;
        if (glassMode || glassButtons) {
            glassShown[GLASS_PILL] = glassShown[GLASS_BACK] = glassShown[GLASS_MENU] = false;
        }
        // The back glass widens for the unread badge, the chevron moving a little to the left
        final float counterProgress = glassMode && hasBackButton ? glassBackCounterProgress() : 0f;
        final int backExtra = (int) ((glassBackCounterWidth - dp(4)) * counterProgress);
        if (glassBackCounterFollower != null && glassBackCounterFollower != chatAvatarContainer
                && glassBackCounterFollower.getTranslationX() != backExtra) {
            glassBackCounterFollower.setTranslationX(backExtra);
        }
        if (glassMode && backButtonImageView != null) {
            // The arrow sits 2dp right of the circle's middle; the chevron, lighter to the eye on its
            // open side, a little left of it (with the badge it is left in the capsule anyway)
            final boolean chevron = backButtonDrawable instanceof BackDrawable && ((BackDrawable) backButtonDrawable).isChevron();
            final float tx = chevron
                ? AndroidUtilities.dpf2(-0.5f) - AndroidUtilities.dpf2(0.5f) * counterProgress
                : dp(2) - dp(3) * counterProgress;
            // In the middle of its circle wherever the circle is (the iOS-like chat header's is smaller, further in)
            final float shift = (mi + si / 2f) - (p + s / 2f);
            if (backButtonImageView.getTranslationX() != tx + shift) {
                backButtonImageView.setTranslationX(tx + shift);
            }
        }

        final int t = getHeight() - (getCurrentActionBarHeight() + si) / 2 - p;
        final int b = t + si + p * 2;

        if (glassDrawable != null && !glassOnlyBack && drawGlassTitle) {
            final int menuWidthWithPadding = menuWidth + ((hasForcedMenuWidth || hasForcedMenuMinWidth) ? (menuWidth > 0 ? mi : 0) : (int) (mi * animatorHasMenuItems.getFloatValue()));
            final int rightOffset = lerp(menuWidthWithPadding, Math.max(menuWidthWithPadding, mi + si), chatAvatarContainer == null ? 0f : 1f - animatorAvatarContainerHasAvatar.getFloatValue());

            final int leftDefault = lerp(hasBackButton ? si + mi + backExtra : (int) ((si + mi) * closeFactor), si + mi + backExtra, chatAvatarContainer == null? 0f : 1f - animatorAvatarContainerHasAvatar.getFloatValue());
            final int rightDefault = getWidth() - rightOffset;
            final int widthDefault = rightDefault - leftDefault;
            final int left, right;
            if (chatAvatarContainer != null) {
                final float openFactor = Math.max(searchFactor, actionModeFactor);
                final int width = lerp(Math.min(widthDefault, (int) animatorAvatarContainerWidth.getFactor() + p * 2), widthDefault, openFactor);
                final int between = (rightDefault + leftDefault - width) / 2;
                if (isIosCenteredPill() && openFactor < 1f) {
                    // In the middle of the bar while it fits there, else as near it as the gaps to the
                    // buttons beside it let it (the glass's own inset p is part of each gap)
                    final int gap = Math.max(0, iosHeaderSize(IOS_GAP) - p);
                    final int centered = Math.max(leftDefault + gap, Math.min((getWidth() - width) / 2, rightDefault - gap - width));
                    left = lerp(centered, between, openFactor);
                } else {
                    left = between;
                }
                right = left + width;

                final float translationX = left
                    - ((MarginLayoutParams)(chatAvatarContainer.getLayoutParams())).leftMargin
                    - chatAvatarContainer.getLeftPadding()
                    + p + dp(3);
                chatAvatarContainer.setTranslationX(translationX);
                chatAvatarContainer.setPivotX((chatAvatarContainer.getMeasuredWidth()) / 2f - translationX );
            } else if (glassPillContent != null) {
                // Wraps its content, and opens to the whole bar for search and selection
                final int wanted = (int) Math.ceil(glassPillContent.getContentRight()) + dp(14) - leftDefault;
                final int width = lerp(Math.min(widthDefault, Math.max(s + p * 2, wanted)), widthDefault, Math.max(searchFactor, actionModeFactor));
                left = leftDefault;
                right = left + width;
            } else {
                left = leftDefault;
                right = rightDefault;
            }

            final int pillOffset = glassPillContent != null ? (int) glassPillContent.getContentOffsetY() : 0;
            glassDrawable.setBounds(left, t + pillOffset, right, b + pillOffset);
            final float pillAlpha = glassPillAlpha * (glassPillOnlyForActionMode ? Math.max(actionModeFactor, searchFactor) : 1f);
            if (glassAlpha * pillAlpha > 0) {
                glassDrawable.setAlpha((int) (255 * glassAlpha * pillAlpha));
                drawGlass(canvas, glassDrawable, GLASS_PILL, p);
            }
        }
        // The back glass goes with its arrow: the chats list hides the arrow (alpha 0, still visible)
        // while topics are open beside it, and its glass stayed there as a smudge under the topics' bar
        final float backAlpha = Math.max(hasBackButton ? backButtonImageView.getAlpha() : closeFactor, glassBackOverlay);
        if (glassDrawableBack != null && backAlpha > 0 && glassAlpha > 0) {
            glassDrawableBack.setBounds(mi - p, t, mi + si + p + backExtra, b);
            glassDrawableBack.setAlpha((int) (255 * glassAlpha * backAlpha));
            drawGlass(canvas, glassDrawableBack, GLASS_BACK, p);
        }
        if (!(menuWidth > 0)) {
            glassMenuLeftDrawn = Float.NaN;
        }
        if (glassDrawableMenu != null && menuWidth > 0 && !glassOnlyBack && !doNotDrawGlassMenu && glassAlpha > 0) {
            // Moved in from the end for the screen's own button there, back for the selection bar's actions
            final int endInset = (int) (glassMenuEndInset * (1f - actionModeFactor));
            glassDrawableMenu.setBounds(getWidth() - Math.max(si, menuWidth) - p - mi - endInset, t, getWidth() - mi + p - endInset, b);
            glassMenuLeftDrawn = getWidth() - Math.max(si, menuWidth) - mi - endInset;
            glassDrawableMenu.setAlpha((int) (glassAlpha * (hasForcedMenuWidth ? 255 : 255 * Math.max(endFactor, animatorHasMenuItems.getFloatValue()))));
            drawGlass(canvas, glassDrawableMenu, GLASS_MENU, p);
            if (glassEndView != null) {
                // Exactly in the middle of the circle drawn: placing it by margins rounded differently
                // and left it a pixel or two off, with a thicker rim on one side
                final RectF r = glassRect[GLASS_MENU];
                final float tx = r.centerX() - (glassEndView.getLeft() + glassEndView.getWidth() / 2f);
                final float ty = r.centerY() - (glassEndView.getTop() + glassEndView.getHeight() / 2f);
                if (glassEndView.getTranslationX() != tx) {
                    glassEndView.setTranslationX(tx);
                }
                if (glassEndView.getTranslationY() != ty) {
                    glassEndView.setTranslationY(ty);
                }
                // A 2dp rim of glass around it in a circle of any size
                final float scale = (si - dp(4)) / (float) glassEndView.getWidth();
                if (glassEndView.getWidth() > 0 && glassEndView.getScaleX() != scale) {
                    glassEndView.setScaleX(scale);
                    glassEndView.setScaleY(scale);
                }
            }
        }

        if (glassMode) {
            drawGlassNecks(canvas);
        }

        if (blurredBackground && actionBarColor != Color.TRANSPARENT) {
            rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            blurScrimPaint.setColor(actionBarColor);
            if (adaptiveBackground) {
                contentView.drawBlurRect(canvas, getY(), rectTmp, blurScrimPaint, true, 1.0f - onTopAnimated);
            } else {
                contentView.drawBlurRect(canvas, getY(), rectTmp, blurScrimPaint, true);
            }
        }

        isAnimationsAllowed = true;
        if (doNotDrawChild) {
            if (skipDrawChildBackAlpha > 0f && backButtonImageView != null && backButtonImageView.getVisibility() == VISIBLE) {
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (255 * skipDrawChildBackAlpha));
                drawChild(canvas, backButtonImageView, getDrawingTime());
                canvas.restore();
            }
            return;
        }

        super.dispatchDraw(canvas);
        glassRestoreClip();
        if (glassMode && actionMode != null) {
            // The selection bar moves its items itself (drawChild), so its own drawing has to be redone
            // on every frame of a stretch, plus one more to put them back
            boolean moved = false;
            for (LiquidPressEffect effect : glassPress) {
                moved |= effect != null && effect.isActive();
            }
            if (moved || glassActionModeMoved) {
                actionMode.invalidate();
            }
            glassActionModeMoved = moved;
        }
        if (glassPillFollower != null) {
            final boolean moved = glassPress[GLASS_PILL] != null && glassPress[GLASS_PILL].isActive();
            if (moved || glassPillFollowerMoved) {
                glassPillFollower.invalidate();
            }
            glassPillFollowerMoved = moved;
        }
    }

    public void setForceSkipTouches(boolean forceSkipTouches) {
        this.forceSkipTouches = forceSkipTouches;
    }

    public void setDrawBackButton(boolean b) {
        this.drawBackButton = b;
        if (backButtonImageView != null) {
            backButtonImageView.invalidate();
        }
    }

    public void setUseContainerForTitles() {
        this.useContainerForTitles = true;
        if (titlesContainer == null) {
            titlesContainer = new FrameLayout(getContext()) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
                }

                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {

                }
            };
            addView(titlesContainer);
        }
    }

    public FrameLayout getTitlesContainer() {
        return titlesContainer;
    }

    @Override
    public void updateColors() {
        adaptive_updateColor();
        if (glassDrawable != null) {
            glassDrawable.updateColors();
        }
        if (glassDrawableMenu != null) {
            glassDrawableMenu.updateColors();
        }
        if (glassDrawableBack != null) {
            glassDrawableBack.updateColors();
        }
        if (additionalSubTitleOverlayContainer != null) {
            additionalSubTitleOverlayContainer.updateColors();
        }
    }

    private ActionBarAnimatedSubtitleOverlayContainer additionalSubTitleOverlayContainer;
    public FrameLayout createAdditionalSubTitleOverlayContainer() {
        if (additionalSubTitleOverlayContainer == null) {
            additionalSubTitleOverlayContainer = new ActionBarAnimatedSubtitleOverlayContainer(getContext(), resourcesProvider, ellipsizeSpanAnimator) {
                @Override
                public void onItemChanged(ReplaceAnimator<?> animator) {
                    super.onItemChanged(animator);
                    final float overlayVisibility = getTotalVisibility();
                    if (titlesContainer != null) {
                        titlesContainer.setTranslationY(overlayVisibility * dp(-11));
                    }
                }
            };
            additionalSubTitleOverlayContainer.setClipChildren(false);
            addView(additionalSubTitleOverlayContainer);
        }
        return additionalSubTitleOverlayContainer;
    }
    public FrameLayout getAdditionalSubTitleOverlayContainer() {
        return additionalSubTitleOverlayContainer;
    }

    private boolean adaptiveBackground, adaptiveBackgroundHideTitle;
    private int adaptive_topColorKey;
    private int adaptive_lowerColorKey;
    private boolean onTop = true;
    private float onTopAnimated = 1.0f;
    private ValueAnimator adaptive_animator;
    public void setAdaptiveBackground(RecyclerView list) {
        setAdaptiveBackground(list, false, Theme.key_windowBackgroundGray, Theme.key_actionBarDefault);
    }
    public void setAdaptiveBackground(RecyclerView list, boolean hideTitle) {
        setAdaptiveBackground(list, hideTitle, Theme.key_windowBackgroundGray, Theme.key_actionBarDefault);
    }
    public void setAdaptiveBackground(RecyclerView list, boolean hideTitle, final int topColorKey, final int lowerColorKey) {
        this.adaptive_topColorKey = topColorKey;
        this.adaptive_lowerColorKey = lowerColorKey;
        final Runnable checkScroll = () -> {
            final boolean onTop = !list.canScrollVertically(-1);
            if (ActionBar.this.onTop == onTop) return;
            if (adaptive_animator != null)
                adaptive_animator.cancel();
            adaptive_animator = ValueAnimator.ofFloat(onTopAnimated, (ActionBar.this.onTop = onTop) ? 1.0f : 0.0f);
            adaptive_animator.addUpdateListener(anm -> {
                onTopAnimated = (float) anm.getAnimatedValue();
                adaptive_updateColor();
            });
            adaptive_animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onTopAnimated = onTop ? 1.0f : 0.0f;
                    adaptive_updateColor();
                }
            });
            adaptive_animator.setDuration(320);
            adaptive_animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            adaptive_animator.start();
        };
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                checkScroll.run();
            }
        });
        this.adaptiveBackgroundHideTitle = hideTitle;
        if (this.adaptiveBackground) {
            checkScroll.run();
        } else {
            this.adaptiveBackground = true;
            this.onTopAnimated = (this.onTop = !list.canScrollVertically(-1)) ? 1 : 0;
            adaptive_updateColor();
        }
    }
    public void setAdaptiveBackground(SectionsScrollView list) {
        setAdaptiveBackground(list, Theme.key_windowBackgroundGray, Theme.key_actionBarDefault);
    }
    public void setAdaptiveBackground(SectionsScrollView list, final int topColorKey, final int lowerColorKey) {
        this.adaptive_topColorKey = topColorKey;
        this.adaptive_lowerColorKey = lowerColorKey;
        adaptive_updateColor();
        final Runnable checkScroll = () -> {
            final boolean onTop = !list.canScrollVertically(-1);
            if (ActionBar.this.onTop == onTop) return;
            if (adaptive_animator != null)
                adaptive_animator.cancel();
            adaptive_animator = ValueAnimator.ofFloat(onTopAnimated, (ActionBar.this.onTop = onTop) ? 1.0f : 0.0f);
            adaptive_animator.addUpdateListener(anm -> {
                onTopAnimated = (float) anm.getAnimatedValue();
                adaptive_updateColor();
            });
            adaptive_animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onTopAnimated = onTop ? 1.0f : 0.0f;
                    adaptive_updateColor();
                }
            });
            adaptive_animator.setDuration(320);
            adaptive_animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            adaptive_animator.start();
        };
        list.onScroll(checkScroll);
        if (this.adaptiveBackground) {
            checkScroll.run();
        } else {
            this.adaptiveBackground = true;
            this.onTopAnimated = (this.onTop = !list.canScrollVertically(-1)) ? 1 : 0;
            adaptive_updateColor();
        }
    }
    private void adaptive_updateColor() {
        if (!adaptiveBackground) return;
        if (adaptiveBackgroundHideTitle) {
            if (titlesContainer != null) {
                titlesContainer.setAlpha(1.0f - onTopAnimated);
            } else if (titleTextView[0] != null) {
                titleTextView[0].setAlpha(1.0f - onTopAnimated);
            }
        }

        final float factor = onTopAnimated;
        int lowerColor = adaptive_lowerColorKey == -1 ? 0 : Theme.getColor(adaptive_lowerColorKey, resourcesProvider);
        int topColor = adaptive_topColorKey == -1 ? 0 : Theme.getColor(adaptive_topColorKey, resourcesProvider);

        if (topColor == 0) {
            topColor = ColorUtils.setAlphaComponent(lowerColor, 0);
        }
        if (lowerColor == 0) {
            lowerColor = ColorUtils.setAlphaComponent(topColor, 0);
        }

        setBackgroundColor(ColorUtils.blendARGB(lowerColor, topColor, factor));
        setShadowAlpha((int) ((1.0f - onTopAnimated) * 0xFF));
        if (blurredBackground) {
            invalidate();
        }
    }
}
