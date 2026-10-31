package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.GlassgramApi;
import org.telegram.messenger.GlassgramBadges;
import org.telegram.messenger.GlassgramConfig;
import org.telegram.messenger.GlassgramProxy;
import org.telegram.messenger.GlassgramStats;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.utils.proxy.ProxySettings;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

/**
 * glassGram: the dialogs of glassGram's own API (GlassgramApi): signing in, a badge from a profile,
 * support reports, crashes and admins. The developer's screen is GlassgramDeveloperActivity.
 */
public final class GlassgramApiUi {

    private GlassgramApiUi() {
    }

    // --- Common -----------------------------------------------------------------------------------

    static void showError(BaseFragment fragment, GlassgramApi.Response response) {
        if (fragment.getParentActivity() != null) {
            BulletinFactory.of(fragment).createErrorBulletin(response.message()).show();
        }
    }

    static void showDone(BaseFragment fragment, CharSequence text) {
        if (fragment.getParentActivity() != null) {
            BulletinFactory.of(fragment).createSuccessBulletin(text).show();
        }
    }

    static AlertDialog progress(BaseFragment fragment) {
        final AlertDialog dialog = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        dialog.setCanCancel(false);
        dialog.show();
        return dialog;
    }

    static EditTextBoldCursor field(Context context, CharSequence hint, boolean digits, boolean multiline) {
        final EditTextBoldCursor field = new EditTextBoldCursor(context);
        field.setHint(hint);
        field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        field.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        field.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        field.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        field.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), Theme.getColor(Theme.key_text_RedRegular));
        if (digits) {
            field.setInputType(InputType.TYPE_CLASS_NUMBER);
            field.setSingleLine(true);
        } else if (multiline) {
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            field.setSingleLine(false);
            field.setMaxLines(6);
        } else {
            field.setSingleLine(true);
        }
        field.setPadding(0, dp(6), 0, dp(6));
        return field;
    }

    static FrameLayout wrap(Context context, View field) {
        final FrameLayout container = new FrameLayout(context);
        container.addView(field, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 6, 24, 0));
        return container;
    }

    /** A long text in a scrollable dialog, with a button that copies it. */
    static void showText(BaseFragment fragment, CharSequence title, String text) {
        showText(fragment, title, text, null, null);
    }

    /** The same, with one more button. */
    static void showText(BaseFragment fragment, CharSequence title, String text, CharSequence extra, Runnable extraAction) {
        final Activity context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final TextView view = new TextView(context);
        view.setText(text);
        view.setTextIsSelectable(true);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        view.setPadding(dp(24), dp(4), dp(24), dp(4));
        final ScrollView scroll = new ScrollView(context);
        scroll.addView(view);
        final FrameLayout container = new FrameLayout(context);
        container.addView(scroll, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (int) (AndroidUtilities.displaySize.y * 0.55f)));
        final AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(container)
                .setPositiveButton(getString(R.string.Copy), (d, w) -> AndroidUtilities.addToClipboard(text))
                .setNegativeButton(getString(R.string.Close), null);
        if (extra != null && extraAction != null) {
            builder.setNeutralButton(extra, (d, w) -> extraAction.run());
        }
        builder.show();
    }

    static String date(long seconds) {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(seconds * 1000L));
    }

    /** JSON laid out for reading, without the braces' noise. */
    static String pretty(Object value, String indent) {
        final StringBuilder sb = new StringBuilder();
        if (value instanceof JSONObject) {
            final JSONObject json = (JSONObject) value;
            final Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                final String key = keys.next();
                final Object item = json.opt(key);
                if (item instanceof JSONObject || item instanceof JSONArray && ((JSONArray) item).optJSONObject(0) != null) {
                    sb.append(indent).append(key).append(":\n").append(pretty(item, indent + "  "));
                } else {
                    sb.append(indent).append(key).append(": ").append(item instanceof JSONArray ? join((JSONArray) item) : String.valueOf(item)).append('\n');
                }
            }
        } else if (value instanceof JSONArray) {
            final JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                sb.append(pretty(array.opt(i), indent)).append(i + 1 < array.length() ? indent + "—\n" : "");
            }
        } else if (value != null) {
            sb.append(indent).append(value).append('\n');
        }
        return sb.toString();
    }

    private static String join(JSONArray array) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(array.opt(i));
        }
        return sb.toString();
    }

    // --- Signing in -------------------------------------------------------------------------------

    /** Runs the action signed in, signing in first if need be. */
    public static void withSession(BaseFragment fragment, Runnable action) {
        if (GlassgramApi.getSession(fragment.getCurrentAccount()) != null) {
            action.run();
        } else {
            signIn(fragment, action);
        }
    }

    /** Signs in by Telegram's signature; where that can't be, by a code from the bot. */
    public static void signIn(BaseFragment fragment, Runnable onSignedIn) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final int account = fragment.getCurrentAccount();
        final AlertDialog progress = progress(fragment);
        GlassgramApi.signInWithTelegram(account, response -> {
            progress.dismiss();
            if (response.ok()) {
                signedIn(fragment, onSignedIn);
            } else if (response.status == 0 || response.status >= 500 || response.status == 409 || response.status == 429) {
                showError(fragment, response);
            } else {
                // the bot has no Mini App: the code it gives for /login instead
                GlassgramApi.resolveBot(account, botId -> askCode(fragment, botId, onSignedIn));
            }
        });
    }

    private static void signedIn(BaseFragment fragment, Runnable onSignedIn) {
        final GlassgramApi.Session session = GlassgramApi.getSession(fragment.getCurrentAccount());
        if (session != null && session.isStaff()) {
            showDone(fragment, formatString(R.string.GlassgramApiSignedInAs, session.levelName));
        }
        if (onSignedIn != null) {
            onSignedIn.run();
        }
    }

    private static void askCode(BaseFragment fragment, long botId, Runnable onSignedIn) {
        final Activity context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final int account = fragment.getCurrentAccount();
        final EditTextBoldCursor field = field(context, "12345678", true, false);
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(getString(R.string.GlassgramApiSignIn))
                .setMessage(AndroidUtilities.replaceTags(formatString(R.string.GlassgramApiCodeInfo, "@" + GlassgramApi.BOT_USERNAME)))
                .setView(wrap(context, field))
                .setPositiveButton(getString(R.string.GlassgramApiSignInButton), null)
                .setNeutralButton(getString(R.string.GlassgramApiGetCode), null)
                .setNegativeButton(getString(R.string.Cancel), null)
                .create();
        final boolean[] busy = {false};
        final Runnable submit = () -> {
            final String code = field.getText().toString().replaceAll("\\D", "");
            if (busy[0]) {
                return;
            }
            if (code.length() != 8) {
                AndroidUtilities.shakeViewSpring(field);
                return;
            }
            busy[0] = true;
            GlassgramApi.signInWithCode(account, code, response -> {
                busy[0] = false;
                if (response.ok()) {
                    dialog.dismiss();
                    signedIn(fragment, onSignedIn);
                } else {
                    field.setText("");
                    Toast.makeText(context, response.message(), Toast.LENGTH_LONG).show();
                }
            });
        };
        // the bot's answer to /login fills the code in by itself
        final NotificationCenter.NotificationCenterDelegate observer = (id, acc, args) -> {
            if (id != NotificationCenter.didReceiveNewMessages || botId == 0 || !(args[0] instanceof Long) || (Long) args[0] != botId) {
                return;
            }
            @SuppressWarnings("unchecked") final ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[1];
            for (MessageObject message : messages) {
                final String code = GlassgramApi.codeIn(message, botId);
                if (code != null) {
                    field.setText(code);
                    submit.run();
                    return;
                }
            }
        };
        NotificationCenter.getInstance(account).addObserver(observer, NotificationCenter.didReceiveNewMessages);
        dialog.setOnDismissListener(d -> NotificationCenter.getInstance(account).removeObserver(observer, NotificationCenter.didReceiveNewMessages));
        dialog.show();
        final View signInButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (signInButton != null) {
            signInButton.setOnClickListener(v -> submit.run());
        }
        final View getCodeButton = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
        if (getCodeButton != null) {
            getCodeButton.setOnClickListener(v -> {
                if (botId == 0) {
                    Toast.makeText(context, getString(R.string.GlassgramApiNoConnection), Toast.LENGTH_SHORT).show();
                    return;
                }
                GlassgramApi.requestCode(account, botId);
                Toast.makeText(context, getString(R.string.GlassgramApiCodeRequested), Toast.LENGTH_SHORT).show();
            });
        }
    }

    // --- Support reports --------------------------------------------------------------------------

    /** Anyone: sends the developer this phone's details, with the camera report and a comment. */
    public static void sendReport(BaseFragment fragment) {
        final Activity context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final EditTextBoldCursor field = field(context, getString(R.string.GlassgramApiReportHint), false, true);
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.GlassgramApiReportTitle))
                .setMessage(getString(R.string.GlassgramApiReportInfo))
                .setView(wrap(context, field))
                .setPositiveButton(getString(R.string.GlassgramApiReportSend), (d, w) -> {
                    final String comment = field.getText().toString().trim();
                    withSession(fragment, () -> doSendReport(fragment, comment));
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private static void doSendReport(BaseFragment fragment, String comment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final int account = fragment.getCurrentAccount();
        final AlertDialog progress = progress(fragment);
        Utilities.globalQueue.postRunnable(() -> {
            final Context context = ApplicationLoader.applicationContext;
            final int size = GlassgramConfig.roundVideoSize(MessagesController.getInstance(account).roundVideoSize);
            final JSONObject body = GlassgramApi.json(
                    "comment", comment,
                    "app", GlassgramStats.app(),
                    "device", GlassgramStats.device(),
                    "camera", org.telegram.messenger.camera.CameraReport.summary(context, size),
                    "camera_report", org.telegram.messenger.camera.CameraReport.build(context, size),
                    "logs", GlassgramStats.lastRound());
            AndroidUtilities.runOnUIThread(() -> GlassgramApi.call(account, "POST", "/support/report", body, response -> {
                progress.dismiss();
                if (response.ok()) {
                    showDone(fragment, getString(R.string.GlassgramApiReportSent));
                } else {
                    showError(fragment, response);
                }
            }));
        });
    }

    /** The support reports of one person (userId), or the latest of everyone (0). */
    public static void showReports(BaseFragment fragment, long userId) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final AlertDialog progress = progress(fragment);
        GlassgramApi.call(fragment.getCurrentAccount(), "GET", "/support/reports?limit=30" + (userId != 0 ? "&user_id=" + userId : ""), null, response -> {
            progress.dismiss();
            if (!response.ok()) {
                showError(fragment, response);
                return;
            }
            final JSONArray reports = response.json.optJSONArray("reports");
            if (reports == null || reports.length() == 0) {
                showDone(fragment, getString(R.string.GlassgramApiNoReports));
                return;
            }
            final CharSequence[] items = new CharSequence[reports.length()];
            for (int i = 0; i < reports.length(); i++) {
                final JSONObject report = reports.optJSONObject(i);
                final String comment = report.optString("comment");
                items[i] = "#" + report.optLong("id") + " · " + date(report.optLong("created_at")) + " · " + report.optString("model")
                        + (TextUtils.isEmpty(comment) ? "" : "\n" + comment.split("\n")[0]);
            }
            new AlertDialog.Builder(fragment.getParentActivity())
                    .setTitle(getString(R.string.GlassgramApiReports))
                    .setItems(items, (d, which) -> {
                        final JSONObject report = reports.optJSONObject(which);
                        final JSONObject details = report.optJSONObject("report");
                        final StringBuilder text = new StringBuilder();
                        text.append("User ").append(report.optLong("user_id")).append(", ").append(date(report.optLong("created_at"))).append("\n\n");
                        if (!TextUtils.isEmpty(report.optString("comment"))) {
                            text.append(report.optString("comment")).append("\n\n");
                        }
                        if (details != null) {
                            text.append("App:\n").append(pretty(details.optJSONObject("app"), "  "));
                            text.append("\nDevice:\n").append(pretty(details.optJSONObject("device"), "  "));
                            if (!TextUtils.isEmpty(details.optString("camera_report"))) {
                                text.append("\n").append(details.optString("camera_report"));
                            }
                            if (!TextUtils.isEmpty(details.optString("logs"))) {
                                text.append("\n\nLogs:\n").append(details.optString("logs"));
                            }
                        }
                        showText(fragment, "#" + report.optLong("id"), text.toString());
                    })
                    .setNegativeButton(getString(R.string.Close), null)
                    .show();
        });
    }

    // --- Who uses glassGram ------------------------------------------------------------------------

    /** The profile's glassGram row: the version, phone and last day, from GlassgramApi.userInfo. */
    public static CharSequence userInfoLine(GlassgramApi.Response response) {
        if (response == null) {
            return getString(R.string.GlassgramApiUserChecking);
        }
        if (!response.ok()) {
            return getString(R.string.GlassgramApiUserUnknown);
        }
        final JSONArray installs = response.json.optJSONArray("installs");
        final JSONObject install = installs != null ? installs.optJSONObject(0) : null;
        if (!response.json.optBoolean("uses") || install == null) {
            return getString(R.string.GlassgramApiUserNo);
        }
        return install.optString("version") + " · " + deviceName(install) + " · "
                + LocaleController.formatDate(install.optLong("last_seen"), true)
                + (installs.length() > 1 ? " +" + (installs.length() - 1) : "");
    }

    private static String deviceName(JSONObject install) {
        final String maker = install.optString("manufacturer");
        final String model = install.optString("model");
        if (maker.isEmpty() || model.toLowerCase().startsWith(maker.toLowerCase())) {
            return model;
        }
        return Character.toUpperCase(maker.charAt(0)) + maker.substring(1) + " " + model;
    }

    /** What the profile's glassGram row knows, in full: each phone, its version and last report. */
    public static void showUserInfo(BaseFragment fragment, long userId, GlassgramApi.Response response) {
        if (response == null) {
            return;
        }
        if (!response.ok()) {
            showError(fragment, response);
            return;
        }
        final JSONArray installs = response.json.optJSONArray("installs");
        final int reports = response.json.optInt("support_reports");
        final StringBuilder text = new StringBuilder();
        if (!response.json.optBoolean("uses") || installs == null || installs.length() == 0) {
            text.append(formatString(R.string.GlassgramApiUserNoInfo, reports));
        } else {
            text.append(formatString(R.string.GlassgramApiUserYesInfo,
                    LocaleController.formatDate(response.json.optLong("last_seen"), true), reports));
            for (int i = 0; i < installs.length(); i++) {
                final JSONObject install = installs.optJSONObject(i);
                if (install == null) {
                    continue;
                }
                text.append("\n\n— ").append(formatString(R.string.GlassgramApiUserInstall,
                        deviceName(install), GlassgramDeveloperActivity.androidName(install.optInt("sdk")),
                        install.optString("version"), install.optString("telegram"), install.optString("edition"),
                        LocaleController.formatDate(install.optLong("first_seen"), true),
                        LocaleController.formatDate(install.optLong("last_seen"), true)));
                final JSONObject report = install.optJSONObject("report");
                if (report != null) {
                    report.remove("app");
                    report.remove("install");
                    text.append('\n').append(pretty(report, "  "));
                }
            }
        }
        showText(fragment, "glassGram · " + userId, text.toString().trim());
    }

    // --- Badges -----------------------------------------------------------------------------------

    /** The way badges.json names a profile: users and bots by id, channels as -100…, groups as -id. */
    public static String holderOf(long userId, TLRPC.Chat chat) {
        if (userId != 0) {
            return String.valueOf(userId);
        }
        if (chat == null) {
            return null;
        }
        return ChatObject.isChannel(chat) ? "-100" + chat.id : "-" + chat.id;
    }

    /** Asks who, by id or @username, and edits their badge. */
    public static void askHolderAndEditBadge(BaseFragment fragment) {
        final Activity context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final EditTextBoldCursor field = field(context, getString(R.string.GlassgramApiHolderHint), false, false);
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.GlassgramApiGiveBadge))
                .setView(wrap(context, field))
                .setPositiveButton(getString(R.string.OK), (d, w) -> {
                    final String holder = field.getText().toString().trim().replace("https://t.me/", "").replace("t.me/", "");
                    if (!holder.isEmpty()) {
                        editBadge(fragment, holder, holder);
                    }
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    /** Gives, changes or takes the badge of a holder, as far as the signed-in admin may. */
    public static void editBadge(BaseFragment fragment, String holder, CharSequence name) {
        if (fragment.getParentActivity() == null || holder == null) {
            return;
        }
        final int account = fragment.getCurrentAccount();
        final AlertDialog progress = progress(fragment);
        GlassgramApi.call(account, "GET", "/badges", null, kinds -> {
            if (!kinds.ok()) {
                progress.dismiss();
                showError(fragment, kinds);
                return;
            }
            GlassgramApi.call(account, "GET", "/badges/" + Uri.encode(holder), null, current -> {
                progress.dismiss();
                if (!current.ok()) {
                    showError(fragment, current);
                    return;
                }
                showBadgeChoice(fragment, holder, name, kinds.json, current.json);
            });
        });
    }

    private static void showBadgeChoice(BaseFragment fragment, String holder, CharSequence name, JSONObject kinds, JSONObject current) {
        final Activity context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final String badge = current.isNull("badge") ? null : current.optString("badge");
        if (!current.optBoolean("can_change", true)) {
            new AlertDialog.Builder(context)
                    .setTitle(name)
                    .setMessage(formatString(R.string.GlassgramApiBadgeLocked, badge))
                    .setPositiveButton(getString(R.string.OK), null)
                    .show();
            return;
        }
        final JSONArray allowed = kinds.optJSONArray("allowed");
        final ArrayList<String> ids = new ArrayList<>();
        final ArrayList<CharSequence> items = new ArrayList<>();
        for (int i = 0; allowed != null && i < allowed.length(); i++) {
            final String id = allowed.optString(i);
            ids.add(id);
            items.add((id.equals(badge) ? "✓ " : "") + id);
        }
        if (badge != null) {
            ids.add(null);
            items.add(formatString(R.string.GlassgramApiTakeBadge, badge));
        }
        final String title = badge != null ? formatString(R.string.GlassgramApiBadgeNow, name, badge) : formatString(R.string.GlassgramApiBadgeNone, name);
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setItems(items.toArray(new CharSequence[0]), (d, which) -> {
                    final String chosen = ids.get(which);
                    if (chosen == null) {
                        takeBadge(fragment, holder, badge);
                    } else {
                        askBadgeText(fragment, holder, chosen);
                    }
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private static void askBadgeText(BaseFragment fragment, String holder, String badge) {
        final Activity context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final EditTextBoldCursor field = field(context, getString(R.string.GlassgramApiBadgeTextHint), false, true);
        new AlertDialog.Builder(context)
                .setTitle(formatString(R.string.GlassgramApiGiveBadgeNamed, badge))
                .setMessage(getString(R.string.GlassgramApiBadgeTextInfo))
                .setView(wrap(context, field))
                .setPositiveButton(getString(R.string.GlassgramApiGive), (d, w) -> {
                    final String text = field.getText().toString().trim();
                    Object description = null;
                    final String[] parts = text.split("(?m)^\\s*---\\s*$");
                    if (parts.length == 2 && !parts[0].trim().isEmpty() && !parts[1].trim().isEmpty()) {
                        description = GlassgramApi.json("ru", parts[0].trim(), "en", parts[1].trim());
                    } else if (!text.isEmpty()) {
                        description = text;
                    }
                    final AlertDialog progress = progress(fragment);
                    GlassgramApi.call(fragment.getCurrentAccount(), "PUT", "/badges/" + Uri.encode(holder),
                            GlassgramApi.json("badge", badge, "text", description), response -> {
                                progress.dismiss();
                                if (response.ok()) {
                                    showDone(fragment, formatString(R.string.GlassgramApiBadgeGiven, badge));
                                    GlassgramBadges.refresh(true);
                                } else {
                                    showError(fragment, response);
                                }
                            });
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private static void takeBadge(BaseFragment fragment, String holder, String badge) {
        final AlertDialog progress = progress(fragment);
        GlassgramApi.call(fragment.getCurrentAccount(), "DELETE", "/badges/" + Uri.encode(holder), null, response -> {
            progress.dismiss();
            if (response.ok()) {
                showDone(fragment, formatString(R.string.GlassgramApiBadgeTaken, badge));
                GlassgramBadges.refresh(true);
            } else {
                showError(fragment, response);
            }
        });
    }

    // --- Crashes ----------------------------------------------------------------------------------

    public static void showCrashes(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final int account = fragment.getCurrentAccount();
        final AlertDialog progress = progress(fragment);
        GlassgramApi.call(account, "GET", "/crashes?days=30", null, response -> {
            progress.dismiss();
            if (!response.ok()) {
                showError(fragment, response);
                return;
            }
            final JSONArray crashes = response.json.optJSONArray("crashes");
            if (crashes == null || crashes.length() == 0) {
                showDone(fragment, getString(R.string.GlassgramApiNoCrashes));
                return;
            }
            final CharSequence[] items = new CharSequence[crashes.length()];
            for (int i = 0; i < crashes.length(); i++) {
                final JSONObject crash = crashes.optJSONObject(i);
                items[i] = "×" + crash.optInt("count") + " · " + formatString(R.string.GlassgramApiInstallsCount, crash.optInt("installs"))
                        + "\n" + crash.optString("title");
            }
            new AlertDialog.Builder(fragment.getParentActivity())
                    .setTitle(getString(R.string.GlassgramApiCrashes))
                    .setItems(items, (d, which) -> {
                        final JSONObject crash = crashes.optJSONObject(which);
                        final AlertDialog loading = progress(fragment);
                        GlassgramApi.call(account, "GET", "/crashes/" + crash.optString("signature"), null, samples -> {
                            loading.dismiss();
                            if (!samples.ok()) {
                                showError(fragment, samples);
                                return;
                            }
                            final JSONArray list = samples.json.optJSONArray("samples");
                            final StringBuilder text = new StringBuilder();
                            text.append(formatString(R.string.GlassgramApiCrashSummary, crash.optInt("count"), crash.optInt("installs"),
                                    join(crash.optJSONArray("versions") != null ? crash.optJSONArray("versions") : new JSONArray())));
                            for (int i = 0; list != null && i < list.length(); i++) {
                                final JSONObject sample = list.optJSONObject(i);
                                text.append("\n\n— ").append(date(sample.optLong("created_at"))).append(", ").append(sample.optString("version"))
                                        .append(", ").append(sample.optString("model")).append(", SDK ").append(sample.optInt("sdk"));
                                if (i == 0) {
                                    text.append("\n").append(sample.optString("trace"));
                                }
                            }
                            showText(fragment, crash.optString("title"), text.toString());
                        });
                    })
                    .setNegativeButton(getString(R.string.Close), null)
                    .show();
        });
    }

    // --- Rules for devices ------------------------------------------------------------------------

    /** What an option a rule may turn off is called in the settings. */
    static String featureLabel(String feature) {
        switch (feature) {
            case "roundFps60": return formatString(R.string.GlassgramApiRuleRound, getString(R.string.GlassgramRound60Fps));
            case "roundStabilization": return formatString(R.string.GlassgramApiRuleRound, getString(R.string.GlassgramRoundStabilization));
            case "roundHorizon": return formatString(R.string.GlassgramApiRuleRound, getString(R.string.GlassgramRoundHorizon));
            case "roundWideCamera": return formatString(R.string.GlassgramApiRuleRound, getString(R.string.GlassgramRoundWide));
            case "roundKeepZoom": return formatString(R.string.GlassgramApiRuleRound, getString(R.string.GlassgramRoundKeepZoom));
            case "roundHighQuality": return formatString(R.string.GlassgramApiRuleRound, getString(R.string.GlassgramRoundHighQuality));
            case "glassHeader": return getString(R.string.GlassgramGlassHeader);
            case "glassPanels": return getString(R.string.GlassgramGlassPanels);
            case "splitInput": return getString(R.string.GlassgramSplitInput);
            case "iosTransitions": return getString(R.string.GlassgramIosTransitions);
            case "uploadBoost": return getString(R.string.GlassgramUploadBoost);
            case "downloadBoost": return getString(R.string.GlassgramDownloadBoost);
            case "pluginsEngine": return getString(R.string.GlassgramPluginsEnable);
            default: return feature;
        }
    }

    /** The keys a rule matches by, in the editor's order, with their hints. */
    private static final String[] MATCH_KEYS = {"model", "manufacturer", "brand", "device", "soc", "sdk_min", "sdk_max", "version_min", "version_max"};
    private static final int[] MATCH_HINTS = {
            R.string.GlassgramApiRuleModel, R.string.GlassgramApiRuleManufacturer, R.string.GlassgramApiRuleBrand,
            R.string.GlassgramApiRuleDevice, R.string.GlassgramApiRuleSoc, R.string.GlassgramApiRuleSdkMin,
            R.string.GlassgramApiRuleSdkMax, R.string.GlassgramApiRuleVersionMin, R.string.GlassgramApiRuleVersionMax};

    static String matchText(JSONObject match) {
        if (match == null || match.length() == 0) {
            return getString(R.string.GlassgramApiRuleAllPhones);
        }
        final StringBuilder sb = new StringBuilder();
        for (String key : MATCH_KEYS) {
            if (!match.has(key)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            switch (key) {
                case "sdk_min": sb.append("SDK ≥ ").append(match.opt(key)); break;
                case "sdk_max": sb.append("SDK ≤ ").append(match.opt(key)); break;
                case "version_min": sb.append("glassGram ≥ ").append(match.opt(key)); break;
                case "version_max": sb.append("glassGram ≤ ").append(match.opt(key)); break;
                default: sb.append(key).append(' ').append(match.opt(key));
            }
        }
        if (match.has("edition")) {
            sb.append(sb.length() > 0 ? ", " : "").append(match.opt("edition"));
        }
        return sb.toString();
    }

    private static String offText(JSONArray off) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; off != null && i < off.length(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(featureLabel(off.optString(i)));
        }
        return sb.toString();
    }

    /** The team's rules for devices: each with how many installs it matches, and a new one. */
    public static void showRules(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final AlertDialog progress = progress(fragment);
        GlassgramApi.call(fragment.getCurrentAccount(), "GET", "/config/rules", null, response -> {
            progress.dismiss();
            if (!response.ok()) {
                showError(fragment, response);
                return;
            }
            final JSONArray rules = response.json.optJSONArray("rules");
            final int count = rules != null ? rules.length() : 0;
            final CharSequence[] items = new CharSequence[count + 1];
            for (int i = 0; i < count; i++) {
                final JSONObject rule = rules.optJSONObject(i);
                items[i] = (rule.optBoolean("enabled", true) ? "" : "⏸ ") + "#" + rule.optLong("id") + " · " + matchText(rule.optJSONObject("match"))
                        + "\n" + offText(rule.optJSONArray("off")) + " · " + formatString(R.string.GlassgramApiInstallsCount, rule.optInt("installs"));
            }
            items[count] = "＋ " + getString(R.string.GlassgramApiRuleNew);
            if (fragment.getParentActivity() == null) {
                return;
            }
            new AlertDialog.Builder(fragment.getParentActivity())
                    .setTitle(getString(R.string.GlassgramApiRules))
                    .setItems(items, (d, which) -> {
                        if (which == count) {
                            editRule(fragment, null, null);
                        } else {
                            ruleActions(fragment, rules.optJSONObject(which));
                        }
                    })
                    .setNegativeButton(getString(R.string.Close), null)
                    .show();
        });
    }

    private static void ruleActions(BaseFragment fragment, JSONObject rule) {
        if (fragment.getParentActivity() == null || rule == null) {
            return;
        }
        final boolean enabled = rule.optBoolean("enabled", true);
        final long id = rule.optLong("id");
        final String note = rule.optString("note");
        new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle("#" + id + " · " + matchText(rule.optJSONObject("match")))
                .setMessage(offText(rule.optJSONArray("off")) + (TextUtils.isEmpty(note) ? "" : "\n\n" + note))
                .setItems(new CharSequence[]{
                        getString(R.string.GlassgramApiRuleEdit),
                        getString(enabled ? R.string.GlassgramApiRulePause : R.string.GlassgramApiRuleResume),
                        getString(R.string.GlassgramApiRuleDelete)}, (d, which) -> {
                    final int account = fragment.getCurrentAccount();
                    final GlassgramApi.Callback done = response -> {
                        if (response.ok()) {
                            showDone(fragment, getString(R.string.GlassgramApiSaved));
                            // this phone follows the change at once
                            org.telegram.messenger.GlassgramRemoteConfig.refresh(0, null);
                            showRules(fragment);
                        } else {
                            showError(fragment, response);
                        }
                    };
                    if (which == 0) {
                        editRule(fragment, rule, null);
                    } else if (which == 1) {
                        GlassgramApi.call(account, "PUT", "/config/rules/" + id, GlassgramApi.json("match", rule.optJSONObject("match"),
                                "off", rule.optJSONArray("off"), "note", note, "enabled", !enabled), done);
                    } else {
                        GlassgramApi.call(account, "DELETE", "/config/rules/" + id, null, done);
                    }
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    /** Adds a rule, or changes one; for a model, from its phones, the model is filled in. */
    public static void editRule(BaseFragment fragment, JSONObject rule, String model) {
        final Activity context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final JSONObject match = rule != null ? rule.optJSONObject("match") : null;
        final JSONArray off = rule != null ? rule.optJSONArray("off") : null;
        final android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        final EditTextBoldCursor[] fields = new EditTextBoldCursor[MATCH_KEYS.length];
        for (int i = 0; i < MATCH_KEYS.length; i++) {
            final boolean number = MATCH_KEYS[i].startsWith("sdk_");
            fields[i] = field(context, getString(MATCH_HINTS[i]), number, false);
            if (MATCH_KEYS[i].startsWith("version_")) {
                fields[i].setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                fields[i].setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789."));
            }
            if (match != null && match.has(MATCH_KEYS[i])) {
                fields[i].setText(String.valueOf(match.opt(MATCH_KEYS[i])));
            } else if ("model".equals(MATCH_KEYS[i]) && model != null) {
                fields[i].setText(model);
            }
            layout.addView(fields[i], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 4, 24, 0));
        }
        final TextView offHeader = new TextView(context);
        offHeader.setText(getString(R.string.GlassgramApiRuleOff));
        offHeader.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        offHeader.setTypeface(AndroidUtilities.bold());
        offHeader.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        layout.addView(offHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 16, 24, 4));
        final String[] features = org.telegram.messenger.GlassgramRemoteConfig.FEATURES;
        final org.telegram.ui.Cells.CheckBoxCell[] checks = new org.telegram.ui.Cells.CheckBoxCell[features.length];
        for (int i = 0; i < features.length; i++) {
            boolean checked = false;
            for (int j = 0; off != null && j < off.length(); j++) {
                checked |= features[i].equals(off.optString(j));
            }
            final org.telegram.ui.Cells.CheckBoxCell check = new org.telegram.ui.Cells.CheckBoxCell(context, 1);
            check.setBackground(Theme.getSelectorDrawable(false));
            check.setText(featureLabel(features[i]), "", checked, false);
            check.setOnClickListener(v -> check.setChecked(!check.isChecked(), true));
            checks[i] = check;
            layout.addView(check, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }
        final EditTextBoldCursor note = field(context, getString(R.string.GlassgramApiRuleNote), false, true);
        if (rule != null) {
            note.setText(rule.optString("note"));
        }
        layout.addView(note, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 8, 24, 8));
        final TextView info = new TextView(context);
        info.setText(getString(R.string.GlassgramApiRuleInfo));
        info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        info.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        layout.addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 8));
        final ScrollView scroll = new ScrollView(context);
        scroll.addView(layout);

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(getString(rule != null ? R.string.GlassgramApiRuleEdit : R.string.GlassgramApiRuleNew))
                .setView(scroll)
                .setPositiveButton(getString(R.string.Save), null)
                .setNeutralButton(getString(R.string.GlassgramApiRuleCheck), null)
                .setNegativeButton(getString(R.string.Cancel), null)
                .create();
        final int account = fragment.getCurrentAccount();
        final Utilities.Callback<JSONObject> collect = target -> {
            for (int i = 0; i < MATCH_KEYS.length; i++) {
                final String value = fields[i].getText().toString().trim();
                if (value.isEmpty()) {
                    continue;
                }
                try {
                    if (MATCH_KEYS[i].startsWith("sdk_")) {
                        target.put(MATCH_KEYS[i], Utilities.parseInt(value));
                    } else {
                        target.put(MATCH_KEYS[i], value);
                    }
                } catch (Exception ignore) {
                }
            }
        };
        dialog.show();
        final View check = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
        if (check != null) {
            check.setOnClickListener(v -> {
                final JSONObject newMatch = new JSONObject();
                collect.run(newMatch);
                GlassgramApi.call(account, "POST", "/config/preview", GlassgramApi.json("match", newMatch), response -> {
                    if (!response.ok()) {
                        Toast.makeText(context, response.message(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    final JSONArray models = response.json.optJSONArray("models");
                    final StringBuilder text = new StringBuilder(formatString(R.string.GlassgramApiRulePreview, response.json.optInt("installs")));
                    for (int i = 0; models != null && i < Math.min(5, models.length()); i++) {
                        final JSONArray row = models.optJSONArray(i);
                        text.append(i == 0 ? ": " : ", ").append(row.optString(0)).append(" (").append(row.optInt(1)).append(')');
                    }
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show();
                });
            });
        }
        final View save = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (save != null) {
            save.setOnClickListener(v -> {
                final JSONObject newMatch = new JSONObject();
                collect.run(newMatch);
                final JSONArray newOff = new JSONArray();
                for (int i = 0; i < features.length; i++) {
                    if (checks[i].isChecked()) {
                        newOff.put(features[i]);
                    }
                }
                if (newOff.length() == 0) {
                    AndroidUtilities.shakeViewSpring(offHeader);
                    Toast.makeText(context, getString(R.string.GlassgramApiRuleNothingOff), Toast.LENGTH_SHORT).show();
                    return;
                }
                final JSONObject body = GlassgramApi.json("match", newMatch, "off", newOff, "note", note.getText().toString().trim(),
                        "enabled", rule == null || rule.optBoolean("enabled", true));
                final Runnable send = () -> GlassgramApi.call(account, rule == null ? "POST" : "PUT",
                        rule == null ? "/config/rules" : "/config/rules/" + rule.optLong("id"), body, response -> {
                            if (response.ok()) {
                                dialog.dismiss();
                                showDone(fragment, getString(R.string.GlassgramApiSaved));
                                // this phone follows the change at once
                                org.telegram.messenger.GlassgramRemoteConfig.refresh(0, null);
                            } else {
                                Toast.makeText(context, response.message(), Toast.LENGTH_LONG).show();
                            }
                        });
                if (newMatch.length() == 0) {
                    // every phone: worth a second look
                    new AlertDialog.Builder(context)
                            .setTitle(getString(R.string.GlassgramApiRuleAllPhones))
                            .setMessage(getString(R.string.GlassgramApiRuleAllPhonesConfirm))
                            .setPositiveButton(getString(R.string.Save), (d2, w2) -> send.run())
                            .setNegativeButton(getString(R.string.Cancel), null)
                            .show();
                } else {
                    send.run();
                }
            });
        }
    }

    // --- Bans -------------------------------------------------------------------------------------

    /** Bans a Telegram account from glassGram, asking why; or lifts its ban. */
    public static void toggleBan(BaseFragment fragment, long userId, CharSequence name) {
        final Activity context = fragment.getParentActivity();
        if (context == null || userId <= 0) {
            return;
        }
        final int account = fragment.getCurrentAccount();
        final GlassgramApi.Callback done = response -> {
            if (response.ok()) {
                showDone(fragment, getString(R.string.GlassgramApiSaved));
                GlassgramApi.loadBans(0, null);
            } else {
                showError(fragment, response);
            }
        };
        if (GlassgramApi.banReason(userId) != null) {
            new AlertDialog.Builder(context)
                    .setTitle(name)
                    .setMessage(getString(R.string.GlassgramApiUnbanConfirm))
                    .setPositiveButton(getString(R.string.GlassgramApiUnban), (d, w) -> GlassgramApi.call(account, "DELETE", "/bans/" + userId, null, done))
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .show();
            return;
        }
        final EditTextBoldCursor field = field(context, getString(R.string.GlassgramApiBanReasonHint), false, true);
        new AlertDialog.Builder(context)
                .setTitle(formatString(R.string.GlassgramApiBanTitle, name))
                .setMessage(getString(R.string.GlassgramApiBanInfo))
                .setView(wrap(context, field))
                .setPositiveButton(getString(R.string.GlassgramApiBan), (d, w) -> GlassgramApi.call(account, "PUT", "/bans/" + userId,
                        GlassgramApi.json("reason", field.getText().toString().trim()), done))
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    /** The banned accounts: tap one to lift its ban, or ban another by id or @username. */
    public static void showBans(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final AlertDialog progress = progress(fragment);
        GlassgramApi.call(fragment.getCurrentAccount(), "GET", "/bans", null, response -> {
            progress.dismiss();
            if (!response.ok()) {
                showError(fragment, response);
                return;
            }
            final JSONArray bans = response.json.optJSONArray("bans");
            final int count = bans != null ? bans.length() : 0;
            final CharSequence[] items = new CharSequence[count + 1];
            for (int i = 0; i < count; i++) {
                final JSONObject ban = bans.optJSONObject(i);
                final String reason = ban.optString("reason");
                items[i] = ban.optLong("user_id") + " · " + date(ban.optLong("created_at")) + (TextUtils.isEmpty(reason) ? "" : "\n" + reason);
            }
            items[count] = "＋ " + getString(R.string.GlassgramApiBanByHolder);
            if (fragment.getParentActivity() == null) {
                return;
            }
            new AlertDialog.Builder(fragment.getParentActivity())
                    .setTitle(getString(R.string.GlassgramApiBans))
                    .setItems(items, (d, which) -> {
                        if (which == count) {
                            askBanTarget(fragment);
                        } else {
                            final long id = bans.optJSONObject(which).optLong("user_id");
                            GlassgramApi.loadBans(0, () -> toggleBan(fragment, id, String.valueOf(id)));
                        }
                    })
                    .setNegativeButton(getString(R.string.Close), null)
                    .show();
        });
    }

    /** Asks who to ban: a Telegram id, or a @username found through Telegram. */
    private static void askBanTarget(BaseFragment fragment) {
        final Activity context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final EditTextBoldCursor field = field(context, getString(R.string.GlassgramApiHolderHint), false, false);
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.GlassgramApiBanByHolder))
                .setView(wrap(context, field))
                .setPositiveButton(getString(R.string.OK), (d, w) -> {
                    final String text = field.getText().toString().trim().replace("https://t.me/", "").replace("t.me/", "");
                    if (text.matches("\\d{1,15}")) {
                        final long id = Long.parseLong(text);
                        toggleBan(fragment, id, text);
                        return;
                    }
                    final String username = text.startsWith("@") ? text.substring(1) : text;
                    if (username.isEmpty()) {
                        return;
                    }
                    MessagesController.getInstance(fragment.getCurrentAccount()).getUserNameResolver().resolve(username, id -> AndroidUtilities.runOnUIThread(() -> {
                        if (id == null || id <= 0) {
                            if (fragment.getParentActivity() != null) {
                                BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.GlassgramApiNoSuchUser)).show();
                            }
                        } else {
                            toggleBan(fragment, id, "@" + username);
                        }
                    }));
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    // --- Proxies ----------------------------------------------------------------------------------

    /** The team's proxies, which every app goes through without a VPN: each with its state, and a new one by its link. */
    public static void showProxies(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final AlertDialog progress = progress(fragment);
        GlassgramApi.call(fragment.getCurrentAccount(), "GET", "/proxies/list", null, response -> {
            progress.dismiss();
            if (!response.ok()) {
                showError(fragment, response);
                return;
            }
            final JSONArray proxies = response.json.optJSONArray("proxies");
            final int count = proxies != null ? proxies.length() : 0;
            final CharSequence[] items = new CharSequence[count + 1];
            for (int i = 0; i < count; i++) {
                final JSONObject proxy = proxies.optJSONObject(i);
                final String note = proxy.optString("note");
                items[i] = (proxy.optBoolean("enabled") ? "🟢 " : "⏸ ") + proxyAddress(proxy) + (TextUtils.isEmpty(note) ? "" : "\n" + note);
            }
            items[count] = "＋ " + getString(R.string.GlassgramApiProxyAdd);
            if (fragment.getParentActivity() == null) {
                return;
            }
            new AlertDialog.Builder(fragment.getParentActivity())
                    .setTitle(getString(R.string.GlassgramApiProxies))
                    .setItems(items, (d, which) -> {
                        if (which == count) {
                            askProxy(fragment);
                        } else {
                            showProxy(fragment, proxies.optJSONObject(which));
                        }
                    })
                    .setNegativeButton(getString(R.string.Close), null)
                    .show();
        });
    }

    private static String proxyAddress(JSONObject proxy) {
        return proxy.optString("host") + ":" + proxy.optInt("port") + ("socks5".equals(proxy.optString("type")) ? " · SOCKS5" : "");
    }

    private static ProxySettings proxySettings(JSONObject proxy) {
        return ProxySettings.builder()
                .setType("socks5".equals(proxy.optString("type")) ? ProxySettings.Type.SOCKS5 : ProxySettings.Type.MTPROTO)
                .setAddress(proxy.optString("host"))
                .setPort(proxy.optInt("port"))
                .setSecret(proxy.optString("secret"))
                .setUser(proxy.optString("user"))
                .setPassword(proxy.optString("password"))
                .build();
    }

    /** One proxy: its ping from this phone, pausing it, its note, its link, deleting it. */
    private static void showProxy(BaseFragment fragment, JSONObject proxy) {
        final Activity context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final long id = proxy.optLong("id");
        final boolean enabled = proxy.optBoolean("enabled");
        final ProxySettings settings = proxySettings(proxy);
        final CharSequence[] items = {
                getString(R.string.GlassgramApiProxyPing),
                getString(enabled ? R.string.GlassgramApiProxyPause : R.string.GlassgramApiProxyResume),
                getString(R.string.GlassgramApiProxyNote),
                getString(R.string.GlassgramApiProxyCopy),
                getString(R.string.Delete)};
        new AlertDialog.Builder(context)
                .setTitle(proxyAddress(proxy))
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        pingProxy(fragment, settings);
                    } else if (which == 1) {
                        saveProxy(fragment, "PUT", "/proxies/" + id, GlassgramApi.json("enabled", !enabled));
                    } else if (which == 2) {
                        askProxyNote(fragment, id, proxy.optString("note"));
                    } else if (which == 3) {
                        AndroidUtilities.addToClipboard(settings.getLink());
                        showDone(fragment, getString(R.string.LinkCopied));
                    } else {
                        new AlertDialog.Builder(context)
                                .setTitle(getString(R.string.Delete))
                                .setMessage(formatString(R.string.GlassgramApiProxyDelete, proxyAddress(proxy)))
                                .setPositiveButton(getString(R.string.Delete), (d2, w) -> saveProxy(fragment, "DELETE", "/proxies/" + id, null))
                                .setNegativeButton(getString(R.string.Cancel), null)
                                .show();
                    }
                })
                .setNegativeButton(getString(R.string.Close), null)
                .show();
    }

    /** How long the proxy takes to answer from this phone, the way Telegram's proxy list checks it. */
    private static void pingProxy(BaseFragment fragment, ProxySettings settings) {
        ConnectionsManager.getInstance(fragment.getCurrentAccount()).checkProxy(settings, time -> AndroidUtilities.runOnUIThread(() -> {
            if (fragment.getParentActivity() == null) {
                return;
            }
            if (time < 0) {
                BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.GlassgramApiProxyNoAnswer)).show();
            } else {
                showDone(fragment, formatString(R.string.GlassgramApiProxyPingResult, time));
            }
        }));
    }

    /** A new proxy by its link, as Telegram shares them, with a note for the team. */
    private static void askProxy(BaseFragment fragment) {
        final Activity context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final EditTextBoldCursor link = field(context, getString(R.string.GlassgramApiProxyLinkHint), false, false);
        final EditTextBoldCursor note = field(context, getString(R.string.GlassgramApiProxyNoteHint), false, false);
        final android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.addView(wrap(context, link));
        layout.addView(wrap(context, note));
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.GlassgramApiProxyAdd))
                .setView(layout)
                .setPositiveButton(getString(R.string.OK), (d, w) -> saveProxy(fragment, "POST", "/proxies",
                        GlassgramApi.json("link", link.getText().toString().trim(), "note", note.getText().toString().trim())))
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private static void askProxyNote(BaseFragment fragment, long id, String current) {
        final Activity context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final EditTextBoldCursor note = field(context, getString(R.string.GlassgramApiProxyNoteHint), false, false);
        note.setText(current);
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.GlassgramApiProxyNote))
                .setView(wrap(context, note))
                .setPositiveButton(getString(R.string.OK), (d, w) -> saveProxy(fragment, "PUT", "/proxies/" + id,
                        GlassgramApi.json("note", note.getText().toString().trim())))
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    /** Sends a change of the list, then shows it again; this app takes the new list at once. */
    private static void saveProxy(BaseFragment fragment, String method, String path, JSONObject body) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final AlertDialog progress = progress(fragment);
        GlassgramApi.call(fragment.getCurrentAccount(), method, path, body, response -> {
            progress.dismiss();
            if (!response.ok()) {
                showError(fragment, response);
                return;
            }
            showDone(fragment, getString(R.string.GlassgramApiProxySaved));
            GlassgramProxy.refreshNow();
            showProxies(fragment);
        });
    }

    // --- The tunnel's personal keys ---------------------------------------------------------------

    /** The tunnel: its limits, the 3x-ui inbounds and which give glassGram's keys, and an account's key. */
    public static void showTunnel(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final AlertDialog progress = progress(fragment);
        GlassgramApi.call(fragment.getCurrentAccount(), "GET", "/tunnel", null, response -> {
            progress.dismiss();
            final Activity context = fragment.getParentActivity();
            if (!response.ok() || context == null) {
                showError(fragment, response);
                return;
            }
            if (!response.json.optBoolean("configured")) {
                new AlertDialog.Builder(context)
                        .setTitle(getString(R.string.GlassgramApiTunnel))
                        .setMessage(getString(R.string.GlassgramApiTunnelNotSet))
                        .setPositiveButton(getString(R.string.OK), null)
                        .show();
                return;
            }
            final JSONObject settings = response.json.optJSONObject("settings");
            final JSONArray inbounds = response.json.optJSONArray("inbounds");
            final String defaultHost = response.json.optString("default_host");
            final int count = inbounds != null ? inbounds.length() : 0;
            final CharSequence[] items = new CharSequence[count + 2];
            final int gb = settings != null ? settings.optInt("gb") : 0;
            items[0] = formatString(R.string.GlassgramApiTunnelLimits, settings != null ? settings.optInt("devices") : 0,
                    gb == 0 ? getString(R.string.GlassgramApiTunnelNoLimit) : String.valueOf(gb), settings != null ? settings.optInt("days") : 0);
            for (int i = 0; i < count; i++) {
                final JSONObject inbound = inbounds.optJSONObject(i);
                final String reason = inbound.optString("unsupported");
                final StringBuilder text = new StringBuilder()
                        .append(inbound.optBoolean("enabled") ? "🟢 " : TextUtils.isEmpty(reason) ? "⚪ " : "⛔ ")
                        .append(inbound.optString("remark")).append(" · ").append(inbound.optString("protocol"))
                        .append(' ').append(inbound.optString("network")).append('/').append(inbound.optString("security"))
                        .append(" :").append(inbound.optInt("port"));
                if (inbound.optBoolean("enabled")) {
                    text.append("\n→ ").append(TextUtils.isEmpty(inbound.optString("host")) ? defaultHost : inbound.optString("host"));
                    text.append(" · ").append(formatString(R.string.GlassgramApiTunnelInboundKeys, inbound.optInt("keys")));
                } else if (!TextUtils.isEmpty(reason)) {
                    text.append("\n").append(reason);
                }
                items[i + 1] = text;
            }
            items[count + 1] = formatString(R.string.GlassgramApiTunnelKeys, response.json.optInt("keys"));
            new AlertDialog.Builder(context)
                    .setTitle(getString(R.string.GlassgramApiTunnel))
                    .setItems(items, (d, which) -> {
                        if (which == 0) {
                            askTunnelLimits(fragment, settings);
                        } else if (which == count + 1) {
                            askTunnelAccount(fragment);
                        } else {
                            showTunnelInbound(fragment, inbounds.optJSONObject(which - 1), defaultHost);
                        }
                    })
                    .setNegativeButton(getString(R.string.Close), null)
                    .show();
            final String error = response.json.optString("error");
            if (!TextUtils.isEmpty(error)) {
                BulletinFactory.of(fragment).createErrorBulletin(error).show();
            }
        });
    }

    private static void askTunnelLimits(BaseFragment fragment, JSONObject settings) {
        final Activity context = fragment.getParentActivity();
        if (context == null || settings == null) {
            return;
        }
        final EditTextBoldCursor devices = field(context, getString(R.string.GlassgramApiTunnelDevices), true, false);
        final EditTextBoldCursor gb = field(context, getString(R.string.GlassgramApiTunnelGb), true, false);
        final EditTextBoldCursor days = field(context, getString(R.string.GlassgramApiTunnelDays), true, false);
        devices.setText(String.valueOf(settings.optInt("devices")));
        gb.setText(String.valueOf(settings.optInt("gb")));
        days.setText(String.valueOf(settings.optInt("days")));
        final android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        for (EditTextBoldCursor field : new EditTextBoldCursor[]{devices, gb, days}) {
            final TextView label = new TextView(context);
            label.setText(field.getHint());
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            label.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
            label.setPadding(dp(24), dp(8), dp(24), 0);
            layout.addView(label);
            layout.addView(wrap(context, field));
        }
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.GlassgramApiTunnel))
                .setView(layout)
                .setPositiveButton(getString(R.string.OK), (d, w) -> saveTunnel(fragment, "PUT", "/tunnel/settings",
                        GlassgramApi.json("devices", Utilities.parseInt(devices.getText()), "gb", Utilities.parseInt(gb.getText()),
                                "days", Utilities.parseInt(days.getText()))))
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    /** An inbound: whether it gives keys, the address the apps reach it at, its note. */
    private static void showTunnelInbound(BaseFragment fragment, JSONObject inbound, String defaultHost) {
        final Activity context = fragment.getParentActivity();
        if (context == null || inbound == null) {
            return;
        }
        final int id = inbound.optInt("id");
        final boolean enabled = inbound.optBoolean("enabled");
        final String host = inbound.optString("host");
        final CharSequence[] items = {
                getString(enabled ? R.string.GlassgramApiTunnelStop : R.string.GlassgramApiTunnelGive),
                getString(R.string.GlassgramApiTunnelHost) + ": " + (TextUtils.isEmpty(host) ? defaultHost : host),
                getString(R.string.GlassgramApiProxyNote) + (TextUtils.isEmpty(inbound.optString("note")) ? "" : ": " + inbound.optString("note"))};
        new AlertDialog.Builder(context)
                .setTitle(inbound.optString("remark") + " :" + inbound.optInt("port"))
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        saveTunnel(fragment, "PUT", "/tunnel/sources/" + id, GlassgramApi.json("enabled", !enabled));
                        return;
                    }
                    final boolean isHost = which == 1;
                    final EditTextBoldCursor field = field(context, isHost ? formatString(R.string.GlassgramApiTunnelHostHint, defaultHost)
                            : getString(R.string.GlassgramApiProxyNoteHint), false, false);
                    field.setText(isHost ? host : inbound.optString("note"));
                    new AlertDialog.Builder(context)
                            .setTitle(getString(isHost ? R.string.GlassgramApiTunnelHost : R.string.GlassgramApiProxyNote))
                            .setView(wrap(context, field))
                            .setPositiveButton(getString(R.string.OK), (d2, w) -> saveTunnel(fragment, "PUT", "/tunnel/sources/" + id,
                                    GlassgramApi.json(isHost ? "host" : "note", field.getText().toString().trim())))
                            .setNegativeButton(getString(R.string.Cancel), null)
                            .show();
                })
                .setNegativeButton(getString(R.string.Close), null)
                .show();
    }

    /** Asks whose key to look at: a Telegram id, or a @username found through Telegram. */
    private static void askTunnelAccount(BaseFragment fragment) {
        final Activity context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final EditTextBoldCursor field = field(context, getString(R.string.GlassgramApiHolderHint), false, false);
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.GlassgramApiTunnel))
                .setView(wrap(context, field))
                .setPositiveButton(getString(R.string.OK), (d, w) -> {
                    final String text = field.getText().toString().trim().replace("https://t.me/", "").replace("t.me/", "");
                    if (text.matches("\\d{1,15}")) {
                        showTunnelKey(fragment, Long.parseLong(text));
                        return;
                    }
                    final String username = text.startsWith("@") ? text.substring(1) : text;
                    if (username.isEmpty()) {
                        return;
                    }
                    MessagesController.getInstance(fragment.getCurrentAccount()).getUserNameResolver().resolve(username, id -> AndroidUtilities.runOnUIThread(() -> {
                        if (id == null || id <= 0) {
                            if (fragment.getParentActivity() != null) {
                                BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.GlassgramApiNoSuchUser)).show();
                            }
                        } else {
                            showTunnelKey(fragment, id);
                        }
                    }));
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    /** An account's key, without the key: when made and renewed, until when, its traffic; and revoking it. */
    private static void showTunnelKey(BaseFragment fragment, long userId) {
        final AlertDialog progress = progress(fragment);
        GlassgramApi.call(fragment.getCurrentAccount(), "GET", "/tunnel/keys?user_id=" + userId, null, response -> {
            progress.dismiss();
            final Activity context = fragment.getParentActivity();
            if (!response.ok() || context == null) {
                showError(fragment, response);
                return;
            }
            final JSONArray keys = response.json.optJSONArray("keys");
            final JSONObject key = keys != null ? keys.optJSONObject(0) : null;
            if (key == null) {
                BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.GlassgramApiTunnelNoKey)).show();
                return;
            }
            final StringBuilder text = new StringBuilder(formatString(R.string.GlassgramApiTunnelKeyInfo,
                    date(key.optLong("created_at")), date(key.optLong("renewed_at")),
                    key.optLong("expires_at") > 0 ? date(key.optLong("expires_at")) : "—", key.optInt("generation")));
            final JSONArray traffic = key.optJSONArray("traffic");
            for (int i = 0; traffic != null && i < traffic.length(); i++) {
                final JSONObject item = traffic.optJSONObject(i);
                text.append("\n").append(formatString(R.string.GlassgramApiTunnelTraffic, item.optInt("inbound_id"),
                        AndroidUtilities.formatFileSize(item.optLong("up")), AndroidUtilities.formatFileSize(item.optLong("down"))));
            }
            new AlertDialog.Builder(context)
                    .setTitle(String.valueOf(userId))
                    .setMessage(text)
                    .setPositiveButton(getString(R.string.GlassgramApiTunnelRevoke), (d, w) -> {
                        final AlertDialog revoking = progress(fragment);
                        GlassgramApi.call(fragment.getCurrentAccount(), "DELETE", "/tunnel/keys/" + userId, null, revoked -> {
                            revoking.dismiss();
                            if (revoked.ok()) {
                                showDone(fragment, getString(R.string.GlassgramApiTunnelRevoked));
                            } else {
                                showError(fragment, revoked);
                            }
                        });
                    })
                    .setNegativeButton(getString(R.string.Close), null)
                    .show();
        });
    }

    /** Sends a change of the tunnel, then shows it again. */
    private static void saveTunnel(BaseFragment fragment, String method, String path, JSONObject body) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final AlertDialog progress = progress(fragment);
        GlassgramApi.call(fragment.getCurrentAccount(), method, path, body, response -> {
            progress.dismiss();
            if (!response.ok()) {
                showError(fragment, response);
                return;
            }
            showDone(fragment, getString(R.string.GlassgramApiTunnelSaved));
            showTunnel(fragment);
        });
    }

    // --- Admins -----------------------------------------------------------------------------------

    public static void showAdmins(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final int account = fragment.getCurrentAccount();
        final AlertDialog progress = progress(fragment);
        GlassgramApi.call(account, "GET", "/admins", null, response -> {
            progress.dismiss();
            if (!response.ok()) {
                showError(fragment, response);
                return;
            }
            final JSONArray owners = response.json.optJSONArray("owners");
            final JSONArray admins = response.json.optJSONArray("admins");
            final JSONArray levels = response.json.optJSONArray("levels");
            // the rights this admin may give and take: their own
            final JSONArray rights = response.json.optJSONArray("rights");
            final ArrayList<CharSequence> items = new ArrayList<>();
            final ArrayList<JSONObject> rows = new ArrayList<>();
            for (int i = 0; owners != null && i < owners.length(); i++) {
                items.add("👑 " + owners.optLong(i));
                rows.add(null);
            }
            for (int i = 0; admins != null && i < admins.length(); i++) {
                final JSONObject admin = admins.optJSONObject(i);
                final String adminName = admin.optString("name");
                String item = admin.optLong("id") + (TextUtils.isEmpty(adminName) ? "" : " " + adminName) + " — " + admin.optString("level_name");
                if (admin.optBoolean("custom_rights")) {
                    item += "\n🔐 " + rightsText(admin.optJSONArray("rights"));
                }
                items.add(item);
                rows.add(admin);
            }
            items.add(getString(R.string.GlassgramApiAppoint));
            rows.add(new JSONObject());
            new AlertDialog.Builder(fragment.getParentActivity())
                    .setTitle(getString(R.string.GlassgramApiAdmins))
                    .setItems(items.toArray(new CharSequence[0]), (d, which) -> {
                        final JSONObject row = rows.get(which);
                        if (row == null) {
                            return;
                        }
                        if (row.length() == 0) {
                            askAdminId(fragment, levels, rights);
                        } else if (row.optBoolean("can_change")) {
                            showAdmin(fragment, row.optLong("id"), levels, rights, row);
                        }
                    })
                    .setNegativeButton(getString(R.string.Close), null)
                    .show();
        });
    }

    private static void askAdminId(BaseFragment fragment, JSONArray levels, JSONArray rights) {
        final Activity context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final EditTextBoldCursor field = field(context, getString(R.string.GlassgramApiUserIdHint), true, false);
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.GlassgramApiAppoint))
                .setView(wrap(context, field))
                .setPositiveButton(getString(R.string.OK), (d, w) -> {
                    final long id = Utilities.parseLong(field.getText().toString());
                    if (id > 0) {
                        showAdmin(fragment, id, levels, rights, null);
                    }
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    /** An admin's level, rights and removal; a new one (admin null) is appointed with a level, or as a moderator with chosen rights. */
    private static void showAdmin(BaseFragment fragment, long userId, JSONArray levels, JSONArray rights, JSONObject admin) {
        final Activity context = fragment.getParentActivity();
        if (context == null || levels == null) {
            return;
        }
        final boolean existing = admin != null;
        final int currentLevel = existing ? admin.optInt("level") : 0;
        final ArrayList<CharSequence> items = new ArrayList<>();
        for (int i = 0; i < levels.length(); i++) {
            final JSONObject level = levels.optJSONObject(i);
            items.add((level.optInt("level") == currentLevel ? "✓ " : "") + level.optString("name"));
        }
        final int rightsItem = rights != null && rights.length() > 0 && (existing || levels.length() > 0) ? items.size() : -1;
        if (rightsItem >= 0) {
            items.add("🔐 " + getString(existing ? R.string.GlassgramApiRights : R.string.GlassgramApiModeratorWithRights));
        }
        final int removeItem = existing ? items.size() : -1;
        if (existing) {
            items.add(getString(R.string.GlassgramApiRemoveAdmin));
        }
        final int account = fragment.getCurrentAccount();
        new AlertDialog.Builder(context)
                .setTitle(adminTitle(userId, admin))
                .setItems(items.toArray(new CharSequence[0]), (d, which) -> {
                    if (which == rightsItem) {
                        showRights(fragment, userId, levels, rights, admin);
                        return;
                    }
                    final GlassgramApi.Callback done = adminSaved(fragment, progress(fragment));
                    if (which < levels.length()) {
                        GlassgramApi.call(account, "PUT", "/admins/" + userId,
                                GlassgramApi.json("level", levels.optJSONObject(which).optInt("level")), done);
                    } else if (which == removeItem) {
                        GlassgramApi.call(account, "DELETE", "/admins/" + userId, null, done);
                    }
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    /** Ticks for the rights this admin may give: their own. An admin not set one by one follows their level. */
    private static void showRights(BaseFragment fragment, long userId, JSONArray levels, JSONArray rights, JSONObject admin) {
        final Activity context = fragment.getParentActivity();
        if (context == null || rights == null) {
            return;
        }
        final boolean existing = admin != null;
        final JSONArray current = existing ? admin.optJSONArray("rights") : null;
        final java.util.HashSet<String> theirs = new java.util.HashSet<>();
        for (int i = 0; current != null && i < current.length(); i++) {
            theirs.add(current.optString(i));
        }
        final android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        final TextView info = new TextView(context);
        String infoText = getString(R.string.GlassgramApiRightsInfo);
        if (existing) {
            infoText = getString(admin.optBoolean("custom_rights") ? R.string.GlassgramApiRightsOwn : R.string.GlassgramApiRightsByLevel) + "\n\n" + infoText;
        }
        info.setText(infoText);
        info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        info.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        layout.addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 4, 24, 8));
        final String[] ids = new String[rights.length()];
        final org.telegram.ui.Cells.CheckBoxCell[] checks = new org.telegram.ui.Cells.CheckBoxCell[rights.length()];
        for (int i = 0; i < rights.length(); i++) {
            final JSONObject right = rights.optJSONObject(i);
            ids[i] = right.optString("id");
            final org.telegram.ui.Cells.CheckBoxCell check = new org.telegram.ui.Cells.CheckBoxCell(context, 1);
            check.setBackground(Theme.getSelectorDrawable(false));
            check.setText(rightLabel(ids[i], right.optString("name")), "", theirs.contains(ids[i]), false);
            check.setOnClickListener(v -> check.setChecked(!check.isChecked(), true));
            checks[i] = check;
            layout.addView(check, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }
        final ScrollView scroll = new ScrollView(context);
        scroll.addView(layout);
        final int account = fragment.getCurrentAccount();
        final AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(existing ? formatString(R.string.GlassgramApiRightsTitle, adminTitle(userId, admin)) : getString(R.string.GlassgramApiModeratorWithRights))
                .setView(scroll)
                .setPositiveButton(getString(R.string.Save), (d, w) -> {
                    final JSONArray chosen = new JSONArray();
                    for (int i = 0; i < checks.length; i++) {
                        if (checks[i].isChecked()) {
                            chosen.put(ids[i]);
                        }
                    }
                    // a new one is appointed at the lowest level, with just these rights
                    final JSONObject body = existing ? GlassgramApi.json("rights", chosen)
                            : GlassgramApi.json("level", levels.optJSONObject(0).optInt("level"), "rights", chosen);
                    GlassgramApi.call(account, "PUT", "/admins/" + userId, body, adminSaved(fragment, progress(fragment)));
                })
                .setNegativeButton(getString(R.string.Cancel), null);
        if (existing && admin.optBoolean("custom_rights")) {
            builder.setNeutralButton(getString(R.string.GlassgramApiRightsLevel), (d, w) ->
                    GlassgramApi.call(account, "PUT", "/admins/" + userId, GlassgramApi.json("rights", JSONObject.NULL),
                            adminSaved(fragment, progress(fragment))));
        }
        builder.show();
    }

    private static String adminTitle(long userId, JSONObject admin) {
        final String name = admin != null ? admin.optString("name") : "";
        return TextUtils.isEmpty(name) ? String.valueOf(userId) : name + " (" + userId + ")";
    }

    /** After a change of an admin: done, or why not; rights one lacks are named. */
    private static GlassgramApi.Callback adminSaved(BaseFragment fragment, AlertDialog progress) {
        return response -> {
            progress.dismiss();
            if (response.ok()) {
                showDone(fragment, getString(R.string.GlassgramApiSaved));
                return;
            }
            final JSONArray lacking = response.json.optJSONArray("rights");
            if (response.status == 403 && lacking != null && lacking.length() > 0 && fragment.getParentActivity() != null) {
                BulletinFactory.of(fragment).createErrorBulletin(formatString(R.string.GlassgramApiRightsLacking, rightsText(lacking))).show();
            } else {
                showError(fragment, response);
            }
        };
    }

    private static String rightsText(JSONArray rights) {
        final StringBuilder text = new StringBuilder();
        for (int i = 0; rights != null && i < rights.length(); i++) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(rightLabel(rights.optString(i), rights.optString(i)));
        }
        return text.length() > 0 ? text.toString() : getString(R.string.GlassgramApiRightsNone);
    }

    /** A right's name in the app's language; the server's name for one this version doesn't know. */
    private static String rightLabel(String right, String fallback) {
        switch (right) {
            case GlassgramApi.RIGHT_BADGES: return getString(R.string.GlassgramApiRightBadges);
            case GlassgramApi.RIGHT_BANS: return getString(R.string.GlassgramApiRightBans);
            case GlassgramApi.RIGHT_SUPPORT: return getString(R.string.GlassgramApiRightSupport);
            case GlassgramApi.RIGHT_STATS: return getString(R.string.GlassgramApiRightStats);
            case GlassgramApi.RIGHT_USERS: return getString(R.string.GlassgramApiRightUsers);
            case GlassgramApi.RIGHT_CONFIG: return getString(R.string.GlassgramApiRightConfig);
            case GlassgramApi.RIGHT_PLUGINS: return getString(R.string.GlassgramApiRightPlugins);
            case GlassgramApi.RIGHT_PROXIES: return getString(R.string.GlassgramApiRightProxies);
            case GlassgramApi.RIGHT_TUNNEL: return getString(R.string.GlassgramApiRightTunnel);
            case GlassgramApi.RIGHT_ADMINS: return getString(R.string.GlassgramApiRightAdmins);
            default: return fallback;
        }
    }
}
