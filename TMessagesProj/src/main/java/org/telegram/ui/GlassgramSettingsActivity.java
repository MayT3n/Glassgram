package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.GlassgramApi;
import org.telegram.messenger.GlassgramChatLock;
import org.telegram.messenger.GlassgramConfig;
import org.telegram.messenger.GlassgramFonts;
import org.telegram.messenger.GlassgramProxy;
import org.telegram.messenger.GlassgramRemoteConfig;
import org.telegram.messenger.GlassgramSpyStorage;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.GlassgramSettingsCells;
import org.telegram.ui.Components.GlassgramWallpaperLayout;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Settings → Glassgram: the fork's own options, in General, Appearance and Chats pages.
 */
public class GlassgramSettingsActivity extends UniversalFragment {

    public static final int PAGE_MAIN = 0;
    public static final int PAGE_GENERAL = 1;
    public static final int PAGE_APPEARANCE = 2;
    public static final int PAGE_CHATS = 3;
    public static final int PAGE_GHOST = 4;
    public static final int PAGE_SPY = 5;
    public static final int PAGE_CUSTOMIZATION = 6;
    public static final int PAGE_FILTERS = 7;
    public static final int PAGE_TABS = 8;
    public static final int PAGE_MESSAGE_MENU = 9;
    public static final int PAGE_CHAT_LOCK = 10;

    private static final int ID_GENERAL = 1;
    private static final int ID_APPEARANCE = 2;
    private static final int ID_CHATS = 3;
    private static final int ID_GHOST = 4;
    private static final int ID_SPY = 5;
    private static final int ID_CUSTOMIZATION = 6;
    private static final int ID_FILTERS = 7;
    private static final int ID_SOURCE_CODE = 8;
    private static final int ID_CHANNEL = 9;
    private static final int ID_PLUGINS = 90;

    private static final int ID_GHOST_MODE = 40;
    private static final int ID_GHOST_READ = 41;
    private static final int ID_GHOST_STORIES = 42;
    private static final int ID_GHOST_ONLINE = 43;
    private static final int ID_GHOST_TYPING = 44;
    private static final int ID_GHOST_OFFLINE = 45;
    private static final int ID_GHOST_READ_ON_INTERACT = 46;

    private static final int ID_SPY_DELETED = 50;
    private static final int ID_SPY_EDITS = 51;
    private static final int ID_SPY_BOTS = 52;
    private static final int ID_SPY_READ_DATE = 53;
    private static final int ID_SPY_LAST_SEEN = 54;
    private static final int ID_SPY_ATTACHMENTS = 55;
    private static final int ID_SPY_LIMIT = 56;
    private static final int ID_SPY_EXPORT = 57;
    private static final int ID_SPY_IMPORT = 58;
    private static final int ID_SPY_CLEAR = 59;

    private static final int ID_CUSTOM_TRANSLUCENT = 60;
    private static final int ID_CUSTOM_TRASH = 61;
    private static final int ID_CUSTOM_ADS = 62;
    private static final int ID_CUSTOM_GHOST_STATUS = 63;
    private static final int ID_CUSTOM_SELF_DESTRUCT = 64;
    private static final int ID_CONTENT_PROTECTION = 65;

    private static final int ID_FILTER_ENABLE = 70;
    private static final int ID_FILTER_SHARED = 71;
    private static final int ID_FILTER_BLOCKED = 72;
    private static final int ID_FILTER_SHADOW = 73;
    private static final int ID_FILTER_SHARED_LIST = 74;

    private static final int REQUEST_SPY_EXPORT = 9001;
    private static final int REQUEST_SPY_IMPORT = 9002;
    private static final int REQUEST_BACKUP_EXPORT = 9003;
    private static final int REQUEST_BACKUP_RESTORE = 9004;
    private static final int REQUEST_FONT_IMPORT = 9005;

    private static final int ID_NUMBER_ROUNDING = 10;
    private static final int ID_SECONDS = 11;
    private static final int ID_RELATIVE_LAST_SEEN = 12;
    private static final int ID_HIDE_PHONE = 13;
    private static final int ID_SHOW_ID = 14;
    private static final int ID_YANDEX_MAPS = 15;
    private static final int ID_DOWNLOAD_BOOST = 16;
    private static final int ID_UPLOAD_BOOST = 17;

    private static final int ID_TITLE_TEXT = 20;
    private static final int ID_HIDE_STORIES = 21;
    private static final int ID_HEADER_BADGE = 91;
    private static final int ID_GLASS_HEADER = 92;
    private static final int ID_GLASS_PANELS = 93;
    private static final int ID_HIDE_FAB = 22;
    private static final int ID_SPLIT_INPUT = 118;
    private static final int ID_IOS_TRANSITIONS = 119;

    private static final int ID_HIDE_STICKER_TIME = 30;
    private static final int ID_NO_GREETING = 31;
    private static final int ID_HIDE_KEYBOARD = 32;
    private static final int ID_HIDE_SEND_AS = 33;
    private static final int ID_RECENT_STICKERS = 34;
    private static final int ID_ALWAYS_HD = 35;
    private static final int ID_HIDE_REACTIONS = 37;
    private static final int ID_HIDE_REACTIONS_CHANNELS = 38;
    private static final int ID_HIDE_REACTIONS_GROUPS = 39;
    private static final int ID_HIDE_REACTIONS_PRIVATE = 100;
    private static final int ID_MESSAGE_TAIL = 101;
    private static final int ID_EDITED_ICON = 102;
    private static final int ID_ONLINE_INDICATOR = 103;
    private static final int ID_FORWARD_COUNT = 104;
    private static final int ID_HIDE_SHARE = 105;
    private static final int ID_POLL_RESULTS = 106;
    private static final int ID_COMMA_MENTION = 107;
    private static final int ID_DOUBLE_TAP_SEEK = 108;
    private static final int ID_ORIGINAL_QUALITY = 109;
    private static final int ID_VOLUME_UNMUTE = 110;
    private static final int ID_AUTO_PAUSE = 111;
    private static final int ID_AUTO_PAUSE_VIDEO = 112;
    private static final int ID_AUTO_PAUSE_VOICE = 113;
    private static final int ID_AUTO_PAUSE_ROUND = 114;
    private static final int ID_ZALGO = 115;
    private static final int ID_SAVE_FOLDER = 116;
    private static final int ID_SHOW_DC = 117;
    private static final int ID_ROUND_60FPS = 120;
    private static final int ID_ROUND_STABILIZATION = 121;
    private static final int ID_ROUND_WIDE = 122;
    private static final int ID_ROUND_KEEP_ZOOM = 123;
    private static final int ID_ROUND_CAMERA_INFO = 124;
    private static final int ID_ROUND_HORIZON = 125;
    private static final int ID_ROUND_HIGH_QUALITY = 126;
    private static final int ID_SEND_STATS = 127;
    private static final int ID_SEND_REPORT = 128;
    private static final int ID_TEAM = 129;
    private static final int ID_BACKUP_EXPORT = 131;
    private static final int ID_BACKUP_RESTORE = 132;
    private static final int ID_FONT = 133;
    private static final int ID_HIDE_ATTACH_CAMERA = 134;
    private static final int ID_TABS = 135;
    private static final int ID_TABS_RESET = 136;
    private static final int ID_TABS_APPLY = 137;
    // + GlassgramConfig.TAB_*
    private static final int ID_TAB = 140;
    private static final int ID_INPUT_CALCULATOR = 150;
    private static final int ID_MINI_AVATARS = 151;
    private static final int ID_OFFLINE_TRANSCRIBE = 152;
    private static final int ID_TRANSCRIBE_LANG = 153;
    private static final int ID_TRANSCRIBE_MODEL = 154;
    private static final int ID_BUTTON_DATA = 155;
    private static final int ID_PROXY_MODE = 156;
    private static final int ID_CHAT_LOCK = 160;
    private static final int ID_DOUBLE_TAP_ACTION = 161;
    private static final int ID_MESSAGE_MENU = 162;
    private static final int ID_MENU_RESET = 163;
    private static final int ID_LOCK_PIN = 164;
    private static final int ID_LOCK_FINGERPRINT = 165;
    private static final int ID_LOCK_REMOVE = 166;
    private static final int ID_HIDDEN_CHATS = 167;
    private static final int ID_GHOST_SCHEDULE = 168;
    private static final int ID_GHOST_SILENT = 169;
    private static final int ID_GHOST_STORY_ALERT = 170;
    private static final int ID_IOS_CHATS_HEADER = 171;
    private static final int ID_MENU_ITEM = 300;
    private static final int ID_LOCKED_CHAT = 400;

    private final int page;
    private boolean ghostExpanded;
    private boolean reactionsExpanded;
    private boolean autoPauseExpanded;

    private GlassgramWallpaperLayout previewView;
    private View stickerSizeView;
    private View stickerShapeView;
    private GlassgramWallpaperLayout stickerPreviewView;
    private final ArrayList<ChatMessageCell> stickerPreviewCells = new ArrayList<>();
    private ChatMessageCell previewCell;
    private MessageObject previewMessage;
    private View colorsView;
    private View aboutHeader;

    // 0 keeps the time color.
    private static final int[] MARK_COLORS = {0, 0xFFFF0000, 0xFFDC2B2B, 0xFFDB2777, 0xFFC026D3, 0xFF9333EA, 0xFF4F46E5, 0xFF2563EB};

    public GlassgramSettingsActivity() {
        this(PAGE_MAIN);
    }

    public GlassgramSettingsActivity(int page) {
        this.page = page;
        GlassgramConfig.load();
    }

    @Override
    protected CharSequence getTitle() {
        switch (page) {
            case PAGE_GENERAL:
                return getString(R.string.GlassgramGeneral);
            case PAGE_APPEARANCE:
                return getString(R.string.GlassgramAppearance);
            case PAGE_CHATS:
                return getString(R.string.GlassgramChats);
            case PAGE_GHOST:
                return getString(R.string.GlassgramGhostMode);
            case PAGE_SPY:
                return getString(R.string.GlassgramSpy);
            case PAGE_CUSTOMIZATION:
                return getString(R.string.GlassgramCustomization);
            case PAGE_FILTERS:
                return getString(R.string.GlassgramMessageFilters);
            case PAGE_TABS:
                return getString(R.string.GlassgramTabs);
            case PAGE_MESSAGE_MENU:
                return getString(R.string.GlassgramMessageMenu);
            case PAGE_CHAT_LOCK:
                return getString(R.string.GlassgramChatLockSettings);
            default:
                // The header below the action bar carries the name
                return "";
        }
    }

    @Override
    public boolean onFragmentCreate() {
        if (page == PAGE_MAIN) {
            // the team changes in the bot: its button follows
            GlassgramApi.loadTeam(() -> {
                if (listView != null) listView.adapter.update(true);
            });
            if (GlassgramApi.getSession(currentAccount) != null) {
                GlassgramApi.refresh(currentAccount, response -> {
                    if (listView != null) listView.adapter.update(true);
                });
            }
        }
        // the team's rules for this phone as they are now, before an option they turn off is tapped
        GlassgramRemoteConfig.refresh(GlassgramRemoteConfig.REFRESH_SETTINGS, () -> {
            if (listView != null) listView.adapter.update(true);
        });
        return super.onFragmentCreate();
    }

