package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * glassGram's own API, served by the bot (glassgram-bot/API.md): signing in for the team's tools,
 * badges and admins from the app, usage statistics (with who uses glassGram), anonymous crash
 * reports and support reports.
 * <p>
 * The app's source is open, so the server never takes the app's word for who is signing in:
 * Telegram vouches for it. The app asks Telegram for the bot's Mini App, whose launch data Telegram
 * signs with the bot's token, and sends that; or, where the bot has no Mini App, it sends /login to
 * the bot and signs in with the code the bot answers. A session is kept per Telegram account.
 */
public final class GlassgramApi {

    public static final String BASE_URL = "https://glassgram-api.vanguardvpn.xyz:8444/api/v1";
    public static final String BOT_USERNAME = "glassGramBot";

    /** The rights the server gives: by the bot's admin levels, or to an admin one by one. */
    public static final String RIGHT_BADGES = "badges";
    public static final String RIGHT_SUPPORT = "support";
    public static final String RIGHT_STATS = "stats";
    public static final String RIGHT_USERS = "users";
    public static final String RIGHT_CONFIG = "config";
    public static final String RIGHT_PLUGINS = "plugins";
    public static final String RIGHT_BANS = "bans";
    public static final String RIGHT_PROXIES = "proxies";
    public static final String RIGHT_TUNNEL = "tunnel";
    public static final String RIGHT_ADMINS = "admins";

    private static final String PREFS = "glassgram_api";
    private static final DispatchQueue queue = new DispatchQueue("glassgramApi");

    private GlassgramApi() {
    }

    // --- Requests ---------------------------------------------------------------------------------

    public static final class Response {
        /** The HTTP status, or 0 when the server couldn't be reached. */
        public final int status;
        /** The answer, never null: {} when there was none. */
        public final JSONObject json;

        Response(int status, JSONObject json) {
            this.status = status;
            this.json = json;
        }

        public boolean ok() {
            return status >= 200 && status < 300;
        }

        /** The server's error code ("forbidden"…), or "network" when it couldn't be reached. */
        public String error() {
            return json.optString("error", status == 0 ? "network" : "http_" + status);
        }

        /** What went wrong, to show. */
        public String message() {
            if (status == 0) {
                return LocaleController.getString(R.string.GlassgramApiNoConnection);
            }
            return json.optString("message", "HTTP " + status);
        }
    }

    public interface Callback {
        void onResponse(Response response);
    }

