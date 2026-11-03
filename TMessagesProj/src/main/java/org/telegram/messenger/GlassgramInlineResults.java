package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * glassGram's Inline Results on Android: the engine's options from the settings, and the exchange
 * rates it converts money with. Fiat comes from open.er-api.com, crypto from CoinGecko, Telegram
 * Stars at the price Telegram sells them; kept for a few hours and asked again when needed.
 */
public final class GlassgramInlineResults {

    private GlassgramInlineResults() {
    }

    private static final String PREFS = "glassgram_rates";
    private static final long FRESH_MS = 6 * 60 * 60 * 1000L;
    private static final long RETRY_MS = 10 * 60 * 1000L;
    private static final String FIAT_URL = "https://open.er-api.com/v6/latest/USD";
    /** CoinGecko's id, then the codes it goes by: Toncoin is called Gram now. */
    private static final String[][] CRYPTO = {
        {"bitcoin", "BTC"}, {"ethereum", "ETH"}, {"the-open-network", "GRAM", "TON"}, {"tether", "USDT"},
        {"usd-coin", "USDC"}, {"solana", "SOL"}, {"binancecoin", "BNB"}, {"ripple", "XRP"}, {"dogecoin", "DOGE"},
        {"tron", "TRX"}, {"litecoin", "LTC"}, {"notcoin", "NOT"}, {"cardano", "ADA"},
    };

    private static HashMap<String, Double> rates;
    private static long ratesTime;
    private static boolean ratesLoaded, loading;
    private static long lastAttempt;
    private static final ArrayList<Runnable> waiting = new ArrayList<>();
    /** The rates with Telegram Stars added at the price of the time, rebuilt when either changes. */
    private static Map<String, Double> withStars;
    private static HashMap<String, Double> withStarsBase;
    private static double withStarsRate;

    /** The options for the engine as the settings are now. */
    public static GlassgramInlineEngine.Options options() {
        final GlassgramInlineEngine.Options o = new GlassgramInlineEngine.Options();
        o.calculator = GlassgramConfig.inlineCalculator;
        o.currencies = GlassgramConfig.inlineCurrencies;
        o.units = GlassgramConfig.inlineUnits;
        o.timeZones = GlassgramConfig.inlineTimeZones;
        o.groupDigits = GlassgramConfig.inlineGroupDigits;
        o.radians = GlassgramConfig.inlineRadians;
        o.imperial = GlassgramConfig.inlineMeasure == GlassgramConfig.INLINE_MEASURE_IMPERIAL
            || GlassgramConfig.inlineMeasure == GlassgramConfig.INLINE_MEASURE_AUTO && imperialByRegion();
        o.defaultCurrency = defaultCurrency();
        o.russian = isRussian();
        o.use24h = LocaleController.is24HourFormat;
        o.zone = TimeZone.getDefault();
        o.now = System.currentTimeMillis();
        if (o.currencies) {
            o.perUsd = perUsd();
        }
        return o;
    }

    /** The result to show after the "=" right before the cursor, or null. */
    public static String resultFor(CharSequence text, int cursor) {
        if (!GlassgramConfig.inlineResults || text == null || cursor < 2 || cursor > text.length() || text.charAt(cursor - 1) != '=') {
            return null;
        }
        return GlassgramInlineEngine.resultFor(text, cursor, options());
    }

    private static boolean isRussian() {
        try {
            final String lang = LocaleController.getInstance().getCurrentLocale().getLanguage();
            return "ru".equals(lang) || "uk".equals(lang) || "be".equals(lang);
        } catch (Exception e) {
            return false;
        }
    }

    /** Miles and Fahrenheit where they are the usual ones: the United States, Liberia, Myanmar. */
    public static boolean imperialByRegion() {
        final String country = Locale.getDefault().getCountry();
        return "US".equals(country) || "LR".equals(country) || "MM".equals(country);
    }

    public static String defaultCurrency() {
        final String chosen = GlassgramConfig.inlineCurrency;
        if (!TextUtils.isEmpty(chosen) && !"auto".equals(chosen)) {
            return chosen;
        }
        return autoCurrency();
    }