    @Override
    public void onResume() {
        super.onResume();
        // back from the team's screen, maybe signed out there
        if (page == PAGE_MAIN && listView != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    public View createView(Context context) {
        final View view = super.createView(context);
        // exteraGram's layout: rounded cards on the gray background, titles above them
        listView.adapter.setApplyBackground(false);
        listView.setSections(true);
        if (page == PAGE_TABS) {
            // a tab is dragged to its place
            listView.listenReorder(this::onTabsReordered);
            listView.allowReorder(true);
        } else if (page == PAGE_MESSAGE_MENU) {
            // an item is dragged to its place
            listView.listenReorder(this::onMenuReordered);
            listView.allowReorder(true);
        }
        return view;
    }

    private static UItem header(CharSequence text) {
        return GlassgramSettingsCells.Header.of(text);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        switch (page) {
            case PAGE_GENERAL:
                items.add(header(getString(R.string.GlassgramGeneral)));
                items.add(GlassgramSettingsCells.ExampleCheck.of(ID_NUMBER_ROUNDING, getString(R.string.GlassgramDisableNumberRounding), getString(R.string.GlassgramNumberRoundingExample), GlassgramConfig.disableNumberRounding));
                items.add(GlassgramSettingsCells.ExampleCheck.of(ID_SECONDS, getString(R.string.GlassgramTimeWithSeconds), getString(R.string.GlassgramTimeWithSecondsExample), GlassgramConfig.formatTimeWithSeconds));
                items.add(UItem.asCheck(ID_ZALGO, getString(R.string.GlassgramFilterZalgo)).setChecked(GlassgramConfig.filterZalgo));
                items.add(UItem.asShadow(getString(R.string.GlassgramGeneralInfo) + "\n\n" + getString(R.string.GlassgramFilterZalgoInfo)));
                items.add(header(getString(R.string.GlassgramStorage)));
                items.add(UItem.asButton(ID_SAVE_FOLDER, getString(R.string.GlassgramSaveFolder), saveFolderName()));
                items.add(UItem.asShadow(LocaleController.formatString(R.string.GlassgramSaveFolderInfo, saveFolderName(), saveFolderName())));
                items.add(header(getString(R.string.GlassgramMaps)));
                items.add(UItem.asCheck(ID_YANDEX_MAPS, getString(R.string.GlassgramUseYandexMaps)).setChecked(GlassgramConfig.useYandexMaps));
                items.add(UItem.asShadow(getString(R.string.GlassgramUseYandexMapsInfo)));
                items.add(header(getString(R.string.GlassgramDownloadBoost)));
                items.add(UItem.asButton(ID_DOWNLOAD_BOOST, getString(R.string.GlassgramDownloadBoost), boostNames()[GlassgramConfig.downloadBoost]));
                items.add(UItem.asCheck(ID_UPLOAD_BOOST, getString(R.string.GlassgramUploadBoost)).setChecked(GlassgramConfig.uploadBoost));
                items.add(UItem.asShadow(getString(R.string.GlassgramBoostInfo)));
                // no way around blocks in the RuStore edition
                if (!GlassgramConfig.STORE_BUILD) {
                    items.add(header(getString(R.string.GlassgramProxy)));
                    items.add(UItem.asButton(ID_PROXY_MODE, getString(R.string.GlassgramProxy), proxyModeNames()[GlassgramConfig.proxyMode]));
                    items.add(UItem.asShadow(getString(R.string.GlassgramProxyInfo) + "\n\n" + proxyStatus()));
                }
                items.add(header(getString(R.string.GlassgramProfile)));
                items.add(UItem.asCheck(ID_RELATIVE_LAST_SEEN, getString(R.string.GlassgramRelativeLastSeen)).setChecked(GlassgramConfig.relativeLastSeen));
                items.add(UItem.asCheck(ID_HIDE_PHONE, getString(R.string.GlassgramHidePhone)).setChecked(GlassgramConfig.hidePhoneNumber));
                items.add(UItem.asButton(ID_SHOW_ID, getString(R.string.GlassgramShowId), showIdNames()[GlassgramConfig.showId]));
                items.add(UItem.asCheck(ID_SHOW_DC, getString(R.string.GlassgramShowDc)).setChecked(GlassgramConfig.showDc));
                items.add(UItem.asShadow(getString(R.string.GlassgramProfileInfo)));
                break;
            case PAGE_APPEARANCE:
                items.add(header(getString(R.string.GlassgramChatList)));
                items.add(UItem.asButton(ID_TITLE_TEXT, getString(R.string.GlassgramTitleText), GlassgramConfig.getTitle()));
                items.add(UItem.asCheck(ID_HIDE_STORIES, getString(R.string.GlassgramHideStories)).setChecked(GlassgramConfig.hideStories));
                items.add(UItem.asCheck(ID_HEADER_BADGE, getString(R.string.GlassgramHeaderBadge)).setChecked(GlassgramConfig.headerBadge));
                items.add(UItem.asCheck(ID_GLASS_HEADER, getString(R.string.GlassgramGlassHeader)).setChecked(GlassgramConfig.glassHeader));
                if (GlassgramConfig.glassHeader) {
                    items.add(UItem.asCheck(ID_IOS_CHATS_HEADER, getString(R.string.GlassgramIosChatsHeader)).setChecked(GlassgramConfig.iosChatsHeader));
                }
                items.add(UItem.asCheck(ID_HIDE_FAB, getString(R.string.GlassgramHideFloatingButton)).setChecked(GlassgramConfig.hideFloatingButton));
                items.add(UItem.asCheck(ID_SPLIT_INPUT, getString(R.string.GlassgramSplitInput)).setChecked(GlassgramConfig.splitInput));
                items.add(UItem.asCheck(ID_GLASS_PANELS, getString(R.string.GlassgramGlassPanels)).setChecked(GlassgramConfig.glassPanels));
                items.add(UItem.asButton(ID_TABS, getString(R.string.GlassgramTabs), tabsSummary()));
                items.add(UItem.asShadow(getString(R.string.GlassgramChatListInfo)));
                items.add(header(getString(R.string.GlassgramFontHeader)));
                items.add(UItem.asButton(ID_FONT, getString(R.string.GlassgramFont), fontName(GlassgramConfig.font)));
                items.add(UItem.asShadow(getString(R.string.GlassgramFontInfo)));
                items.add(header(getString(R.string.GlassgramAnimations)));
                items.add(UItem.asCheck(ID_IOS_TRANSITIONS, getString(R.string.GlassgramIosTransitions)).setChecked(GlassgramConfig.iosTransitions));
                items.add(UItem.asShadow(getString(R.string.GlassgramIosTransitionsInfo)));
                break;
            case PAGE_CHATS: {
                // Sticker size with a chat preview, as one card
                items.add(UItem.asCustom(getStickerSizeView()));
                items.add(UItem.asCustom(getStickerPreviewView()));
                items.add(UItem.asCheck(ID_HIDE_STICKER_TIME, getString(R.string.GlassgramHideStickerTime)).setChecked(GlassgramConfig.hideTimeOnStickers));
                items.add(header(getString(R.string.GlassgramStickerShape)));
                items.add(UItem.asCustom(getStickerShapeView()));
                items.add(header(getString(R.string.GlassgramStickers)));
                items.add(UItem.asCheck(ID_RECENT_STICKERS, getString(R.string.GlassgramUnlimitedRecentStickers)).setChecked(GlassgramConfig.unlimitedRecentStickers));
                items.add(UItem.asCheck(ID_NO_GREETING, getString(R.string.GlassgramDisableGreeting)).setChecked(GlassgramConfig.disableGreetingSticker));
                final int reactionsHidden = (GlassgramConfig.hideReactionsChannels ? 1 : 0) + (GlassgramConfig.hideReactionsGroups ? 1 : 0) + (GlassgramConfig.hideReactionsPrivate ? 1 : 0);
                items.add(UItem.asExpandableSwitch(ID_HIDE_REACTIONS, getString(R.string.GlassgramHideReactions), reactionsHidden + "/3")
                        .setChecked(GlassgramConfig.hideReactions)
                        .setCollapsed(!reactionsExpanded)
                        .setClickCallback(v -> toggleSwitch("hideReactions")));
                if (reactionsExpanded) {
                    items.add(UItem.asRoundCheckbox(ID_HIDE_REACTIONS_CHANNELS, getString(R.string.GlassgramChannels)).setChecked(GlassgramConfig.hideReactionsChannels).setPad(1));
                    items.add(UItem.asRoundCheckbox(ID_HIDE_REACTIONS_GROUPS, getString(R.string.GlassgramGroups)).setChecked(GlassgramConfig.hideReactionsGroups).setPad(1));
                    items.add(UItem.asRoundCheckbox(ID_HIDE_REACTIONS_PRIVATE, getString(R.string.GlassgramPrivateChats)).setChecked(GlassgramConfig.hideReactionsPrivate).setPad(1));
                }
                items.add(UItem.asShadow(getString(R.string.GlassgramHideReactionsInfo)));
                items.add(header(getString(R.string.GlassgramChats)));
                items.add(UItem.asCheck(ID_HIDE_KEYBOARD, getString(R.string.GlassgramHideKeyboardOnScroll)).setChecked(GlassgramConfig.hideKeyboardOnScroll));
                items.add(UItem.asCheck(ID_COMMA_MENTION, getString(R.string.GlassgramCommaAfterMention)).setChecked(GlassgramConfig.commaAfterMention));
                items.add(buttonCheck(ID_BUTTON_DATA, getString(R.string.GlassgramButtonData), getString(R.string.GlassgramButtonDataInfo)).setChecked(GlassgramConfig.inlineButtonData));
                items.add(buttonCheck(ID_INPUT_CALCULATOR, getString(R.string.GlassgramInputCalculator), getString(R.string.GlassgramInputCalculatorInfo)).setChecked(GlassgramConfig.inputCalculator));
                items.add(UItem.asCheck(ID_HIDE_SEND_AS, getString(R.string.GlassgramHideSendAs)).setChecked(GlassgramConfig.hideSendAsButton));
                items.add(UItem.asShadow(getString(R.string.GlassgramHideSendAsInfo)));
                items.add(header(getString(R.string.GlassgramMessages)));
                items.add(UItem.asButton(ID_DOUBLE_TAP_ACTION, getString(R.string.GlassgramDoubleTapAction), doubleTapNames()[GlassgramConfig.doubleTapAction]));
                items.add(UItem.asButton(ID_MESSAGE_MENU, getString(R.string.GlassgramMessageMenu), getString(GlassgramMessageMenu.isCustomized() ? R.string.GlassgramMessageMenuOwn : R.string.GlassgramMessageMenuTelegram)));
                items.add(UItem.asCheck(ID_MESSAGE_TAIL, getString(R.string.GlassgramRemoveMessageTail)).setChecked(GlassgramConfig.removeMessageTail));
                items.add(UItem.asCheck(ID_EDITED_ICON, getString(R.string.GlassgramEditedIcon)).setChecked(GlassgramConfig.editedIcon));
                items.add(UItem.asCheck(ID_ONLINE_INDICATOR, getString(R.string.GlassgramOnlineIndicator)).setChecked(GlassgramConfig.showOnlineIndicator));
                items.add(buttonCheck(ID_MINI_AVATARS, getString(R.string.GlassgramMiniAvatars), getString(R.string.GlassgramMiniAvatarsInfo)).setChecked(GlassgramConfig.senderMiniAvatars));
                items.add(UItem.asCheck(ID_FORWARD_COUNT, getString(R.string.GlassgramForwardCount)).setChecked(GlassgramConfig.showForwardCount));
                items.add(UItem.asCheck(ID_HIDE_SHARE, getString(R.string.GlassgramHideShareButton)).setChecked(GlassgramConfig.hideShareButton));
                // not in the RuStore edition: Telegram shows a poll's results only after voting
                if (!GlassgramConfig.STORE_BUILD) {
                    items.add(buttonCheck(ID_POLL_RESULTS, getString(R.string.GlassgramPollResults), getString(R.string.GlassgramPollResultsInfo)).setChecked(GlassgramConfig.showPollResults));
                }
                items.add(header(getString(R.string.GlassgramPhotos)));
                items.add(UItem.asCheck(ID_HIDE_ATTACH_CAMERA, getString(R.string.GlassgramHideAttachCamera)).setChecked(GlassgramConfig.hideAttachCamera));
                items.add(UItem.asCheck(ID_ALWAYS_HD, getString(R.string.GlassgramAlwaysSendHD)).setChecked(org.telegram.messenger.SharedConfig.photoHighQualityDefault));
                items.add(UItem.asShadow(getString(R.string.GlassgramAlwaysSendHDInfo)));
                items.add(header(getString(R.string.GlassgramVideos)));
                items.add(UItem.asButton(ID_DOUBLE_TAP_SEEK, getString(R.string.GlassgramDoubleTapSeek), seekNames()[seekIndex()]));
                items.add(UItem.asCheck(ID_ORIGINAL_QUALITY, getString(R.string.GlassgramOriginalQuality)).setChecked(GlassgramConfig.preferOriginalQuality));
                items.add(buttonCheck(ID_VOLUME_UNMUTE, getString(R.string.GlassgramVolumeUnmute), getString(R.string.GlassgramVolumeUnmuteInfo)).setChecked(GlassgramConfig.volumeUnmute));
                final int pauses = (GlassgramConfig.autoPauseVideo ? 1 : 0) + (GlassgramConfig.autoPauseVoice ? 1 : 0) + (GlassgramConfig.autoPauseRound ? 1 : 0);
                items.add(UItem.asExpandableSwitch(ID_AUTO_PAUSE, getString(R.string.GlassgramAutoPause), pauses + "/3")
                        .setChecked(GlassgramConfig.autoPause)
                        .setCollapsed(!autoPauseExpanded)
                        .setClickCallback(v -> toggleSwitch("autoPause")));
                if (autoPauseExpanded) {
                    items.add(UItem.asRoundCheckbox(ID_AUTO_PAUSE_VIDEO, getString(R.string.GlassgramAutoPauseVideo)).setChecked(GlassgramConfig.autoPauseVideo).setPad(1));
                    items.add(UItem.asRoundCheckbox(ID_AUTO_PAUSE_VOICE, getString(R.string.GlassgramAutoPauseVoice)).setChecked(GlassgramConfig.autoPauseVoice).setPad(1));
                    items.add(UItem.asRoundCheckbox(ID_AUTO_PAUSE_ROUND, getString(R.string.GlassgramAutoPauseRound)).setChecked(GlassgramConfig.autoPauseRound).setPad(1));
                }
                items.add(UItem.asShadow(getString(R.string.GlassgramAutoPauseInfo)));
                items.add(header(getString(R.string.GlassgramTranscription)));
                items.add(buttonCheck(ID_OFFLINE_TRANSCRIBE, getString(R.string.GlassgramOfflineTranscription), getString(R.string.GlassgramOfflineTranscriptionInfo)).setChecked(GlassgramConfig.offlineTranscription));
                if (GlassgramConfig.offlineTranscription) {
                    items.add(UItem.asButton(ID_TRANSCRIBE_LANG, getString(R.string.GlassgramTranscriptionLanguage), transcriptionLangName(GlassgramConfig.offlineTranscriptionLang)));
                    items.add(UItem.asButton(ID_TRANSCRIBE_MODEL, transcriptionModelText(), transcriptionModelValue()));
                }
                items.add(UItem.asShadow(getString(R.string.GlassgramTranscriptionInfo)));
                items.add(header(getString(R.string.GlassgramRoundVideos)));
                items.add(buttonCheck(ID_ROUND_60FPS, getString(R.string.GlassgramRound60Fps), getString(R.string.GlassgramRound60FpsInfo)).setChecked(GlassgramConfig.roundFps60));
                items.add(buttonCheck(ID_ROUND_STABILIZATION, getString(R.string.GlassgramRoundStabilization), getString(R.string.GlassgramRoundStabilizationInfo)).setChecked(GlassgramConfig.roundStabilization));
                if (GlassgramConfig.roundStabilization) {
                    items.add(buttonCheck(ID_ROUND_HORIZON, getString(R.string.GlassgramRoundHorizon), getString(R.string.GlassgramRoundHorizonInfo)).setChecked(GlassgramConfig.roundHorizon));
                }
                items.add(buttonCheck(ID_ROUND_HIGH_QUALITY, getString(R.string.GlassgramRoundHighQuality), getString(R.string.GlassgramRoundHighQualityInfo)).setChecked(GlassgramConfig.roundHighQuality));
                items.add(buttonCheck(ID_ROUND_WIDE, getString(R.string.GlassgramRoundWide), getString(R.string.GlassgramRoundWideInfo)).setChecked(GlassgramConfig.roundWideCamera));
                items.add(buttonCheck(ID_ROUND_KEEP_ZOOM, getString(R.string.GlassgramRoundKeepZoom), getString(R.string.GlassgramRoundKeepZoomInfo)).setChecked(GlassgramConfig.roundKeepZoom));
                items.add(UItem.asButton(ID_ROUND_CAMERA_INFO, getString(R.string.GlassgramRoundCameraInfo)));
                items.add(UItem.asShadow(getString(R.string.GlassgramRoundVideosInfo)));
                break;
            }
            case PAGE_TABS: {
                loadTabs();
                items.add(header(getString(R.string.GlassgramTabsHeader)));
                tabsReorderId = adapter.reorderSectionStart();
                for (int tab : tabsOrder) {
                    items.add(UItem.asCheck(ID_TAB + tab, tabName(tab)).setChecked(tabsOn[tab]));
                }
                adapter.reorderSectionEnd();
                items.add(UItem.asShadow(getString(R.string.GlassgramTabsInfo)));
                if (tabsChanged) {
                    items.add(UItem.asButton(ID_TABS_APPLY, getString(R.string.GlassgramTabsApply)).accent());
                }
                if (!GlassgramConfig.mainTabs.isEmpty()) {
                    items.add(UItem.asButton(ID_TABS_RESET, getString(R.string.GlassgramTabsReset)));
                }
                items.add(UItem.asShadow(null));
                break;
            }
            case PAGE_MESSAGE_MENU: {
                final ArrayList<GlassgramMessageMenu.Kind> order = GlassgramMessageMenu.orderedKinds();
                final java.util.Set<String> hidden = GlassgramMessageMenu.hiddenKeys();
                items.add(header(getString(R.string.GlassgramMessageMenuHeader)));
                menuReorderId = adapter.reorderSectionStart();
                for (GlassgramMessageMenu.Kind kind : order) {
                    items.add(UItem.asCheck(ID_MENU_ITEM + kindIndex(kind), getString(kind.title)).setChecked(!hidden.contains(kind.key)));
                }
                adapter.reorderSectionEnd();
                items.add(UItem.asShadow(getString(R.string.GlassgramMessageMenuInfo)));
                if (GlassgramMessageMenu.isCustomized()) {
                    items.add(UItem.asButton(ID_MENU_RESET, getString(R.string.GlassgramMessageMenuReset)).red());
                    items.add(UItem.asShadow(null));
                }
                break;
            }
            case PAGE_CHAT_LOCK: {
                final boolean hasPin = GlassgramChatLock.hasPin();
                items.add(header(getString(R.string.GlassgramChatLockPinHeader)));
                items.add(UItem.asButton(ID_LOCK_PIN, R.drawable.msg_permissions, getString(hasPin ? R.string.GlassgramChatLockChangePin : R.string.GlassgramChatLockSetPin)));
                if (hasPin && GlassgramChatLockUi.canUseFingerprint(getContext())) {
                    items.add(UItem.asCheck(ID_LOCK_FINGERPRINT, getString(R.string.GlassgramChatLockFingerprint)).setChecked(GlassgramChatLock.useFingerprint()));
                }
                if (hasPin) {
                    items.add(UItem.asButton(ID_LOCK_REMOVE, R.drawable.msg_delete, getString(R.string.GlassgramChatLockRemovePin)).red());
                }
                items.add(UItem.asShadow(getString(R.string.GlassgramChatLockInfo)));
                items.add(header(getString(R.string.GlassgramHiddenChats)));
                items.add(UItem.asButton(ID_HIDDEN_CHATS, R.drawable.msg_archive_hide, getString(R.string.GlassgramHiddenChatsOpen)));
                items.add(UItem.asShadow(getString(R.string.GlassgramHiddenChatsHint)));
                lockedChats.clear();
                lockedChats.addAll(GlassgramChatLock.lockedDialogs(currentAccount));
                if (!lockedChats.isEmpty()) {
                    items.add(header(getString(R.string.GlassgramLockedChats)));
                    for (int i = 0; i < lockedChats.size(); i++) {
                        final UItem chat = UItem.asFilterChat(true, lockedChats.get(i));
                        chat.id = ID_LOCKED_CHAT + i;
                        items.add(chat);
                    }
                    items.add(UItem.asShadow(getString(R.string.GlassgramLockedChatsInfo)));
                }
                break;
            }
            case PAGE_GHOST: {
                int enabled = ghostOptionsEnabled();
                items.add(header(getString(R.string.GlassgramGhostEssentials)));
                items.add(UItem.asExpandableSwitch(ID_GHOST_MODE, getString(R.string.GlassgramGhostMode), enabled + "/5")
                        .setChecked(GlassgramConfig.ghostMode)
                        .setCollapsed(!ghostExpanded)
                        .setClickCallback(v -> toggleGhostMode()));
                if (ghostExpanded) {
                    items.add(UItem.asRoundCheckbox(ID_GHOST_READ, getString(R.string.GlassgramGhostNoRead)).setChecked(GlassgramConfig.ghostNoReadMessages).setPad(1));
                    items.add(UItem.asRoundCheckbox(ID_GHOST_STORIES, getString(R.string.GlassgramGhostNoStories)).setChecked(GlassgramConfig.ghostNoReadStories).setPad(1));
                    items.add(UItem.asRoundCheckbox(ID_GHOST_ONLINE, getString(R.string.GlassgramGhostNoOnline)).setChecked(GlassgramConfig.ghostNoOnline).setPad(1));
                    items.add(UItem.asRoundCheckbox(ID_GHOST_TYPING, getString(R.string.GlassgramGhostNoTyping)).setChecked(GlassgramConfig.ghostNoTyping).setPad(1));
                    items.add(UItem.asRoundCheckbox(ID_GHOST_OFFLINE, getString(R.string.GlassgramGhostAutoOffline)).setChecked(GlassgramConfig.ghostAutoOffline).setPad(1));
                }
                items.add(UItem.asCheck(ID_GHOST_READ_ON_INTERACT, getString(R.string.GlassgramGhostReadOnInteract)).setChecked(GlassgramConfig.ghostReadOnInteract));
                items.add(UItem.asShadow(getString(R.string.GlassgramGhostInfo) + "\n\n" + getString(R.string.GlassgramGhostReadOnInteractInfo2)));
                items.add(UItem.asCheck(ID_GHOST_SCHEDULE, getString(R.string.GlassgramGhostSchedule)).setChecked(GlassgramConfig.ghostScheduleMessages));
                items.add(UItem.asShadow(getString(R.string.GlassgramGhostScheduleInfo)));
                items.add(UItem.asButton(ID_GHOST_SILENT, getString(R.string.GlassgramGhostSilent), ghostSilentNames()[GlassgramConfig.ghostSilentSend]));
                items.add(UItem.asShadow(getString(R.string.GlassgramGhostSilentInfo)));
                items.add(UItem.asCheck(ID_GHOST_STORY_ALERT, getString(R.string.GlassgramGhostStoryAlert)).setChecked(GlassgramConfig.ghostStoryAlert));
                items.add(UItem.asShadow(getString(R.string.GlassgramGhostStoryAlertInfo)));
                break;
            }
            case PAGE_SPY:
                items.add(header(getString(R.string.GlassgramSpyEssentials)));
                items.add(UItem.asCheck(ID_SPY_DELETED, getString(R.string.GlassgramSpySaveDeleted)).setChecked(GlassgramConfig.spySaveDeletedMessages));
                items.add(UItem.asCheck(ID_SPY_EDITS, getString(R.string.GlassgramSpySaveEdits)).setChecked(GlassgramConfig.spySaveEditsHistory));
                items.add(UItem.asShadow(getString(R.string.GlassgramSpyEssentialsInfo)));
                items.add(UItem.asCheck(ID_SPY_BOTS, getString(R.string.GlassgramSpySaveBots)).setChecked(GlassgramConfig.spySaveInBotDialogs));
                items.add(UItem.asShadow(getString(R.string.GlassgramSpySaveBotsInfo)));
                items.add(UItem.asCheck(ID_SPY_READ_DATE, getString(R.string.GlassgramSpySaveReadDate)).setChecked(GlassgramConfig.spySaveReadDate));
                items.add(UItem.asShadow(getString(R.string.GlassgramSpySaveReadDateInfo)));
                items.add(UItem.asCheck(ID_SPY_LAST_SEEN, getString(R.string.GlassgramSpySaveLastSeen)).setChecked(GlassgramConfig.spySaveLastSeenDate));
                items.add(UItem.asShadow(AndroidUtilities.replaceTags(getString(R.string.GlassgramSpySaveLastSeenInfo))));
                items.add(buttonCheck(ID_SPY_ATTACHMENTS, getString(R.string.GlassgramSpySaveAttachments), getString(R.string.GlassgramSpySaveAttachmentsSub)).setChecked(GlassgramConfig.spySaveAttachments));
                items.add(UItem.asCheck(ID_CUSTOM_SELF_DESTRUCT, getString(R.string.GlassgramDisableSelfDestruct)).setChecked(GlassgramConfig.spyDisableSelfDestruct));
                items.add(UItem.asCheck(ID_CONTENT_PROTECTION, getString(R.string.GlassgramIgnoreContentProtection)).setChecked(GlassgramConfig.ignoreContentProtection));
                items.add(UItem.asShadow(getString(R.string.GlassgramProtectedInfo)));
                items.add(header(getString(R.string.GlassgramSpyMaxFolderSize)));
                items.add(UItem.asSlideView(folderSizeNames(), Math.max(0, Math.min(5, GlassgramConfig.spyMaxFolderSize)), which -> {
                    GlassgramConfig.spyMaxFolderSize = which;
                    GlassgramConfig.putInt("spyMaxFolderSize", which);
                }));
                items.add(UItem.asShadow(getString(R.string.GlassgramSpyMaxFolderSizeInfo)));
                items.add(UItem.asButton(ID_SPY_EXPORT, R.drawable.msg_unarchive, getString(R.string.GlassgramSpyExport)));
                items.add(UItem.asButton(ID_SPY_IMPORT, R.drawable.msg_archive, getString(R.string.GlassgramSpyImport)));
                items.add(UItem.asShadow(null));
                items.add(UItem.asButton(ID_SPY_CLEAR, R.drawable.msg_clearcache, getString(R.string.GlassgramSpyClear)).red());
                items.add(UItem.asShadow(LocaleController.formatString(R.string.GlassgramSpyStorageInfo, AndroidUtilities.formatFileSize(GlassgramSpyStorage.size()))));
                break;
            case PAGE_CUSTOMIZATION:
                items.add(header(getString(R.string.GlassgramCustomization)));
                items.add(UItem.asCustom(getPreviewView()));
                items.add(UItem.asCheck(ID_CUSTOM_TRANSLUCENT, getString(R.string.GlassgramTranslucentDeleted)).setChecked(GlassgramConfig.spyTranslucentDeleted));
                items.add(UItem.asCheck(ID_CUSTOM_TRASH, getString(R.string.GlassgramDeletedMark)).setChecked(GlassgramConfig.spyDeletedTrashMark));
                if (GlassgramConfig.spyDeletedTrashMark) {
                    items.add(UItem.asCustom(getColorsView()));
                }
                items.add(header(getString(R.string.GlassgramUsefulFeatures)));
                items.add(UItem.asCheck(ID_CUSTOM_ADS, getString(R.string.GlassgramDisableAds)).setChecked(GlassgramConfig.spyDisableAds));
                items.add(UItem.asCheck(ID_CUSTOM_GHOST_STATUS, getString(R.string.GlassgramDisplayGhostStatus)).setChecked(GlassgramConfig.spyDisplayGhostStatus));
                items.add(UItem.asShadow(null));
                break;
            case PAGE_FILTERS:
                items.add(header(getString(R.string.GlassgramFiltersEssentials)));
                items.add(UItem.asCheck(ID_FILTER_ENABLE, getString(R.string.GlassgramFiltersEnable)).setChecked(GlassgramConfig.spyEnableFilters));
                items.add(UItem.asCheck(ID_FILTER_SHARED, getString(R.string.GlassgramFiltersShared)).setChecked(GlassgramConfig.spyEnableSharedFilters));
                items.add(UItem.asCheck(ID_FILTER_BLOCKED, getString(R.string.GlassgramFiltersBlocked)).setChecked(GlassgramConfig.spyHideBlockedUsers));
                items.add(UItem.asShadow(null));
                items.add(UItem.asButton(ID_FILTER_SHARED_LIST, R.drawable.msg_settings, getString(R.string.GlassgramFiltersSharedList)));
                items.add(UItem.asCheck(ID_FILTER_SHADOW, getString(R.string.GlassgramFiltersShadowBan)).setChecked(GlassgramConfig.spyShadowBan));
                items.add(UItem.asShadow(getString(R.string.GlassgramFiltersInfo)));
                break;
            default:
                items.add(UItem.asCustomShadow(getAboutHeader()));
                items.add(header(getString(R.string.GlassgramCategories)));
                items.add(UItem.asButton(ID_GENERAL, R.drawable.msg_settings, getString(R.string.GlassgramGeneral)));
                items.add(UItem.asButton(ID_APPEARANCE, R.drawable.msg_palette, getString(R.string.GlassgramAppearance)));
                items.add(UItem.asButton(ID_CHATS, R.drawable.msg_discussion, getString(R.string.GlassgramChats)));
                // The RuStore edition has no ghost mode and no spy, nor their look of deleted messages
                if (!GlassgramConfig.STORE_BUILD) {
                    items.add(UItem.asButton(ID_CUSTOMIZATION, R.drawable.msg_colors, getString(R.string.GlassgramCustomization)));
                }
                items.add(UItem.asButton(ID_PLUGINS, R.drawable.msg_bots, getString(R.string.GlassgramPlugins)));
                if (GlassgramConfig.STORE_BUILD) {
                    // no "Spy & Ghost" section: the filters join the categories
                    items.add(UItem.asButton(ID_FILTERS, R.drawable.msg_block, getString(R.string.GlassgramMessageFilters)));
                    items.add(UItem.asButton(ID_CHAT_LOCK, R.drawable.msg_secret, getString(R.string.GlassgramChatLockSettings)));
                } else {
                    items.add(header(getString(R.string.GlassgramPrivacySection)));
                    items.add(UItem.asButton(ID_GHOST, R.drawable.msg_stories_stealth, getString(R.string.GlassgramGhostMode)));
                    items.add(UItem.asButton(ID_SPY, R.drawable.msg_stories_views, getString(R.string.GlassgramSpy)));
                    items.add(UItem.asButton(ID_FILTERS, R.drawable.msg_block, getString(R.string.GlassgramMessageFilters)));
                    items.add(UItem.asButton(ID_CHAT_LOCK, R.drawable.msg_secret, getString(R.string.GlassgramChatLockSettings)));
                }
                items.add(header(getString(R.string.GlassgramLinks)));
                items.add(UItem.asButton(ID_CHANNEL, R.drawable.msg_channel, getString(R.string.GlassgramChannel), "@glassgramdev"));
                items.add(UItem.asButton(ID_SOURCE_CODE, R.drawable.msg_link, getString(R.string.GlassgramSourceCode), "GitHub"));
                items.add(header(getString(R.string.GlassgramDeveloperSection)));
                items.add(buttonCheck(ID_SEND_STATS, getString(R.string.GlassgramSendStats), getString(R.string.GlassgramSendStatsInfo)).setChecked(GlassgramConfig.sendStats));
                items.add(UItem.asButton(ID_SEND_REPORT, R.drawable.msg_send, getString(R.string.GlassgramSendReport)));
                // the team's screen, with its sign-in, only for those on the team
                if (GlassgramApi.isTeam(currentAccount)) {
                    final GlassgramApi.Session session = GlassgramApi.getSession(currentAccount);
                    items.add(UItem.asButton(ID_TEAM, R.drawable.msg_admins, getString(R.string.GlassgramApiTeam),
                            session != null && session.isStaff() ? session.levelName : null));
                }
                items.add(header(getString(R.string.GlassgramBackup)));
                items.add(UItem.asButton(ID_BACKUP_EXPORT, R.drawable.msg_shareout, getString(R.string.GlassgramBackupExport)));
                items.add(UItem.asButton(ID_BACKUP_RESTORE, R.drawable.msg_archive, getString(R.string.GlassgramBackupRestore)));
                items.add(UItem.asShadow(getString(R.string.GlassgramPreferencesInfo)));
                break;
        }
    }

    /** The option a row turns, for the team's rules that may turn it off on a phone (GlassgramRemoteConfig). */
    private static String featureOf(int id) {
        switch (id) {
            case ID_ROUND_60FPS: return "roundFps60";
            case ID_ROUND_STABILIZATION: return "roundStabilization";
            case ID_ROUND_HORIZON: return "roundHorizon";
            case ID_ROUND_WIDE: return "roundWideCamera";
            case ID_ROUND_KEEP_ZOOM: return "roundKeepZoom";
            case ID_ROUND_HIGH_QUALITY: return "roundHighQuality";
            case ID_GLASS_HEADER: return "glassHeader";
            case ID_GLASS_PANELS: return "glassPanels";
            case ID_SPLIT_INPUT: return "splitInput";
            case ID_IOS_TRANSITIONS: return "iosTransitions";
            case ID_UPLOAD_BOOST: return "uploadBoost";
            case ID_DOWNLOAD_BOOST: return "downloadBoost";
            default: return null;
        }
    }

    /** Says why an option can't be turned on here: the team turned it off for this phone. */
    static void showForcedOff(org.telegram.ui.ActionBar.BaseFragment fragment, String feature) {
        final String note = GlassgramRemoteConfig.note(feature);
        BulletinFactory.of(fragment).createErrorBulletin(TextUtils.isEmpty(note) ? getString(R.string.GlassgramForcedOff)
                : LocaleController.formatString(R.string.GlassgramForcedOffNote, note)).show();
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        final String feature = featureOf(item.id);
        if (feature != null && GlassgramRemoteConfig.isForced(feature)) {
            showForcedOff(this, feature);
            return;
        }
        switch (item.id) {
            case ID_GENERAL:
                presentFragment(new GlassgramSettingsActivity(PAGE_GENERAL));
                return;
            case ID_APPEARANCE:
                presentFragment(new GlassgramSettingsActivity(PAGE_APPEARANCE));
                return;
            case ID_CHATS:
                presentFragment(new GlassgramSettingsActivity(PAGE_CHATS));
                return;
            case ID_GHOST:
                presentFragment(new GlassgramSettingsActivity(PAGE_GHOST));
                return;
            case ID_SPY:
                presentFragment(new GlassgramSettingsActivity(PAGE_SPY));
                return;
            case ID_CUSTOMIZATION:
                presentFragment(new GlassgramSettingsActivity(PAGE_CUSTOMIZATION));
                return;
            case ID_FILTERS:
                presentFragment(new GlassgramSettingsActivity(PAGE_FILTERS));
                return;
            case ID_PLUGINS:
                presentFragment(new GlassgramPluginsActivity());
                return;
            case ID_SEND_REPORT:
                GlassgramApiUi.sendReport(this);
                return;
            case ID_BACKUP_EXPORT:
                startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
                        .putExtra(Intent.EXTRA_TITLE, "glassgram_settings.json"), REQUEST_BACKUP_EXPORT);
                return;
            case ID_BACKUP_RESTORE:
                new AlertDialog.Builder(getParentActivity())
                        .setTitle(getString(R.string.GlassgramBackupRestore))
                        .setMessage(getString(R.string.GlassgramBackupRestoreConfirm))
                        .setPositiveButton(getString(R.string.GlassgramBackupChooseFile), (d, w) ->
                                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                                        .addCategory(Intent.CATEGORY_OPENABLE), REQUEST_BACKUP_RESTORE))
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .show();
                return;
            case ID_TEAM: {
                final GlassgramApi.Session session = GlassgramApi.getSession(currentAccount);
                if (session != null && session.isStaff()) {
                    presentFragment(new GlassgramDeveloperActivity());
                } else if (session == null) {
                    GlassgramApiUi.signIn(this, this::onTeamSignedIn);
                } else {
                    // signed in without rights: they may have been given since
                    GlassgramApi.refresh(currentAccount, response -> {
                        if (response.ok()) {
                            onTeamSignedIn();
                        } else if (response.status == 401) {
                            GlassgramApiUi.signIn(this, this::onTeamSignedIn);
                        } else {
                            GlassgramApiUi.showError(this, response);
                        }
                    });
                }
                return;
            }
            case ID_CHANNEL:
                org.telegram.messenger.browser.Browser.openUrl(getParentActivity(), "https://t.me/glassgramdev");
                return;
            case ID_SOURCE_CODE:
                org.telegram.messenger.browser.Browser.openUrl(getParentActivity(), "https://github.com/shashachkaaa/Telegram");
                return;
            case ID_SPY_LIMIT:
                showChoice("Max folder size", new String[]{"300 MB","1 GB","2 GB","5 GB","16 GB","No limit"}, GlassgramConfig.spyMaxFolderSize, which -> {
                    GlassgramConfig.spyMaxFolderSize = which;
                    GlassgramConfig.putInt("spyMaxFolderSize", which);
                });
                return;
            case ID_SPY_EXPORT:
                startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").putExtra(Intent.EXTRA_TITLE, "glassgram_spy.jsonl"), REQUEST_SPY_EXPORT);
                return;
            case ID_SPY_IMPORT:
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE), REQUEST_SPY_IMPORT);
                return;
            case ID_SPY_CLEAR:
                new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.GlassgramSpyClearTitle))
                        .setMessage(getString(R.string.GlassgramSpyClearText))
                        .setPositiveButton(getString(R.string.Delete), (d, w) -> {
                            GlassgramSpyStorage.clear();
                            if (listView != null) listView.adapter.update(true);
                        }).setNegativeButton(getString(R.string.Cancel), null).show();
                return;
            case ID_GHOST_MODE:
                ghostExpanded = !ghostExpanded;
                if (listView != null) listView.adapter.update(true);
                return;
            case ID_HIDE_REACTIONS:
                reactionsExpanded = !reactionsExpanded;
                if (listView != null) listView.adapter.update(true);
                return;
            case ID_AUTO_PAUSE:
                autoPauseExpanded = !autoPauseExpanded;
                if (listView != null) listView.adapter.update(true);
                return;
            case ID_ROUND_CAMERA_INFO:
                showCameraReport();
                return;
            case ID_GHOST_SILENT:
                showChoice(getString(R.string.GlassgramGhostSilent), ghostSilentNames(), GlassgramConfig.ghostSilentSend, which -> {
                    GlassgramConfig.ghostSilentSend = which;
                    GlassgramConfig.putInt("ghostSilentSend", which);
                });
                return;
            case ID_DOUBLE_TAP_ACTION:
                showChoice(getString(R.string.GlassgramDoubleTapAction), doubleTapNames(), GlassgramConfig.doubleTapAction, which -> {
                    GlassgramConfig.doubleTapAction = which;
                    GlassgramConfig.putInt("doubleTapAction", which);
                });
                return;
            case ID_MESSAGE_MENU:
                presentFragment(new GlassgramSettingsActivity(PAGE_MESSAGE_MENU));
                return;
            case ID_MENU_RESET:
                GlassgramMessageMenu.reset();
                if (listView != null) {
                    listView.adapter.update(true);
                }
                return;
            case ID_CHAT_LOCK:
                presentFragment(new GlassgramSettingsActivity(PAGE_CHAT_LOCK));
                return;
            case ID_LOCK_PIN:
                if (GlassgramChatLock.hasPin()) {
                    GlassgramChatLockUi.requirePin(this, () -> GlassgramChatLockUi.createPin(this, this::onPinChanged), null);
                } else {
                    GlassgramChatLockUi.createPin(this, this::onPinChanged);
                }
                return;
            case ID_LOCK_FINGERPRINT:
                GlassgramChatLock.setUseFingerprint(!GlassgramChatLock.useFingerprint());
                if (listView != null) {
                    listView.adapter.update(true);
                }
                return;
            case ID_LOCK_REMOVE:
                GlassgramChatLockUi.requirePin(this, this::confirmRemovePin, null);
                return;
            case ID_HIDDEN_CHATS:
                GlassgramChatLockUi.openHidden(this);
                return;
            case ID_DOUBLE_TAP_SEEK:
                showChoice(getString(R.string.GlassgramDoubleTapSeek), seekNames(), seekIndex(), which -> {
                    GlassgramConfig.doubleTapSeekSeconds = SEEK_SECONDS[which];
                    GlassgramConfig.putInt("doubleTapSeekSeconds", SEEK_SECONDS[which]);
                });
                return;
            case ID_SAVE_FOLDER:
                showSaveFolderDialog();
                return;
            case ID_TITLE_TEXT:
                showTitleTextDialog();
                return;
            case ID_PROXY_MODE:
                showChoice(getString(R.string.GlassgramProxy), proxyModeNames(), GlassgramConfig.proxyMode, which -> {
                    GlassgramConfig.proxyMode = which;
                    GlassgramConfig.putInt("proxyMode", which);
                    GlassgramProxy.update();
                });
                return;
            case ID_DOWNLOAD_BOOST:
                showChoice(getString(R.string.GlassgramDownloadBoost), boostNames(), GlassgramConfig.downloadBoost, which -> {
                    GlassgramConfig.downloadBoost = which;
                    GlassgramConfig.putInt("downloadBoost", which);
                });
                return;
            case ID_SHOW_ID:
                showChoice(getString(R.string.GlassgramShowId), showIdNames(), GlassgramConfig.showId, which -> {
                    GlassgramConfig.showId = which;
                    GlassgramConfig.putInt("showId", which);
                });
                return;
            case ID_FONT:
                showFontChoice();
                return;
            case ID_TABS:
                presentFragment(new GlassgramSettingsActivity(PAGE_TABS));
                return;
            case ID_TRANSCRIBE_LANG: {
                final String[] langs = org.telegram.messenger.GlassgramVoice.LANGS;
                final String[] names = new String[langs.length];
                int current = 0;
                for (int i = 0; i < langs.length; i++) {
                    names[i] = transcriptionLangName(langs[i]);
                    if (langs[i].equals(GlassgramConfig.offlineTranscriptionLang)) {
                        current = i;
                    }
                }
                showChoice(getString(R.string.GlassgramTranscriptionLanguage), names, current, which -> {
                    GlassgramConfig.offlineTranscriptionLang = langs[which];
                    GlassgramConfig.putString("offlineTranscriptionLang", langs[which]);
                    if (!org.telegram.messenger.GlassgramVoice.isModelReady(langs[which])) {
                        downloadTranscriptionModel();
                    }
                });
                return;
            }
            case ID_TRANSCRIBE_MODEL: {
                final String lang = GlassgramConfig.offlineTranscriptionLang;
                if (org.telegram.messenger.GlassgramVoice.isDownloading(lang)) {
                    org.telegram.messenger.GlassgramVoice.cancelDownload();
                } else if (org.telegram.messenger.GlassgramVoice.isModelReady(lang)) {
                    new AlertDialog.Builder(getParentActivity())
                        .setTitle(getString(R.string.GlassgramTranscriptionDelete))
                        .setMessage(getString(R.string.GlassgramTranscriptionDeleteInfo))
                        .setPositiveButton(getString(R.string.Delete), (d, w) -> {
                            org.telegram.messenger.GlassgramVoice.deleteModel(lang);
                            AndroidUtilities.runOnUIThread(() -> {
                                if (listView != null) listView.adapter.update(true);
                            }, 300);
                        })
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .show();
                } else {
                    downloadTranscriptionModel();
                }
                return;
            }
            case ID_TABS_APPLY:
                restartApp(getParentActivity());
                return;
            case ID_TABS_RESET:
                tabsOrder = null;
                saveTabs("");
                return;
        }
        if (item.id >= ID_TAB && item.id < ID_TAB + GlassgramConfig.TAB_KINDS) {
            toggleTab(item.id - ID_TAB);
            return;
        }
        if (item.id >= ID_MENU_ITEM && item.id < ID_MENU_ITEM + GlassgramMessageMenu.KINDS.length) {
            toggleMenuItem(GlassgramMessageMenu.KINDS[item.id - ID_MENU_ITEM]);
            return;
        }
        if (item.id >= ID_LOCKED_CHAT && item.id < ID_LOCKED_CHAT + lockedChats.size()) {
            final long dialogId = lockedChats.get(item.id - ID_LOCKED_CHAT);
            GlassgramChatLockUi.toggleLock(this, currentAccount, dialogId, () -> {
                if (listView != null) {
                    listView.adapter.update(true);
                }
            });
            return;
        }
        final boolean value;
        switch (item.id) {
            case ID_NUMBER_ROUNDING:
                value = GlassgramConfig.disableNumberRounding = !GlassgramConfig.disableNumberRounding;
                GlassgramConfig.putBoolean("disableNumberRounding", value);
                break;
            case ID_SECONDS:
                value = GlassgramConfig.formatTimeWithSeconds = !GlassgramConfig.formatTimeWithSeconds;
                GlassgramConfig.putBoolean("formatTimeWithSeconds", value);
                LocaleController.getInstance().recreateFormatters();
                break;
            case ID_RELATIVE_LAST_SEEN:
                value = GlassgramConfig.relativeLastSeen = !GlassgramConfig.relativeLastSeen;
                GlassgramConfig.putBoolean("relativeLastSeen", value);
                break;
            case ID_HIDE_PHONE:
                value = GlassgramConfig.hidePhoneNumber = !GlassgramConfig.hidePhoneNumber;
                GlassgramConfig.putBoolean("hidePhoneNumber", value);
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                break;
            case ID_GHOST_READ:
                value = GlassgramConfig.ghostNoReadMessages = !GlassgramConfig.ghostNoReadMessages;
                GlassgramConfig.putBoolean("ghostNoReadMessages", value);
                break;
            case ID_GHOST_STORIES:
                value = GlassgramConfig.ghostNoReadStories = !GlassgramConfig.ghostNoReadStories;
                GlassgramConfig.putBoolean("ghostNoReadStories", value);
                break;
            case ID_GHOST_ONLINE:
                value = GlassgramConfig.ghostNoOnline = !GlassgramConfig.ghostNoOnline;
                GlassgramConfig.putBoolean("ghostNoOnline", value);
                break;
            case ID_GHOST_TYPING:
                value = GlassgramConfig.ghostNoTyping = !GlassgramConfig.ghostNoTyping;
                GlassgramConfig.putBoolean("ghostNoTyping", value);
                break;
            case ID_GHOST_OFFLINE:
                value = GlassgramConfig.ghostAutoOffline = !GlassgramConfig.ghostAutoOffline;
                GlassgramConfig.putBoolean("ghostAutoOffline", value);
                break;
            case ID_GHOST_READ_ON_INTERACT:
                value = GlassgramConfig.ghostReadOnInteract = !GlassgramConfig.ghostReadOnInteract;
                GlassgramConfig.putBoolean("ghostReadOnInteract", value);
                break;
            case ID_GHOST_SCHEDULE:
                value = GlassgramConfig.ghostScheduleMessages = !GlassgramConfig.ghostScheduleMessages;
                GlassgramConfig.putBoolean("ghostScheduleMessages", value);
                break;
            case ID_GHOST_STORY_ALERT:
                value = GlassgramConfig.ghostStoryAlert = !GlassgramConfig.ghostStoryAlert;
                GlassgramConfig.putBoolean("ghostStoryAlert", value);
                break;
            case ID_YANDEX_MAPS:
                value = GlassgramConfig.useYandexMaps = !GlassgramConfig.useYandexMaps;
                GlassgramConfig.putBoolean("useYandexMaps", value);
                break;
            case ID_UPLOAD_BOOST:
                value = GlassgramConfig.uploadBoost = !GlassgramConfig.uploadBoost;
                GlassgramConfig.putBoolean("uploadBoost", value);
                break;
            case ID_HIDE_STORIES:
                value = GlassgramConfig.hideStories = !GlassgramConfig.hideStories;
                GlassgramConfig.putBoolean("hideStories", value);
                getNotificationCenter().postNotificationName(NotificationCenter.storiesUpdated);
                break;
            case ID_GLASS_HEADER:
                value = GlassgramConfig.glassHeader = !GlassgramConfig.glassHeader;
                GlassgramConfig.putBoolean("glassHeader", value);
                // The iOS layout is a way of the glass header: shown with it
                if (listView != null) listView.adapter.update(true);
                break;
            case ID_IOS_CHATS_HEADER:
                value = GlassgramConfig.iosChatsHeader = !GlassgramConfig.iosChatsHeader;
                GlassgramConfig.putBoolean("iosChatsHeader", value);
                break;
            case ID_GLASS_PANELS:
                value = GlassgramConfig.glassPanels = !GlassgramConfig.glassPanels;
                GlassgramConfig.putBoolean("glassPanels", value);
                break;
            case ID_HEADER_BADGE:
                value = GlassgramConfig.headerBadge = !GlassgramConfig.headerBadge;
                GlassgramConfig.putBoolean("headerBadge", value);
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_EMOJI_STATUS);
                break;
            case ID_HIDE_FAB:
                value = GlassgramConfig.hideFloatingButton = !GlassgramConfig.hideFloatingButton;
                GlassgramConfig.putBoolean("hideFloatingButton", value);
                break;
            case ID_SPLIT_INPUT:
                value = GlassgramConfig.splitInput = !GlassgramConfig.splitInput;
                GlassgramConfig.putBoolean("splitInput", value);
                break;
            case ID_IOS_TRANSITIONS:
                value = GlassgramConfig.iosTransitions = !GlassgramConfig.iosTransitions;
                GlassgramConfig.putBoolean("iosTransitions", value);
                break;
            case ID_HIDE_STICKER_TIME:
                value = GlassgramConfig.hideTimeOnStickers = !GlassgramConfig.hideTimeOnStickers;
                GlassgramConfig.putBoolean("hideTimeOnStickers", value);
                break;
            case ID_NO_GREETING:
                value = GlassgramConfig.disableGreetingSticker = !GlassgramConfig.disableGreetingSticker;
                GlassgramConfig.putBoolean("disableGreetingSticker", value);
                break;
            case ID_HIDE_KEYBOARD:
                value = GlassgramConfig.hideKeyboardOnScroll = !GlassgramConfig.hideKeyboardOnScroll;
                GlassgramConfig.putBoolean("hideKeyboardOnScroll", value);
                break;
            case ID_HIDE_SEND_AS:
                value = GlassgramConfig.hideSendAsButton = !GlassgramConfig.hideSendAsButton;
                GlassgramConfig.putBoolean("hideSendAsButton", value);
                break;
            case ID_OFFLINE_TRANSCRIBE:
                value = GlassgramConfig.offlineTranscription = !GlassgramConfig.offlineTranscription;
                GlassgramConfig.putBoolean("offlineTranscription", value);
                if (value && !org.telegram.messenger.GlassgramVoice.isModelReady(GlassgramConfig.offlineTranscriptionLang)) {
                    downloadTranscriptionModel();
                }
                listView.adapter.update(true);
                break;
            case ID_BUTTON_DATA:
                value = GlassgramConfig.inlineButtonData = !GlassgramConfig.inlineButtonData;
                GlassgramConfig.putBoolean("inlineButtonData", value);
                break;
            case ID_MINI_AVATARS:
                value = GlassgramConfig.senderMiniAvatars = !GlassgramConfig.senderMiniAvatars;
                GlassgramConfig.putBoolean("senderMiniAvatars", value);
                break;
            case ID_INPUT_CALCULATOR:
                value = GlassgramConfig.inputCalculator = !GlassgramConfig.inputCalculator;
                GlassgramConfig.putBoolean("inputCalculator", value);
                break;
            case ID_HIDE_ATTACH_CAMERA:
                value = GlassgramConfig.hideAttachCamera = !GlassgramConfig.hideAttachCamera;
                GlassgramConfig.putBoolean("hideAttachCamera", value);
                break;
            case ID_RECENT_STICKERS:
                value = GlassgramConfig.unlimitedRecentStickers = !GlassgramConfig.unlimitedRecentStickers;
                GlassgramConfig.putBoolean("unlimitedRecentStickers", value);
                break;
            case ID_SPY_DELETED:
                value = GlassgramConfig.spySaveDeletedMessages = !GlassgramConfig.spySaveDeletedMessages;
                GlassgramConfig.putBoolean("spySaveDeletedMessages", value); break;
            case ID_SPY_EDITS:
                value = GlassgramConfig.spySaveEditsHistory = !GlassgramConfig.spySaveEditsHistory;
                GlassgramConfig.putBoolean("spySaveEditsHistory", value); break;
            case ID_SPY_BOTS:
                value = GlassgramConfig.spySaveInBotDialogs = !GlassgramConfig.spySaveInBotDialogs;
                GlassgramConfig.putBoolean("spySaveInBotDialogs", value); break;
            case ID_SPY_READ_DATE:
                value = GlassgramConfig.spySaveReadDate = !GlassgramConfig.spySaveReadDate;
                GlassgramConfig.putBoolean("spySaveReadDate", value); break;
            case ID_SPY_LAST_SEEN:
                value = GlassgramConfig.spySaveLastSeenDate = !GlassgramConfig.spySaveLastSeenDate;
                GlassgramConfig.putBoolean("spySaveLastSeenDate", value); break;
            case ID_SPY_ATTACHMENTS:
                value = GlassgramConfig.spySaveAttachments = !GlassgramConfig.spySaveAttachments;
                GlassgramConfig.putBoolean("spySaveAttachments", value); break;
            case ID_CUSTOM_TRANSLUCENT:
                value = GlassgramConfig.spyTranslucentDeleted = !GlassgramConfig.spyTranslucentDeleted;
                GlassgramConfig.putBoolean("spyTranslucentDeleted", value); break;
            case ID_CUSTOM_TRASH:
                value = GlassgramConfig.spyDeletedTrashMark = !GlassgramConfig.spyDeletedTrashMark;
                GlassgramConfig.putBoolean("spyDeletedTrashMark", value); break;
            case ID_CUSTOM_ADS:
                value = GlassgramConfig.spyDisableAds = !GlassgramConfig.spyDisableAds;
                GlassgramConfig.putBoolean("spyDisableAds", value); break;
            case ID_CUSTOM_GHOST_STATUS:
                value = GlassgramConfig.spyDisplayGhostStatus = !GlassgramConfig.spyDisplayGhostStatus;
                GlassgramConfig.putBoolean("spyDisplayGhostStatus", value); break;
            case ID_CUSTOM_SELF_DESTRUCT:
                value = GlassgramConfig.spyDisableSelfDestruct = !GlassgramConfig.spyDisableSelfDestruct;
                GlassgramConfig.putBoolean("spyDisableSelfDestruct", value); break;
            case ID_CONTENT_PROTECTION:
                value = GlassgramConfig.ignoreContentProtection = !GlassgramConfig.ignoreContentProtection;
                GlassgramConfig.putBoolean("ignoreContentProtection", value); break;
            case ID_FILTER_ENABLE:
                value = GlassgramConfig.spyEnableFilters = !GlassgramConfig.spyEnableFilters;
                GlassgramConfig.putBoolean("spyEnableFilters", value); break;
            case ID_FILTER_SHARED:
                value = GlassgramConfig.spyEnableSharedFilters = !GlassgramConfig.spyEnableSharedFilters;
                GlassgramConfig.putBoolean("spyEnableSharedFilters", value); break;
            case ID_FILTER_BLOCKED:
                value = GlassgramConfig.spyHideBlockedUsers = !GlassgramConfig.spyHideBlockedUsers;
                GlassgramConfig.putBoolean("spyHideBlockedUsers", value); break;
            case ID_FILTER_SHADOW:
                value = GlassgramConfig.spyShadowBan = !GlassgramConfig.spyShadowBan;
                GlassgramConfig.putBoolean("spyShadowBan", value); break;
            case ID_ZALGO:
                value = GlassgramConfig.filterZalgo = !GlassgramConfig.filterZalgo;
                GlassgramConfig.putBoolean("filterZalgo", value); break;
            case ID_SHOW_DC:
                value = GlassgramConfig.showDc = !GlassgramConfig.showDc;
                GlassgramConfig.putBoolean("showDc", value); break;
            case ID_SEND_STATS:
                value = GlassgramConfig.sendStats = !GlassgramConfig.sendStats;
                GlassgramConfig.putBoolean("sendStats", value); break;
            case ID_HIDE_REACTIONS_CHANNELS:
                value = GlassgramConfig.hideReactionsChannels = !GlassgramConfig.hideReactionsChannels;
                GlassgramConfig.putBoolean("hideReactionsChannels", value); break;
            case ID_HIDE_REACTIONS_GROUPS:
                value = GlassgramConfig.hideReactionsGroups = !GlassgramConfig.hideReactionsGroups;
                GlassgramConfig.putBoolean("hideReactionsGroups", value); break;
            case ID_HIDE_REACTIONS_PRIVATE:
                value = GlassgramConfig.hideReactionsPrivate = !GlassgramConfig.hideReactionsPrivate;
                GlassgramConfig.putBoolean("hideReactionsPrivate", value); break;
            case ID_MESSAGE_TAIL:
                value = GlassgramConfig.removeMessageTail = !GlassgramConfig.removeMessageTail;
                GlassgramConfig.putBoolean("removeMessageTail", value); break;
            case ID_EDITED_ICON:
                value = GlassgramConfig.editedIcon = !GlassgramConfig.editedIcon;
                GlassgramConfig.putBoolean("editedIcon", value); break;
            case ID_ONLINE_INDICATOR:
                value = GlassgramConfig.showOnlineIndicator = !GlassgramConfig.showOnlineIndicator;
                GlassgramConfig.putBoolean("showOnlineIndicator", value); break;
            case ID_FORWARD_COUNT:
                value = GlassgramConfig.showForwardCount = !GlassgramConfig.showForwardCount;
                GlassgramConfig.putBoolean("showForwardCount", value); break;
            case ID_HIDE_SHARE:
                value = GlassgramConfig.hideShareButton = !GlassgramConfig.hideShareButton;
                GlassgramConfig.putBoolean("hideShareButton", value); break;
            case ID_POLL_RESULTS:
                value = GlassgramConfig.showPollResults = !GlassgramConfig.showPollResults;
                GlassgramConfig.putBoolean("showPollResults", value); break;
            case ID_COMMA_MENTION:
                value = GlassgramConfig.commaAfterMention = !GlassgramConfig.commaAfterMention;
                GlassgramConfig.putBoolean("commaAfterMention", value); break;
            case ID_ORIGINAL_QUALITY:
                value = GlassgramConfig.preferOriginalQuality = !GlassgramConfig.preferOriginalQuality;
                GlassgramConfig.putBoolean("preferOriginalQuality", value); break;
            case ID_VOLUME_UNMUTE:
                value = GlassgramConfig.volumeUnmute = !GlassgramConfig.volumeUnmute;
                GlassgramConfig.putBoolean("volumeUnmute", value); break;
            case ID_AUTO_PAUSE_VIDEO:
                value = GlassgramConfig.autoPauseVideo = !GlassgramConfig.autoPauseVideo;
                GlassgramConfig.putBoolean("autoPauseVideo", value); break;
            case ID_AUTO_PAUSE_VOICE:
                value = GlassgramConfig.autoPauseVoice = !GlassgramConfig.autoPauseVoice;
                GlassgramConfig.putBoolean("autoPauseVoice", value); break;
            case ID_AUTO_PAUSE_ROUND:
                value = GlassgramConfig.autoPauseRound = !GlassgramConfig.autoPauseRound;
                GlassgramConfig.putBoolean("autoPauseRound", value); break;
            case ID_ROUND_60FPS:
                value = GlassgramConfig.roundFps60 = !GlassgramConfig.roundFps60;
                GlassgramConfig.putBoolean("roundFps60", value); break;
            case ID_ROUND_STABILIZATION:
                value = GlassgramConfig.roundStabilization = !GlassgramConfig.roundStabilization;
                GlassgramConfig.putBoolean("roundStabilization", value); break;
            case ID_ROUND_HORIZON:
                value = GlassgramConfig.roundHorizon = !GlassgramConfig.roundHorizon;
                GlassgramConfig.putBoolean("roundHorizon", value); break;
            case ID_ROUND_HIGH_QUALITY:
                value = GlassgramConfig.roundHighQuality = !GlassgramConfig.roundHighQuality;
                GlassgramConfig.putBoolean("roundHighQuality", value); break;
            case ID_ROUND_WIDE:
                value = GlassgramConfig.roundWideCamera = !GlassgramConfig.roundWideCamera;
                GlassgramConfig.putBoolean("roundWideCamera", value);
                if (value) {
                    // a hidden lens that failed before gets another try
                    org.telegram.messenger.camera.Camera2Session.resetUnavailableCameras();
                }
                break;
            case ID_ROUND_KEEP_ZOOM:
                value = GlassgramConfig.roundKeepZoom = !GlassgramConfig.roundKeepZoom;
                GlassgramConfig.putBoolean("roundKeepZoom", value); break;
            case ID_ALWAYS_HD:
                // Telegram's own default for the HD toggle in the photo viewer
                value = org.telegram.messenger.SharedConfig.photoHighQualityDefault = !org.telegram.messenger.SharedConfig.photoHighQualityDefault;
                org.telegram.messenger.ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", android.app.Activity.MODE_PRIVATE)
                    .edit().putBoolean("photoHighQualityDefault", value).apply();
                break;
            default:
                return;
        }
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(value);
        }
        if (view instanceof org.telegram.ui.Cells.NotificationsCheckCell) {
            ((org.telegram.ui.Cells.NotificationsCheckCell) view).setChecked(value);
        }
        if (view instanceof CheckBoxCell) {
            ((CheckBoxCell) view).setChecked(value, true);
        }
        if (page == PAGE_GHOST || item.id == ID_CUSTOM_TRASH
                || item.id == ID_HIDE_REACTIONS_CHANNELS || item.id == ID_HIDE_REACTIONS_GROUPS || item.id == ID_HIDE_REACTIONS_PRIVATE
                || item.id == ID_AUTO_PAUSE_VIDEO || item.id == ID_AUTO_PAUSE_VOICE || item.id == ID_AUTO_PAUSE_ROUND
                || item.id == ID_ROUND_STABILIZATION) {
            // The Ghost Mode counter and the mark colors row follow these switches
            if (listView != null) listView.adapter.update(true);
        }
        if (item.id == ID_CUSTOM_TRANSLUCENT || item.id == ID_CUSTOM_TRASH) {
            updatePreview();
        }
        if (item.id == ID_HIDE_STICKER_TIME) {
            updateStickerPreview();
        }
        // Screens below show the change when they are rebuilt
        if (parentLayout != null) {
            parentLayout.rebuildAllFragmentViews(false, false);
        }
    }

    private void onTeamSignedIn() {
        if (listView != null) {
            listView.adapter.update(true);
        }
        final GlassgramApi.Session session = GlassgramApi.getSession(currentAccount);
        if (session == null) {
            return;
        }
        if (session.isStaff()) {
            presentFragment(new GlassgramDeveloperActivity());
        } else if (getParentActivity() != null) {
            BulletinFactory.of(this).createErrorBulletin(LocaleController.formatString(R.string.GlassgramApiNoRights, "@" + GlassgramApi.BOT_USERNAME)).show();
        }
    }

    private static final int[] SEEK_SECONDS = {5, 10, 15, 30};

    private static String[] seekNames() {
        final String[] names = new String[SEEK_SECONDS.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = LocaleController.formatPluralString("Seconds", SEEK_SECONDS[i]);
        }
        return names;
    }

    private static int seekIndex() {
        for (int i = 0; i < SEEK_SECONDS.length; i++) {
            if (SEEK_SECONDS[i] == GlassgramConfig.doubleTapSeekSeconds) {
                return i;
            }
        }
        return 1;
    }

    private static String[] stickerShapeNames() {
        return new String[]{getString(R.string.GlassgramStickerShapeDefault), getString(R.string.GlassgramStickerShapeRounded), getString(R.string.GlassgramStickerShapeMessage)};
    }

    private static String saveFolderName() {
        return GlassgramConfig.saveFolder == null || GlassgramConfig.saveFolder.isEmpty() ? "glassGram" : GlassgramConfig.saveFolder;
    }

    /** Switches with an expandable list: the switch toggles, the row itself expands. */
    private void toggleSwitch(String key) {
        final boolean value;
        if ("hideReactions".equals(key)) {
            value = GlassgramConfig.hideReactions = !GlassgramConfig.hideReactions;
        } else {
            value = GlassgramConfig.autoPause = !GlassgramConfig.autoPause;
        }
        GlassgramConfig.putBoolean(key, value);
        if (listView != null) listView.adapter.update(true);
        rebuildChats();
    }

    private void rebuildChats() {
        if (parentLayout != null) {
            parentLayout.rebuildAllFragmentViews(false, false);
        }
    }

    // Screens below are rebuilt once the slider or the tiles settle, not on every step; also after leaving here
    private Runnable rebuildChatsLater;

    private void scheduleRebuildChats() {
        if (rebuildChatsLater != null) {
            AndroidUtilities.cancelRunOnUIThread(rebuildChatsLater);
        }
        final org.telegram.ui.ActionBar.INavigationLayout layout = parentLayout;
        rebuildChatsLater = () -> {
            if (layout != null) {
                layout.rebuildAllFragmentViews(false, false);
            }
        };
        AndroidUtilities.runOnUIThread(rebuildChatsLater, 400);
    }

    private void showSaveFolderDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final org.telegram.ui.Components.EditTextBoldCursor editText = new org.telegram.ui.Components.EditTextBoldCursor(getParentActivity());
        editText.setText(GlassgramConfig.saveFolder);
        editText.setHint("glassGram");
        editText.setSingleLine(true);
        editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(getThemedColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        editText.setHintTextColor(getThemedColor(org.telegram.ui.ActionBar.Theme.key_dialogTextHint));
        editText.setCursorColor(getThemedColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        editText.setBackground(null);
        final android.widget.FrameLayout container = new android.widget.FrameLayout(getParentActivity());
        container.addView(editText, org.telegram.ui.Components.LayoutHelper.createFrame(org.telegram.ui.Components.LayoutHelper.MATCH_PARENT, 36, android.view.Gravity.TOP, 24, 8, 24, 0));
        new AlertDialog.Builder(getParentActivity())
            .setTitle(getString(R.string.GlassgramSaveFolder))
            .setView(container)
            .setPositiveButton(getString(R.string.OK), (d, w) -> {
                // A folder name only: no separators, nothing that leaves the media folders
                final String name = editText.getText().toString().trim().replaceAll("[\\\\/:*?\"<>|]", "").replace("..", "");
                GlassgramConfig.saveFolder = name;
                GlassgramConfig.putString("saveFolder", name);
                if (listView != null) listView.adapter.update(true);
            })
            .setNegativeButton(getString(R.string.Cancel), null)
            .show();
    }

    private static int ghostOptionsEnabled() {
        return (GlassgramConfig.ghostNoReadMessages ? 1 : 0) + (GlassgramConfig.ghostNoReadStories ? 1 : 0)
                + (GlassgramConfig.ghostNoOnline ? 1 : 0) + (GlassgramConfig.ghostNoTyping ? 1 : 0)
                + (GlassgramConfig.ghostAutoOffline ? 1 : 0);
    }

    private void toggleGhostMode() {
        GlassgramConfig.setGhostMode(!GlassgramConfig.ghostMode);
        if (listView != null) listView.adapter.update(true);
        if (parentLayout != null) {
            parentLayout.rebuildAllFragmentViews(false, false);
        }
    }

    /** "Send without sound", in GlassgramConfig.SILENT_*'s order. */
    private static String[] ghostSilentNames() {
        return new String[]{getString(R.string.GlassgramGhostSilentNever), getString(R.string.GlassgramGhostSilentInGhost), getString(R.string.GlassgramGhostSilentAlways)};
    }

    private static String[] folderSizeNames() {
        return new String[]{"300 MB", "1 GB", "2 GB", "5 GB", "16 GB", getString(R.string.GlassgramSpyNoLimit)};
    }

    /** "Sticker Size" with the value, Small and Large, and the slider. */
    private View getStickerSizeView() {
        if (stickerSizeView == null) {
            stickerSizeView = new GlassgramSettingsCells.SizeSlider(getContext(), getString(R.string.GlassgramStickerSize),
                getString(R.string.GlassgramStickerSizeSmall), getString(R.string.GlassgramStickerSizeLarge),
                GlassgramConfig.STICKER_SIZE_MIN, GlassgramConfig.stickerSize, GlassgramConfig.STICKER_SIZE_MAX, val -> {
                    GlassgramConfig.stickerSize = val;
                    GlassgramConfig.putInt("stickerSize", val);
                    updateStickerPreview();
                    scheduleRebuildChats();
                });
        }
        return stickerSizeView;
    }

    /** Three tiles: the sticker shapes, the chosen one framed. */
    private View getStickerShapeView() {
        if (stickerShapeView == null) {
            stickerShapeView = new GlassgramSettingsCells.ShapePicker(getContext(), stickerShapeNames(), GlassgramConfig.stickerShape, which -> {
                GlassgramConfig.stickerShape = which;
                GlassgramConfig.putInt("stickerShape", which);
                updateStickerPreview();
                scheduleRebuildChats();
            });
        }
        return stickerShapeView;
    }

    /** A sticker the user has: a recent or favorite one, the greeting one, or the first of an installed set. */
    private TLRPC.Document getPreviewSticker() {
        final org.telegram.messenger.MediaDataController data = getMediaDataController();
        for (int type : new int[]{org.telegram.messenger.MediaDataController.TYPE_IMAGE, org.telegram.messenger.MediaDataController.TYPE_FAVE}) {
            final ArrayList<TLRPC.Document> recent = data.getRecentStickersNoCopy(type);
            if (recent != null) {
                for (TLRPC.Document document : recent) {
                    if (document != null && MessageObject.isStickerDocument(document)) {
                        return document;
                    }
                }
            }
        }
        final TLRPC.Document greeting = data.getGreetingsSticker();
        if (greeting != null) {
            return greeting;
        }
        final ArrayList<TLRPC.TL_messages_stickerSet> sets = data.getStickerSets(org.telegram.messenger.MediaDataController.TYPE_IMAGE);
        if (sets != null) {
            for (TLRPC.TL_messages_stickerSet set : sets) {
                if (set != null && set.documents != null && !set.documents.isEmpty()) {
                    return set.documents.get(0);
                }
            }
        }
        return null;
    }

    private TLRPC.Message previewMessage(int id, boolean out, String text, int date) {
        final TLRPC.Message message = new TLRPC.TL_message();
        message.message = text;
        message.date = date;
        message.dialog_id = 1;
        message.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        message.from_id = new TLRPC.TL_peerUser();
        message.id = id;
        message.media = new TLRPC.TL_messageMediaEmpty();
        message.out = out;
        message.peer_id = new TLRPC.TL_peerUser();
        if (out) {
            message.from_id.user_id = getUserConfig().getClientUserId();
        } else {
            message.peer_id.user_id = getUserConfig().getClientUserId();
        }
        return message;
    }

    /** The chat wallpaper with a message, the user's sticker in the current size and shape, and a reply to it. */
    private View getStickerPreviewView() {
        if (stickerPreviewView != null) {
            return stickerPreviewView;
        }
        final Context context = getContext();
        stickerPreviewView = new GlassgramWallpaperLayout(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return true;
            }
        };
        stickerPreviewView.setPadding(0, dp(10), 0, dp(10));
        final LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        stickerPreviewView.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final int date = (int) (System.currentTimeMillis() / 1000) - 600;
        final ArrayList<MessageObject> messages = new ArrayList<>();
        messages.add(new MessageObject(currentAccount, previewMessage(1, false, getString(R.string.GlassgramStickerPreviewLine1), date), true, false));
        final TLRPC.Document sticker = getPreviewSticker();
        if (sticker != null) {
            final TLRPC.Message stickerMessage = previewMessage(2, true, "", date + 60);
            stickerMessage.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;
            stickerMessage.media = new TLRPC.TL_messageMediaDocument();
            stickerMessage.media.flags |= 1;
            stickerMessage.media.document = sticker;
            final MessageObject stickerObject = new MessageObject(currentAccount, stickerMessage, true, false);
            messages.add(stickerObject);

            final TLRPC.Message reply = previewMessage(3, false, getString(R.string.GlassgramStickerPreviewLine2), date + 120);
            reply.reply_to = new TLRPC.TL_messageReplyHeader();
            reply.reply_to.flags |= 16;
            reply.reply_to.reply_to_msg_id = 2;
            final MessageObject replyObject = new MessageObject(currentAccount, reply, true, false);
            replyObject.replyMessageObject = stickerObject;
            messages.add(replyObject);
        }
        stickerPreviewCells.clear();
        for (MessageObject messageObject : messages) {
            messageObject.eventId = 1;
            messageObject.resetLayout();
            final ChatMessageCell cell = new ChatMessageCell(context, currentAccount);
            cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
            });
            cell.isChat = false;
            cell.setFullyDraw(true);
            cell.setMessageObject(messageObject, null, false, false, false);
            column.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            stickerPreviewCells.add(cell);
        }
        return stickerPreviewView;
    }

    private void updateStickerPreview() {
        for (ChatMessageCell cell : stickerPreviewCells) {
            final MessageObject messageObject = cell.getMessageObject();
            if (messageObject == null) {
                continue;
            }
            messageObject.forceUpdate = true;
            cell.setMessageObject(messageObject, null, false, false, false);
            messageObject.forceUpdate = false;
            cell.requestLayout();
            cell.invalidate();
        }
    }

    /** A deleted incoming message on the chat wallpaper, drawn with the current mark settings. */
    private View getPreviewView() {
        if (previewView != null) {
            return previewView;
        }
        Context context = getContext();
        previewView = new GlassgramWallpaperLayout(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return true;
            }
        };
        previewView.setPadding(0, dp(12), 0, dp(12));

        TLRPC.Message message = new TLRPC.TL_message();
        message.message = getString(R.string.GlassgramPreviewText);
        message.date = (int) (System.currentTimeMillis() / 1000) - 60;
        message.dialog_id = 1;
        message.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        message.from_id = new TLRPC.TL_peerUser();
        message.id = 1;
        message.media = new TLRPC.TL_messageMediaEmpty();
        message.out = false;
        message.peer_id = new TLRPC.TL_peerUser();
        message.peer_id.user_id = getUserConfig().getClientUserId();
        previewMessage = new MessageObject(currentAccount, message, true, false);
        previewMessage.eventId = 1;
        previewMessage.resetLayout();

        previewCell = new ChatMessageCell(context, currentAccount);
        previewCell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
        });
        previewCell.isChat = false;
        previewCell.glassgramForceDeleted = true;
        previewCell.setFullyDraw(true);
        previewCell.setMessageObject(previewMessage, null, false, false, false);
        previewView.addView(previewCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return previewView;
    }

    private void updatePreview() {
        if (previewCell == null || previewMessage == null) {
            return;
        }
        previewMessage.forceUpdate = true;
        previewCell.setMessageObject(previewMessage, null, false, false, false);
        previewMessage.forceUpdate = false;
        previewCell.requestLayout();
        previewCell.invalidate();
    }

    /** A row of colors for the trash mark; the first one keeps the time color. */
    private View getColorsView() {
        if (colorsView != null) {
            return colorsView;
        }
        colorsView = new View(getContext()) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(64), MeasureSpec.EXACTLY));
            }

            private float step() {
                return (getMeasuredWidth() - dp(32)) / (float) MARK_COLORS.length;
            }

            @Override
            protected void onDraw(Canvas canvas) {
                float step = step();
                float radius = Math.min(step / 2f - dp(4), dp(19));
                float cy = getMeasuredHeight() / 2f;
                for (int i = 0; i < MARK_COLORS.length; i++) {
                    float cx = dp(16) + step * i + step / 2f;
                    int color = MARK_COLORS[i] == 0 ? Theme.getColor(Theme.key_windowBackgroundWhiteBlackText) : MARK_COLORS[i];
                    boolean selected = GlassgramConfig.spyDeletedMarkColor == MARK_COLORS[i];
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(color);
                    canvas.drawCircle(cx, cy, selected ? radius - dp(6) : radius, paint);
                    if (selected) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(dp(2.5f));
                        canvas.drawCircle(cx, cy, radius - dp(1.25f), paint);
                    }
                }
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    int index = (int) ((event.getX() - dp(16)) / step());
                    if (index >= 0 && index < MARK_COLORS.length) {
                        GlassgramConfig.spyDeletedMarkColor = MARK_COLORS[index];
                        GlassgramConfig.putInt("spyDeletedMarkColor", MARK_COLORS[index]);
                        invalidate();
                        updatePreview();
                    }
                    return true;
                }
                return super.onTouchEvent(event);
            }
        };
        return colorsView;
    }

    /** App icon, name and version at the top of the main page. */
    private View getAboutHeader() {
        if (aboutHeader != null) {
            return aboutHeader;
        }
        Context context = getContext();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(0, dp(8), 0, dp(20));

        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            icon.setImageDrawable(context.getPackageManager().getApplicationIcon(context.getPackageName()));
        } catch (Exception e) {
            icon.setImageResource(R.mipmap.ic_launcher);
        }
        icon.setClipToOutline(true);
        icon.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(26));
            }
        });
        layout.addView(icon, LayoutHelper.createLinear(96, 96, Gravity.CENTER_HORIZONTAL));

        TextView name = new TextView(context);
        name.setText(getString(R.string.AppName));
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        name.setGravity(Gravity.CENTER);
        layout.addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

        TextView version = new TextView(context);
        version.setText(getVersionText(context));
        version.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        version.setTypeface(AndroidUtilities.bold());
        version.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        version.setGravity(Gravity.CENTER);
        layout.addView(version, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

        aboutHeader = layout;
        return aboutHeader;
    }

    private static String getVersionText(Context context) {
        try {
            android.content.pm.PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName + " (" + info.versionCode + ")";
        } catch (Exception e) {
            return org.telegram.messenger.BuildVars.BUILD_VERSION_STRING;
        }
    }

    private static String[] boostNames() {
        return new String[] { getString(R.string.GlassgramBoostOff), getString(R.string.GlassgramBoostFast), getString(R.string.GlassgramBoostUltra) };
    }

    private static String[] showIdNames() {
        return new String[] { getString(R.string.GlassgramShowIdOff), "Telegram API", "Bot API" };
    }

    /** What the cameras offer video messages; built off the main thread, as it asks the camera service about many ids. */
    private void showCameraReport() {
        final Context context = getParentActivity();
        if (context == null) return;
        final int size = GlassgramConfig.roundVideoSize(MessagesController.getInstance(currentAccount).roundVideoSize);
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            final String report = org.telegram.messenger.camera.CameraReport.build(context.getApplicationContext(), size);
            AndroidUtilities.runOnUIThread(() -> {
                if (getParentActivity() == null) return;
                final TextView text = new TextView(context);
                text.setText(report);
                text.setTextIsSelectable(true);
                text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                text.setTypeface(android.graphics.Typeface.MONOSPACE);
                text.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                text.setPadding(dp(24), dp(4), dp(24), dp(4));
                final android.widget.ScrollView scroll = new android.widget.ScrollView(context);
                scroll.addView(text);
                final FrameLayout container = new FrameLayout(context);
                container.addView(scroll, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (int) (AndroidUtilities.displaySize.y * 0.55f)));
                new AlertDialog.Builder(context)
                        .setTitle(getString(R.string.GlassgramRoundCameraInfo))
                        .setView(container)
                        .setPositiveButton(getString(R.string.Copy), (d, w) -> AndroidUtilities.addToClipboard(report))
                        .setNegativeButton(getString(R.string.Close), null)
                        .show();
            });
        });
    }

    // Double tap, message menu, chat lock

    private int menuReorderId = -1;
    private final ArrayList<Long> lockedChats = new ArrayList<>();

    /** In GlassgramConfig.DOUBLE_TAP_*'s order. */
    private static String[] doubleTapNames() {
        return new String[]{
                getString(R.string.GlassgramDoubleTapReaction),
                getString(R.string.Reply),
                getString(R.string.Copy),
                getString(R.string.TranslateMessage),
                getString(R.string.Edit),
                getString(R.string.GlassgramDoubleTapSave),
                getString(R.string.GlassgramDoubleTapNothing),
        };
    }

    private static int kindIndex(GlassgramMessageMenu.Kind kind) {
        for (int i = 0; i < GlassgramMessageMenu.KINDS.length; i++) {
            if (GlassgramMessageMenu.KINDS[i] == kind) {
                return i;
            }
        }
        return 0;
    }

    private void toggleMenuItem(GlassgramMessageMenu.Kind kind) {
        final java.util.Set<String> hidden = GlassgramMessageMenu.hiddenKeys();
        if (!hidden.remove(kind.key)) {
            hidden.add(kind.key);
        }
        GlassgramMessageMenu.save(GlassgramMessageMenu.orderedKinds(), hidden);
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    private void onMenuReordered(int id, ArrayList<UItem> items) {
        if (id != menuReorderId) {
            return;
        }
        final ArrayList<GlassgramMessageMenu.Kind> order = new ArrayList<>();
        for (UItem item : items) {
            if (item.id >= ID_MENU_ITEM && item.id < ID_MENU_ITEM + GlassgramMessageMenu.KINDS.length) {
                order.add(GlassgramMessageMenu.KINDS[item.id - ID_MENU_ITEM]);
            }
        }
        if (order.size() == GlassgramMessageMenu.KINDS.length) {
            GlassgramMessageMenu.save(order, GlassgramMessageMenu.hiddenKeys());
        }
    }

    private void onPinChanged() {
        BulletinFactory.of(this).createSimpleBulletin(R.raw.passcode_lock_close, getString(R.string.GlassgramChatLockPinSaved)).show();
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    private void confirmRemovePin() {
        if (getParentActivity() == null) {
            return;
        }
        final AlertDialog dialog = new AlertDialog.Builder(getParentActivity())
                .setTitle(getString(R.string.GlassgramChatLockRemovePin))
                .setMessage(getString(R.string.GlassgramChatLockRemovePinInfo))
                .setPositiveButton(getString(R.string.Delete), (d, w) -> {
                    GlassgramChatLock.removePin();
                    if (listView != null) {
                        listView.adapter.update(true);
                    }
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .create();
        showDialog(dialog);
        final View button = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (button instanceof android.widget.TextView) {
            ((android.widget.TextView) button).setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    private void showChoice(CharSequence title, String[] names, int current, org.telegram.messenger.Utilities.Callback<Integer> onChoose) {
        if (getParentActivity() == null) {
            return;
        }
        final CharSequence[] items = new CharSequence[names.length];
        for (int i = 0; i < names.length; i++) {
            items[i] = (i == current ? "✓ " : "") + names[i];
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setItems(items, (dialog, which) -> {
            onChoose.run(which);
            if (listView != null) {
                listView.adapter.update(true);
            }
            if (parentLayout != null) {
                parentLayout.rebuildAllFragmentViews(false, false);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    /** The team's proxy settings, in GlassgramProxy's modes' order. */
    private static String[] proxyModeNames() {
        return new String[]{getString(R.string.GlassgramProxyAuto), getString(R.string.GlassgramProxyAlways), getString(R.string.GlassgramProxyOff)};
    }

    /** How the app reaches Telegram now. */
    private static String proxyStatus() {
        if (GlassgramProxy.count() == 0) {
            return getString(R.string.GlassgramProxyStatusNone);
        }
        if (GlassgramProxy.isTunnel()) {
            return getString(R.string.GlassgramProxyStatusTunnel);
        }
        if (GlassgramProxy.activeProxy() != null) {
            return getString(R.string.GlassgramProxyStatusTeam);
        }
        if (SharedConfig.isProxyEnabled()) {
            return getString(R.string.GlassgramProxyStatusOwn);
        }
        return getString(GlassgramProxy.vpnActive() ? R.string.GlassgramProxyStatusVpn : R.string.GlassgramProxyStatusDirect);
    }

    private static String fontName(String id) {
        final String name = GlassgramFonts.name(id);
        return name != null ? name : getString(R.string.GlassgramFontSystem);
    }

    /**
     * The fonts, each shown in itself, then loading one's own and deleting it; the chosen one is
     * taken at the next start.
     */
    private void showFontChoice() {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final String[] ids = GlassgramFonts.ids();
        final boolean hasCustom = !GlassgramFonts.customIds().isEmpty();
        final CharSequence[] items = new CharSequence[ids.length + (hasCustom ? 2 : 1)];
        for (int i = 0; i < ids.length; i++) {
            final SpannableStringBuilder text = new SpannableStringBuilder(fontName(ids[i]));
            final android.graphics.Typeface typeface = GlassgramFonts.preview(ids[i]);
            if (typeface != null) {
                text.setSpan(new org.telegram.ui.Components.TypefaceSpan(typeface), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (ids[i].equals(GlassgramConfig.font)) {
                text.insert(0, "✓ ");
            }
            items[i] = text;
        }
        items[ids.length] = getString(R.string.GlassgramFontImport);
        if (hasCustom) {
            items[ids.length + 1] = getString(R.string.GlassgramFontDelete);
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.GlassgramFont));
        builder.setItems(items, (dialog, which) -> {
            if (which == ids.length) {
                try {
                    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                            .addCategory(Intent.CATEGORY_OPENABLE), REQUEST_FONT_IMPORT);
                } catch (Exception e) {
                    org.telegram.messenger.FileLog.e(e);
                }
            } else if (which > ids.length) {
                showFontDelete();
            } else {
                chooseFont(ids[which]);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void chooseFont(String id) {
        final Context context = getParentActivity();
        if (context == null || id.equals(GlassgramConfig.font)) {
            return;
        }
        GlassgramConfig.font = id;
        // written at once: the restart may follow right away
        GlassgramConfig.putStringNow("font", id);
        if (listView != null) {
            listView.adapter.update(true);
        }
        askRestart(context);
    }

    private void showFontDelete() {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final java.util.ArrayList<String> ids = GlassgramFonts.customIds();
        final CharSequence[] items = new CharSequence[ids.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = fontName(ids.get(i));
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.GlassgramFontDelete));
        builder.setItems(items, (dialog, which) -> {
            final String id = ids.get(which);
            GlassgramFonts.deleteFont(id);
            if (id.equals(GlassgramConfig.font)) {
                // the deleted font is the one in use: back to the standard one at the next start
                chooseFont(GlassgramFonts.SYSTEM);
            } else if (listView != null) {
                listView.adapter.update(true);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    // The capsule's tabs as on this page: every kind in its order, and which of them are shown
    private int[] tabsOrder;
    private final boolean[] tabsOn = new boolean[GlassgramConfig.TAB_KINDS];
    private int tabsReorderId = -1;
    private boolean tabsChanged;

    /** A switch with its description under it, shown whole however long. */
    private static UItem buttonCheck(int id, CharSequence text, CharSequence subtext) {
        return UItem.asButtonCheck(id, text, subtext).wrapText();
    }

    private void loadTabs() {
        if (tabsOrder != null) {
            return;
        }
        int[] shown = GlassgramConfig.mainTabs();
        if (shown == null) {
            // Telegram's own: chats, contacts, calls or settings, profile
            shown = new int[]{GlassgramConfig.TAB_CHATS, GlassgramConfig.TAB_CONTACTS,
                getUserConfig().showCallsTab ? GlassgramConfig.TAB_CALLS : GlassgramConfig.TAB_SETTINGS, GlassgramConfig.TAB_PROFILE};
        }
        tabsOrder = new int[GlassgramConfig.TAB_KINDS];
        java.util.Arrays.fill(tabsOn, false);
        int count = 0;
        for (int tab : shown) {
            tabsOrder[count++] = tab;
            tabsOn[tab] = true;
        }
        for (int tab = 0; tab < GlassgramConfig.TAB_KINDS; tab++) {
            if (!tabsOn[tab]) {
                tabsOrder[count++] = tab;
            }
        }
    }

    private static String tabName(int tab) {
        switch (tab) {
            case GlassgramConfig.TAB_CHATS: return getString(R.string.MainTabsChats);
            case GlassgramConfig.TAB_CONTACTS: return getString(R.string.MainTabsContacts);
            case GlassgramConfig.TAB_SETTINGS: return getString(R.string.Settings);
            case GlassgramConfig.TAB_CALLS: return getString(R.string.MainTabsCalls);
            default: return getString(R.string.MainTabsProfile);
        }
    }

    private String tabsSummary() {
        final int[] shown = GlassgramConfig.mainTabs();
        return shown == null ? getString(R.string.GlassgramTabsDefault) : String.valueOf(shown.length);
    }

    private void toggleTab(int tab) {
        loadTabs();
        if (tab == GlassgramConfig.TAB_CHATS) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.GlassgramTabsChatsStay)).show();
            return;
        }
        int shown = 0;
        for (boolean on : tabsOn) {
            shown += on ? 1 : 0;
        }
        if (tabsOn[tab] && shown <= 2) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.GlassgramTabsMin)).show();
            return;
        }
        // the settings are reached from their tab or from the profile's
        if (tabsOn[tab] && (tab == GlassgramConfig.TAB_SETTINGS && !tabsOn[GlassgramConfig.TAB_PROFILE]
                || tab == GlassgramConfig.TAB_PROFILE && !tabsOn[GlassgramConfig.TAB_SETTINGS])) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.GlassgramTabsSettingsStay)).show();
            return;
        }
        tabsOn[tab] = !tabsOn[tab];
        saveTabs(null);
    }

    private void onTabsReordered(int id, ArrayList<UItem> items) {
        if (id != tabsReorderId || tabsOrder == null) {
            return;
        }
        int count = 0;
        for (UItem item : items) {
            if (item.id >= ID_TAB && item.id < ID_TAB + GlassgramConfig.TAB_KINDS && count < tabsOrder.length) {
                tabsOrder[count++] = item.id - ID_TAB;
            }
        }
        saveTabs(null);
    }

    /** Writes the tabs shown in their order, or value as it is ("" for Telegram's own), and remakes the capsule under these pages with them. */
    private void saveTabs(String value) {
        if (value == null) {
            final StringBuilder sb = new StringBuilder();
            for (int tab : tabsOrder) {
                if (tabsOn[tab]) {
                    sb.append(tab);
                }
            }
            value = sb.toString();
        }
        GlassgramConfig.mainTabs = value;
        // written at once: the restart may follow right away
        GlassgramConfig.putStringNow("mainTabs", value);
        // where it can't be remade (tablets), the restart applies them
        tabsChanged = !MainTabsActivity.rebuildUnder(getParentLayout());
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    private static String transcriptionLangName(String lang) {
        return org.telegram.messenger.GlassgramVoice.LANG_EN.equals(lang) ? "English" : "Русский";
    }

    private String transcriptionModelText() {
        final String lang = GlassgramConfig.offlineTranscriptionLang;
        if (org.telegram.messenger.GlassgramVoice.isDownloading(lang)) {
            return getString(R.string.GlassgramTranscriptionDownloading);
        }
        return getString(org.telegram.messenger.GlassgramVoice.isModelReady(lang) ? R.string.GlassgramTranscriptionModelReady : R.string.GlassgramTranscriptionDownload);
    }

    private String transcriptionModelValue() {
        final String lang = GlassgramConfig.offlineTranscriptionLang;
        if (org.telegram.messenger.GlassgramVoice.isDownloading(lang)) {
            return (int) (org.telegram.messenger.GlassgramVoice.downloadProgress() * 100) + "%";
        }
        if (org.telegram.messenger.GlassgramVoice.isModelReady(lang)) {
            return AndroidUtilities.formatFileSize(org.telegram.messenger.GlassgramVoice.modelSize(lang));
        }
        return "~45 MB";
    }

    private void downloadTranscriptionModel() {
        final String lang = GlassgramConfig.offlineTranscriptionLang;
        org.telegram.messenger.GlassgramVoice.download(lang, new org.telegram.messenger.GlassgramVoice.DownloadListener() {
            @Override
            public void onProgress(long done, long total) {
                if (listView != null) {
                    listView.adapter.update(false);
                }
            }

            @Override
            public void onDone(boolean ok) {
                if (listView != null) {
                    listView.adapter.update(true);
                }
                if (!ok && !isFinishing() && getParentActivity() != null && !org.telegram.messenger.GlassgramVoice.isModelReady(lang)) {
                    BulletinFactory.of(GlassgramSettingsActivity.this).createErrorBulletin(getString(R.string.GlassgramTranscriptionDownloadFailed)).show();
                } else if (ok && getParentActivity() != null) {
                    BulletinFactory.of(GlassgramSettingsActivity.this).createSimpleBulletin(R.raw.done, getString(R.string.GlassgramTranscriptionModelDone)).show();
                }
            }
        });
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    private void askRestart(Context context) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.GlassgramFont));
        builder.setMessage(getString(R.string.GlassgramFontRestart));
        builder.setPositiveButton(getString(R.string.GlassgramFontRestartNow), (d, w) -> restartApp(context));
        builder.setNegativeButton(getString(R.string.GlassgramFontRestartLater), null);
        builder.show();
    }

    /** Starts the app anew: the font is set as the process starts. */
    private static void restartApp(Context context) {
        try {
            final Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (launch == null || launch.getComponent() == null) {
                return;
            }
            context.startActivity(Intent.makeRestartActivityTask(launch.getComponent()));
            AndroidUtilities.runOnUIThread(() -> Runtime.getRuntime().exit(0), 300);
        } catch (Exception e) {
            org.telegram.messenger.FileLog.e(e);
        }
    }

    private void showTitleTextDialog() {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHint(getString(R.string.AppName));
        editText.setText(GlassgramConfig.titleText);
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setLineColors(Theme.getColor(Theme.key_dialogInputField), Theme.getColor(Theme.key_dialogInputFieldActivated), Theme.getColor(Theme.key_text_RedBold));
        editText.setPadding(0, dp(8), 0, dp(8));

        final FrameLayout container = new FrameLayout(context);
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 4, 24, 0));

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.GlassgramTitleText));
        builder.setMessage(getString(R.string.GlassgramTitleTextInfo));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            GlassgramConfig.setTitleText(editText.getText().toString());
            if (listView != null) {
                listView.adapter.update(true);
            }
            if (parentLayout != null) {
                parentLayout.rebuildAllFragmentViews(false, false);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
        editText.requestFocus();
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(editText), 200);
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (requestCode == REQUEST_FONT_IMPORT) {
            final String id = GlassgramFonts.importFont(data.getData());
            if (id == null) {
                BulletinFactory.of(this).createErrorBulletin(getString(R.string.GlassgramFontBadFile)).show();
            } else {
                chooseFont(id);
            }
            return;
        }
        if (requestCode == REQUEST_BACKUP_EXPORT || requestCode == REQUEST_BACKUP_RESTORE) {
            final boolean export = requestCode == REQUEST_BACKUP_EXPORT;
            final int count = export ? org.telegram.messenger.GlassgramBackup.export(data.getData())
                    : org.telegram.messenger.GlassgramBackup.restore(data.getData());
            if (count < 0) {
                BulletinFactory.of(this).createErrorBulletin(getString(export ? R.string.GlassgramBackupExportFailed : R.string.GlassgramBackupBadFile)).show();
                return;
            }
            BulletinFactory.of(this).createSuccessBulletin(LocaleController.formatString(
                    export ? R.string.GlassgramBackupExported : R.string.GlassgramBackupRestored, count)).show();
            if (!export) {
                // every screen shows the restored settings; some take a restart
                if (listView != null) listView.adapter.update(true);
                if (parentLayout != null) parentLayout.rebuildAllFragmentViews(false, false);
            }
            return;
        }
        boolean ok = false;
        if (requestCode == REQUEST_SPY_EXPORT) {
            ok = GlassgramSpyStorage.exportToUri(data.getData());
        } else if (requestCode == REQUEST_SPY_IMPORT) {
            ok = GlassgramSpyStorage.importFromUri(data.getData());
        }
        Toast.makeText(getParentActivity(), getString(ok ? R.string.GlassgramSpyDone : R.string.GlassgramSpyFailed), Toast.LENGTH_SHORT).show();
        if (listView != null) listView.adapter.update(true);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