    /** A request off the main thread; the callback runs on the main thread. The token may be null. */
    public static void request(String method, String path, JSONObject body, String token, Callback callback) {
        queue.postRunnable(() -> {
            final Response response = execute(method, path, body, token);
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onResponse(response));
            }
        });
    }

    /** The same, on the calling thread, which mustn't be the main one. */
    public static Response execute(String method, String path, JSONObject body, String token) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "glassGram/" + BuildVars.GLASSGRAM_VERSION + " (Android " + Build.VERSION.SDK_INT + ")");
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (body != null) {
                final byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(data.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(data);
                }
            }
            final int status = connection.getResponseCode();
            final InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            final String text = read(in);
            JSONObject json;
            try {
                json = TextUtils.isEmpty(text) ? new JSONObject() : new JSONObject(text);
            } catch (JSONException e) {
                json = new JSONObject();
            }
            return new Response(status, json);
        } catch (Exception e) {
            FileLog.e(e, false);
            return new Response(0, new JSONObject());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** A file from the server, up to maxBytes, on the calling thread (not the main one); null when it failed. */
    public static byte[] download(String path, int maxBytes) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "glassGram/" + BuildVars.GLASSGRAM_VERSION + " (Android " + Build.VERSION.SDK_INT + ")");
            if (connection.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = connection.getInputStream()) {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                final byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    if (out.size() > maxBytes) {
                        return null;
                    }
                }
                return out.toByteArray();
            }
        } catch (Exception e) {
            FileLog.e(e, false);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String read(InputStream in) throws java.io.IOException {
        if (in == null) {
            return "";
        }
        try (InputStream stream = in) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        }
    }

    // --- Sessions ---------------------------------------------------------------------------------

    /** Who an account is signed in as, with the level and rights the server last gave. */
    public static final class Session {
        public final long userId;
        public final String token;
        public final int level;
        public final String levelName;
        public final Set<String> rights;

        Session(long userId, String token, int level, String levelName, Set<String> rights) {
            this.userId = userId;
            this.token = token;
            this.level = level;
            this.levelName = levelName;
            this.rights = rights;
        }

        public boolean can(String right) {
            return rights.contains(right);
        }

        /** Whether this is someone of the team, with any of the rights. */
        public boolean isStaff() {
            return !rights.isEmpty();
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Session getSession(int account) {
        final long userId = UserConfig.getInstance(account).getClientUserId();
        if (userId == 0) {
            return null;
        }
        final SharedPreferences prefs = prefs();
        final String token = prefs.getString("token_" + userId, null);
        if (token == null) {
            return null;
        }
        final Set<String> rights = new HashSet<>();
        for (String right : prefs.getString("rights_" + userId, "").split(",")) {
            if (!right.isEmpty()) {
                rights.add(right);
            }
        }
        return new Session(userId, token, prefs.getInt("level_" + userId, 0), prefs.getString("level_name_" + userId, ""), rights);
    }

    /** Whether the account is signed in with a right, as the server last said. */
    public static boolean can(int account, String right) {
        final Session session = getSession(account);
        return session != null && session.can(right);
    }

    private static void saveUser(long userId, String token, JSONObject user) {
        final StringBuilder rights = new StringBuilder();
        final JSONObject rightsJson = user.optJSONObject("rights");
        if (rightsJson != null) {
            final JSONArray names = rightsJson.names();
            for (int i = 0; names != null && i < names.length(); i++) {
                final String name = names.optString(i);
                if (rightsJson.optBoolean(name)) {
                    if (rights.length() > 0) {
                        rights.append(',');
                    }
                    rights.append(name);
                }
            }
        }
        final SharedPreferences.Editor editor = prefs().edit()
                .putInt("level_" + userId, user.optInt("level"))
                .putString("level_name_" + userId, user.optString("level_name"))
                .putString("rights_" + userId, rights.toString());
        if (token != null) {
            editor.putString("token_" + userId, token);
        }
        editor.apply();
    }

    private static void forget(long userId) {
        prefs().edit()
                .remove("token_" + userId)
                .remove("level_" + userId)
                .remove("level_name_" + userId)
                .remove("rights_" + userId)
                .apply();
    }

    /** A request as the account's session; an ended session is forgotten. */
    public static void call(int account, String method, String path, JSONObject body, Callback callback) {
        final Session session = getSession(account);
        request(method, path, body, session != null ? session.token : null, response -> {
            if (response.status == 401 && session != null) {
                forget(session.userId);
            }
            if (callback != null) {
                callback.onResponse(response);
            }
        });
    }

    /** Asks the server who the account is now, as levels change in the bot. */
    public static void refresh(int account, Callback callback) {
        final Session session = getSession(account);
        if (session == null) {
            if (callback != null) {
                callback.onResponse(new Response(401, new JSONObject()));
            }
            return;
        }
        call(account, "GET", "/me", null, response -> {
            if (response.ok()) {
                saveUser(session.userId, null, response.json);
            }
            if (callback != null) {
                callback.onResponse(response);
            }
        });
    }

    public static void signOut(int account) {
        final Session session = getSession(account);
        if (session == null) {
            return;
        }
        forget(session.userId);
        request("POST", "/auth/logout", new JSONObject(), session.token, null);
    }

    // --- Who uses glassGram ------------------------------------------------------------------------

    private static final long USER_INFO_KEEP = 5 * 60 * 1000L;
    private static final java.util.HashMap<Long, Response> userInfo = new java.util.HashMap<>();
    private static final java.util.HashMap<Long, Long> userInfoTime = new java.util.HashMap<>();

    /**
     * Whether a Telegram account uses glassGram and on what, as its app's daily reports said
     * (GET /users/<id>), for the team; kept a few minutes, so profiles open without asking again.
     */
    public static void userInfo(int account, long userId, Callback callback) {
        final Long time = userInfoTime.get(userId);
        final Response cached = userInfo.get(userId);
        if (cached != null && time != null && System.currentTimeMillis() - time < USER_INFO_KEEP) {
            callback.onResponse(cached);
            return;
        }
        call(account, "GET", "/users/" + userId, null, response -> {
            if (response.ok()) {
                userInfo.put(userId, response);
                userInfoTime.put(userId, System.currentTimeMillis());
            }
            callback.onResponse(response);
        });
    }

    // --- Bans -------------------------------------------------------------------------------------

    /** An account on this phone banned from glassGram. */
    public static final class Ban {
        public final int account;
        public final long userId;
        public final String reason;

        Ban(int account, long userId, String reason) {
            this.account = account;
            this.userId = userId;
            this.reason = reason;
        }
    }

    /** How old the list may be when the app opens; the server keeps it for a minute too. */
    public static final long BANS_REFRESH = 60 * 1000L;
    private static long bansLoadedAt;
    private static boolean bansLoading;

    /**
     * Downloads the banned accounts (GET /bans/list), hashed like the team, unless lately; kept, so
     * they hold from the app's start. onLoaded runs once they are in.
     */
    public static void loadBans(long maxAge, Runnable onLoaded) {
        if (bansLoading || bansLoadedAt != 0 && System.currentTimeMillis() - bansLoadedAt < maxAge) {
            return;
        }
        bansLoading = true;
        request("GET", "/bans/list", null, null, response -> {
            bansLoading = false;
            final JSONObject bans = response.json.optJSONObject("bans");
            final String salt = response.json.optString("salt", "");
            if (!response.ok() || bans == null || salt.isEmpty()) {
                return;
            }
            bansLoadedAt = System.currentTimeMillis();
            prefs().edit().putString("bans_salt", salt).putString("bans", bans.toString()).apply();
            if (onLoaded != null) {
                onLoaded.run();
            }
        });
    }

    /** Why a Telegram account is banned from glassGram, by the kept list, or null when it isn't. */
    public static String banReason(long userId) {
        final SharedPreferences prefs = prefs();
        final String salt = prefs.getString("bans_salt", null);
        final String kept = prefs.getString("bans", null);
        if (userId == 0 || salt == null || kept == null) {
            return null;
        }
        try {
            final JSONObject bans = new JSONObject(kept);
            final String hash = Utilities.bytesToHex(Utilities.computeSHA256((salt + ":" + userId).getBytes(StandardCharsets.UTF_8))).toLowerCase();
            return bans.has(hash) ? bans.optString(hash, "") : null;
        } catch (JSONException e) {
            return null;
        }
    }

    /** The first account signed in on this phone that is banned, or null. */
    public static Ban findBan() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            final UserConfig config = UserConfig.getInstance(a);
            if (!config.isClientActivated()) {
                continue;
            }
            final String reason = banReason(config.getClientUserId());
            if (reason != null) {
                return new Ban(a, config.getClientUserId(), reason);
            }
        }
        return null;
    }

    // --- The team ---------------------------------------------------------------------------------

    private static final long TEAM_REFRESH = 5 * 60 * 1000L;
    private static boolean teamLoading;

    /**
     * Whether the account is on glassGram's team, known before signing in: the server lists the
     * team's ids hashed, and the app looks itself up in the list, sending no id of its own.
     * Signed in with rights counts too.
     */
    public static boolean isTeam(int account) {
        final Session session = getSession(account);
        if (session != null && session.isStaff()) {
            return true;
        }
        final long userId = UserConfig.getInstance(account).getClientUserId();
        final SharedPreferences prefs = prefs();
        final String salt = prefs.getString("team_salt", null);
        final String members = prefs.getString("team_members", null);
        if (userId == 0 || salt == null || members == null) {
            return false;
        }
        final String hash = Utilities.bytesToHex(Utilities.computeSHA256((salt + ":" + userId).getBytes(StandardCharsets.UTF_8))).toLowerCase();
        for (String member : members.split(",")) {
            if (member.equals(hash)) {
                return true;
            }
        }
        return false;
    }

    /** Downloads the team's list, unless it was lately; the callback runs if it changed. */
    public static void loadTeam(Runnable onChanged) {
        final SharedPreferences prefs = prefs();
        if (teamLoading || System.currentTimeMillis() - prefs.getLong("team_loaded", 0) < TEAM_REFRESH) {
            return;
        }
        teamLoading = true;
        request("GET", "/team", null, null, response -> {
            teamLoading = false;
            final String salt = response.json.optString("salt", "");
            final JSONArray list = response.json.optJSONArray("members");
            if (!response.ok() || salt.isEmpty() || list == null) {
                return;
            }
            final StringBuilder members = new StringBuilder();
            for (int i = 0; i < list.length(); i++) {
                if (i > 0) {
                    members.append(',');
                }
                members.append(list.optString(i).toLowerCase());
            }
            final boolean changed = !salt.equals(prefs.getString("team_salt", null))
                    || !members.toString().equals(prefs.getString("team_members", null));
            prefs.edit()
                    .putString("team_salt", salt)
                    .putString("team_members", members.toString())
                    .putLong("team_loaded", System.currentTimeMillis())
                    .apply();
            if (changed && onChanged != null) {
                onChanged.run();
            }
        });
    }

    // --- Signing in -------------------------------------------------------------------------------

    /** Keeps a session the server gave, if it is of this account's Telegram user. */
    private static Response accept(int account, Response response) {
        if (!response.ok()) {
            return response;
        }
        final JSONObject user = response.json.optJSONObject("user");
        final String token = response.json.optString("token", null);
        final long userId = UserConfig.getInstance(account).getClientUserId();
        if (user == null || token == null) {
            return new Response(502, new JSONObject());
        }
        if (user.optLong("id") != userId) {
            // a code another account asked for: this account isn't signed in by it
            request("POST", "/auth/logout", new JSONObject(), token, null);
            final JSONObject error = new JSONObject();
            try {
                error.put("error", "other_account");
                error.put("message", LocaleController.getString(R.string.GlassgramApiOtherAccount));
            } catch (JSONException ignore) {
            }
            return new Response(409, error);
        }
        saveUser(userId, token, user);
        return response;
    }

    /** Finds the bot, for its Mini App and for /login; the callback gets 0 when it can't. */
    public static void resolveBot(int account, Utilities.Callback<Long> callback) {
        MessagesController.getInstance(account).getUserNameResolver().resolve(BOT_USERNAME, id ->
                AndroidUtilities.runOnUIThread(() -> callback.run(id == null ? 0L : id)));
    }

    /**
     * Signs in by Telegram's signature: asks for the bot's Main Mini App and sends its launch data.
     * Fails with "no_mini_app" when the bot has none, for signing in by a code instead.
     */
    public static void signInWithTelegram(int account, Callback callback) {
        resolveBot(account, botId -> {
            if (botId == 0) {
                callback.onResponse(new Response(0, new JSONObject()));
                return;
            }
            final MessagesController messagesController = MessagesController.getInstance(account);
            final TLRPC.TL_messages_requestMainWebView req = new TLRPC.TL_messages_requestMainWebView();
            req.bot = messagesController.getInputUser(botId);
            req.peer = messagesController.getInputPeer(botId);
            req.platform = "android";
            ConnectionsManager.getInstance(account).sendRequest(req, (result, error) -> AndroidUtilities.runOnUIThread(() -> {
                final String initData = result instanceof TLRPC.TL_webViewResultUrl ? initDataOf(((TLRPC.TL_webViewResultUrl) result).url) : null;
                if (initData == null) {
                    final JSONObject json = new JSONObject();
                    try {
                        json.put("error", "no_mini_app");
                        json.put("message", error != null ? error.text : "no launch data");
                    } catch (JSONException ignore) {
                    }
                    callback.onResponse(new Response(400, json));
                    return;
                }
                final JSONObject body = new JSONObject();
                try {
                    body.put("init_data", initData);
                } catch (JSONException ignore) {
                }
                request("POST", "/auth/telegram", body, null, response -> callback.onResponse(accept(account, response)));
            }));
        });
    }

    /** The signed launch data in a Mini App's URL: the tgWebAppData of its fragment. */
    static String initDataOf(String url) {
        if (url == null) {
            return null;
        }
        final int fragment = url.indexOf('#');
        if (fragment < 0) {
            return null;
        }
        for (String part : url.substring(fragment + 1).split("&")) {
            if (part.startsWith("tgWebAppData=")) {
                final String data = Uri.decode(part.substring("tgWebAppData=".length()));
                return TextUtils.isEmpty(data) ? null : data;
            }
        }
        return null;
    }

    /** Asks the bot for a sign-in code: sends it /login from this account. */
    public static void requestCode(int account, long botId) {
        SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of("/login", botId));
    }

    /** The sign-in code in a message of the bot, or null. */
    public static String codeIn(MessageObject message, long botId) {
        if (message == null || message.getDialogId() != botId || message.isOut() || message.messageOwner == null
                || message.messageOwner.message == null || !message.messageOwner.message.contains("glassGram")) {
            return null;
        }
        final java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?<!\\d)(\\d{8})(?!\\d)").matcher(message.messageOwner.message);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static void signInWithCode(int account, String code, Callback callback) {
        final JSONObject body = new JSONObject();
        try {
            body.put("code", code);
        } catch (JSONException ignore) {
        }
        request("POST", "/auth/code", body, null, response -> callback.onResponse(accept(account, response)));
    }

    // --- JSON -------------------------------------------------------------------------------------

    /** {"key": value, …} built in one line. */
    public static JSONObject json(Object... pairs) {
        final JSONObject json = new JSONObject();
        try {
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                json.put(String.valueOf(pairs[i]), pairs[i + 1] == null ? JSONObject.NULL : pairs[i + 1]);
            }
        } catch (JSONException ignore) {
        }
        return json;
    }
}