    /** The currency of the country the account's phone number is from; the region's otherwise. */
    public static String autoCurrency() {
        try {
            final TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
            final String phone = user != null ? user.phone : null;
            if (!TextUtils.isEmpty(phone)) {
                final String digits = phone.replaceAll("[^0-9]", "");
                for (int length = Math.min(4, digits.length()); length >= 1; length--) {
                    final String currency = PHONE_CURRENCIES.get(digits.substring(0, length));
                    if (currency != null) {
                        return currency;
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            final Currency currency = Currency.getInstance(Locale.getDefault());
            if (currency != null) {
                return currency.getCurrencyCode();
            }
        } catch (Exception ignore) {
        }
        return "USD";
    }

    /** The currencies offered for the default one, the usual ones first. */
    public static final String[] CURRENCY_CHOICES = {
        "USD", "EUR", "RUB", "UAH", "KZT", "BYN", "UZS", "KGS", "AMD", "AZN", "GEL", "TJS", "MDL", "GBP", "CHF",
        "PLN", "CZK", "TRY", "ILS", "AED", "INR", "CNY", "JPY", "KRW", "THB", "VND", "IDR", "BRL", "CAD", "AUD",
        "BTC", "GRAM", "USDT", "XTR",
    };

    private static final HashMap<String, String> PHONE_CURRENCIES = new HashMap<>();

    private static void phone(String currency, String... prefixes) {
        for (String p : prefixes) {
            PHONE_CURRENCIES.put(p, currency);
        }
    }

    static {
        phone("RUB", "7");
        phone("KZT", "76", "77");
        phone("UAH", "380");
        phone("BYN", "375");
        phone("AMD", "374");
        phone("AZN", "994");
        phone("GEL", "995");
        phone("KGS", "996");
        phone("TJS", "992");
        phone("TMT", "993");
        phone("UZS", "998");
        phone("MDL", "373");
        phone("USD", "1");
        phone("GBP", "44");
        phone("EUR", "30", "31", "32", "33", "34", "39", "43", "49", "351", "352", "353", "356", "357", "358", "370", "371", "372", "377", "385", "386", "421");
        phone("CHF", "41", "423");
        phone("PLN", "48");
        phone("CZK", "420");
        phone("HUF", "36");
        phone("RON", "40");
        phone("BGN", "359");
        phone("RSD", "381");
        phone("SEK", "46");
        phone("NOK", "47");
        phone("DKK", "45");
        phone("TRY", "90");
        phone("ILS", "972");
        phone("AED", "971");
        phone("SAR", "966");
        phone("QAR", "974");
        phone("KWD", "965");
        phone("EGP", "20");
        phone("ZAR", "27");
        phone("NGN", "234");
        phone("INR", "91");
        phone("PKR", "92");
        phone("BDT", "880");
        phone("LKR", "94");
        phone("IRR", "98");
        phone("CNY", "86");
        phone("HKD", "852");
        phone("TWD", "886");
        phone("JPY", "81");
        phone("KRW", "82");
        phone("MNT", "976");
        phone("THB", "66");
        phone("VND", "84");
        phone("IDR", "62");
        phone("MYR", "60");
        phone("PHP", "63");
        phone("SGD", "65");
        phone("AUD", "61");
        phone("NZD", "64");
        phone("BRL", "55");
        phone("MXN", "52");
        phone("ARS", "54");
        phone("CLP", "56");
        phone("COP", "57");
        phone("PEN", "51");
    }

    // ------------------------------------------------------------------------------------------
    // Rates

    /** When the rates were taken, 0 when there are none yet. */
    public static synchronized long ratesTime() {
        loadSaved();
        return ratesTime;
    }

    public static synchronized boolean hasRates() {
        loadSaved();
        return rates != null && !rates.isEmpty();
    }

    /** The rates the engine converts with, Stars included; asks for new ones when they are old. */
    public static synchronized Map<String, Double> perUsd() {
        loadSaved();
        refresh(false, null);
        double starsUsd = 0;
        try {
            // what a star costs: Telegram's rate per 1000 stars, in cents
            final float rate1000 = MessagesController.getInstance(UserConfig.selectedAccount).starsUsdSellRate1000;
            starsUsd = rate1000 / 100000.0;
        } catch (Exception ignore) {
        }
        if (rates == null) {
            if (starsUsd <= 0) {
                return null;
            }
            final HashMap<String, Double> map = new HashMap<>();
            map.put("USD", 1.0);
            map.put("XTR", 1 / starsUsd);
            return map;
        }
        if (withStars == null || withStarsBase != rates || withStarsRate != starsUsd) {
            final HashMap<String, Double> map = new HashMap<>(rates);
            if (starsUsd > 0) {
                map.put("XTR", 1 / starsUsd);
            }
            withStars = Collections.unmodifiableMap(map);
            withStarsBase = rates;
            withStarsRate = starsUsd;
        }
        return withStars;
    }

    private static void loadSaved() {
        if (ratesLoaded) {
            return;
        }
        ratesLoaded = true;
        try {
            final SharedPreferences p = prefs();
            final String json = p.getString("rates", null);
            if (json != null) {
                rates = parseSaved(new JSONObject(json));
                ratesTime = p.getLong("time", 0);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static HashMap<String, Double> parseSaved(JSONObject json) {
        final HashMap<String, Double> map = new HashMap<>();
        final Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            final String key = keys.next();
            final double v = json.optDouble(key, Double.NaN);
            if (v > 0 && !Double.isInfinite(v)) {
                map.put(key, v);
            }
        }
        return map;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Asks for the rates again when they are old (or always, when forced); done runs on the UI
     * thread once they came, or did not.
     */
    public static synchronized void refresh(boolean force, Runnable done) {
        loadSaved();
        final long now = System.currentTimeMillis();
        if (loading) {
            if (done != null) {
                waiting.add(done);
            }
            return;
        }
        final boolean fresh = rates != null && now - ratesTime < FRESH_MS;
        if (!force && (fresh || now - lastAttempt < RETRY_MS) || !GlassgramConfig.inlineResults || !GlassgramConfig.inlineCurrencies) {
            if (done != null) {
                AndroidUtilities.runOnUIThread(done);
            }
            return;
        }
        loading = true;
        lastAttempt = now;
        if (done != null) {
            waiting.add(done);
        }
        Utilities.externalNetworkQueue.postRunnable(() -> {
            final HashMap<String, Double> map = new HashMap<>();
            try {
                final String fiat = get(FIAT_URL);
                if (fiat != null) {
                    final JSONObject list = new JSONObject(fiat).optJSONObject("rates");
                    if (list != null) {
                        map.putAll(parseSaved(list));
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            try {
                final StringBuilder ids = new StringBuilder();
                for (String[] c : CRYPTO) {
                    if (ids.length() > 0) {
                        ids.append(',');
                    }
                    ids.append(c[0]);
                }
                final String crypto = get("https://api.coingecko.com/api/v3/simple/price?ids=" + ids + "&vs_currencies=usd");
                if (crypto != null) {
                    final JSONObject json = new JSONObject(crypto);
                    for (String[] c : CRYPTO) {
                        final JSONObject coin = json.optJSONObject(c[0]);
                        final double usd = coin != null ? coin.optDouble("usd", Double.NaN) : Double.NaN;
                        if (usd > 0) {
                            for (int i = 1; i < c.length; i++) {
                                map.put(c[i], 1 / usd);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            final boolean ok = map.containsKey("EUR") || map.containsKey("BTC");
            final long time = System.currentTimeMillis();
            if (ok) {
                map.put("USD", 1.0);
                // the ones not asked for this time are kept from before
                synchronized (GlassgramInlineResults.class) {
                    if (rates != null) {
                        for (Map.Entry<String, Double> e : rates.entrySet()) {
                            if (!map.containsKey(e.getKey())) {
                                map.put(e.getKey(), e.getValue());
                            }
                        }
                    }
                }
                try {
                    prefs().edit().putString("rates", new JSONObject(new HashMap<String, Object>(map)).toString()).putLong("time", time).apply();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                final ArrayList<Runnable> callbacks;
                synchronized (GlassgramInlineResults.class) {
                    if (ok) {
                        rates = map;
                        ratesTime = time;
                    }
                    loading = false;
                    callbacks = new ArrayList<>(waiting);
                    waiting.clear();
                }
                for (Runnable r : callbacks) {
                    r.run();
                }
            });
        });
    }

    private static String get(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "glassGram");
            if (connection.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = connection.getInputStream()) {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                final byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    if (out.size() > 1024 * 1024) {
                        return null;
                    }
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
