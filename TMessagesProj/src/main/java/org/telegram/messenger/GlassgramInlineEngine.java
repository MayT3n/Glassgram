package org.telegram.messenger;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * glassGram's inline results: what the message field shows in gray right after "=" (as
 * exteraGram's Inline Results). Sums with functions, percents and shorthands, money (currencies,
 * crypto and Telegram Stars), units of measure and time zones. Plain Java: the field asks
 * {@link #resultFor}, the settings page works its examples out with {@link #evaluate}.
 */
public final class GlassgramInlineEngine {

    private GlassgramInlineEngine() {
    }

    /** What is worked out and how it is written. */
    public static final class Options {
        public boolean calculator = true;
        public boolean currencies = true;
        public boolean units = true;
        public boolean timeZones = false;
        public boolean groupDigits = true;
        /** sin, cos and tan take radians rather than degrees. */
        public boolean radians;
        /** Miles, pounds and Fahrenheit are the user's own units, rather than kilometers. */
        public boolean imperial;
        /** Units are written in Russian when nothing in the sum says otherwise. */
        public boolean russian;
        public boolean use24h = true;
        /** Where money goes without "to" or "in". */
        public String defaultCurrency = "USD";
        /** How much of each a dollar buys: ISO codes, crypto (BTC, GRAM...) and XTR, Telegram Stars. */
        public Map<String, Double> perUsd;
        public TimeZone zone = TimeZone.getDefault();
        public long now = System.currentTimeMillis();
    }

    private static final char NBSP = ' ';
    private static final int MAX_LINE = 300;
    private static final int MAX_WORDS = 12;

    /**
     * The gray result for the field: the text before the cursor must end with "=" with nothing
     * after it on its line. The sum is the end of the line before the "=", words before it left
     * out ("so it's 2+2="). Starts with a space when the "=" has one before it ("2 + 2 = 4").
     */
    public static String resultFor(CharSequence text, int cursor, Options o) {
        if (text == null || o == null || cursor < 2 || cursor > text.length() || text.charAt(cursor - 1) != '=') {
            return null;
        }
        if (cursor < text.length() && text.charAt(cursor) != '\n') {
            return null;
        }
        int lineStart = cursor - 1;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }
        String line = text.subSequence(Math.max(lineStart, cursor - 1 - MAX_LINE), cursor - 1).toString();
        final boolean spaced = line.length() > 0 && isSpace(line.charAt(line.length() - 1));
        int end = line.length();
        while (end > 0 && isSpace(line.charAt(end - 1))) {
            end--;
        }
        line = line.substring(0, end);
        if (line.isEmpty() || line.charAt(line.length() - 1) == '=') {
            return null;
        }
        // where a sum can start: the line's start and after every space, the last words only
        final ArrayList<Integer> starts = new ArrayList<>();
        for (int i = line.length() - 1; i >= 0 && starts.size() < MAX_WORDS; i--) {
            if (i == 0 || isSpace(line.charAt(i - 1)) && !isSpace(line.charAt(i))) {
                starts.add(i);
            }
        }
        // the longest that works: "100 dollars to euros" rather than "euros"
        for (int i = starts.size() - 1; i >= 0; i--) {
            final String result = evaluate(line.substring(starts.get(i)), o);
            if (result != null) {
                return spaced ? " " + result : result;
            }
        }
        return null;
    }

    /** The result of a whole sum, conversion or time, or null when it is none or tells nothing new. */
    public static String evaluate(String expression, Options o) {
        if (expression == null) {
            return null;
        }
        final String text = expression.trim();
        if (text.isEmpty() || text.length() > MAX_LINE) {
            return null;
        }
        boolean cyrillic = false, latin = false;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c >= 'Ѐ' && c <= 'ӿ') {
                cyrillic = true;
            } else if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
                latin = true;
            }
        }
        final boolean ru = cyrillic || !latin && o.russian;
        try {
            if (o.timeZones) {
                final String time = Times.evaluate(text, o);
                if (time != null) {
                    return time;
                }
            }
            if (!o.calculator && !o.currencies && !o.units) {
                return null;
            }
            final Parser parser = new Parser(text, o);
            parser.parse();
            final String result = parser.result(ru);
            // "1 h 30 min" is already what it would show
            return result == null || squeeze(result).equals(squeeze(text)) ? null : result;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String squeeze(String s) {
        final StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (!isSpace(c)) {
                b.append(Character.toLowerCase(c));
            }
        }
        return b.toString();
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == NBSP || c == ' ' || c == ' ' || c == '\t';
    }

    // ------------------------------------------------------------------------------------------
    // Units

    private static final int LENGTH = 1, MASS = 2, TEMPERATURE = 3, SPEED = 4, AREA = 5, VOLUME = 6, DURATION = 7, DATA = 8, MONEY = 9;
    private static final int NEUTRAL = 0, METRIC = 1, IMPERIAL = 2;

    private static final class U {
        final int dim, system;
        final String en, ru;
        final double factor, offset;
        final String code;

        U(int dim, int system, String en, String ru, double factor, double offset) {
            this.dim = dim;
            this.system = system;
            this.en = en;
            this.ru = ru;
            this.factor = factor;
            this.offset = offset;
            this.code = null;
        }

        U(String code) {
            dim = MONEY;
            system = NEUTRAL;
            this.code = code;
            final String symbol = CURRENCY_SYMBOLS.get(code);
            en = ru = symbol != null ? symbol : code;
            factor = 1;
            offset = 0;
        }

        double toBase(double v) {
            return v * factor + offset;
        }

        double fromBase(double b) {
            return (b - offset) / factor;
        }

        String name(boolean russian) {
            return russian ? ru : en;
        }
    }

    private static final class Alias {
        final String text;
        final boolean caseSensitive, stem;
        final U unit;
        final String money;
        final double multiplier;

        Alias(String text, U unit, String money, double multiplier) {
            boolean cs = false, stem = false;
            if (text.startsWith("!")) {
                cs = true;
                text = text.substring(1);
            }
            if (text.endsWith("*") && text.length() > 1) {
                stem = true;
                text = text.substring(0, text.length() - 1);
            }
            this.text = text;
            this.caseSensitive = cs;
            this.stem = stem;
            this.unit = unit;
            this.money = money;
            this.multiplier = multiplier;
        }
    }

    private static final ArrayList<Alias> UNIT_ALIASES = new ArrayList<>();
    private static final ArrayList<Alias> MULTIPLIERS = new ArrayList<>();
    private static final Map<String, String> CURRENCY_SYMBOLS = new HashMap<>();
    private static final Set<String> CRYPTO = new HashSet<>();
    private static final Map<String, U> MONEY_UNITS = new HashMap<>();
    /** The currency symbols written before an amount: "$100", "€5". */
    private static final ArrayList<Alias> PREFIX_MONEY = new ArrayList<>();

    private static final U MM = new U(LENGTH, METRIC, "mm", "мм", 0.001, 0);
    private static final U CM = new U(LENGTH, METRIC, "cm", "см", 0.01, 0);
    private static final U M = new U(LENGTH, METRIC, "m", "м", 1, 0);
    private static final U KM = new U(LENGTH, METRIC, "km", "км", 1000, 0);
    private static final U IN = new U(LENGTH, IMPERIAL, "in", "дюйм.", 0.0254, 0);
    private static final U FT = new U(LENGTH, IMPERIAL, "ft", "фут.", 0.3048, 0);
    private static final U YD = new U(LENGTH, IMPERIAL, "yd", "ярд.", 0.9144, 0);
    private static final U MI = new U(LENGTH, IMPERIAL, "mi", "мили", 1609.344, 0);
    private static final U NMI = new U(LENGTH, NEUTRAL, "nmi", "мор. мили", 1852, 0);

    private static final U MG = new U(MASS, METRIC, "mg", "мг", 1e-6, 0);
    private static final U G = new U(MASS, METRIC, "g", "г", 0.001, 0);
    private static final U KG = new U(MASS, METRIC, "kg", "кг", 1, 0);
    private static final U T = new U(MASS, METRIC, "t", "т", 1000, 0);
    private static final U OZ = new U(MASS, IMPERIAL, "oz", "унц.", 0.028349523125, 0);
    private static final U LB = new U(MASS, IMPERIAL, "lb", "фунт.", 0.45359237, 0);
    private static final U ST = new U(MASS, IMPERIAL, "st", "стоун", 6.35029318, 0);

    private static final U C = new U(TEMPERATURE, METRIC, "°C", "°C", 1, 273.15);
    private static final U F = new U(TEMPERATURE, IMPERIAL, "°F", "°F", 5.0 / 9.0, 273.15 - 32 * 5.0 / 9.0);
    private static final U K = new U(TEMPERATURE, NEUTRAL, "K", "K", 1, 0);

    private static final U KMH = new U(SPEED, METRIC, "km/h", "км/ч", 1 / 3.6, 0);
    private static final U MS = new U(SPEED, METRIC, "m/s", "м/с", 1, 0);
    private static final U MPH = new U(SPEED, IMPERIAL, "mph", "миль/ч", 0.44704, 0);
    private static final U FPS = new U(SPEED, IMPERIAL, "ft/s", "фут/с", 0.3048, 0);
    private static final U KN = new U(SPEED, NEUTRAL, "kn", "уз", 1852 / 3600.0, 0);

    private static final U MM2 = new U(AREA, METRIC, "mm²", "мм²", 1e-6, 0);
    private static final U CM2 = new U(AREA, METRIC, "cm²", "см²", 1e-4, 0);
    private static final U M2 = new U(AREA, METRIC, "m²", "м²", 1, 0);
    private static final U HA = new U(AREA, METRIC, "ha", "га", 1e4, 0);
    private static final U KM2 = new U(AREA, METRIC, "km²", "км²", 1e6, 0);
    private static final U IN2 = new U(AREA, IMPERIAL, "in²", "дюйм²", 0.00064516, 0);
    private static final U FT2 = new U(AREA, IMPERIAL, "ft²", "фут²", 0.09290304, 0);
    private static final U YD2 = new U(AREA, IMPERIAL, "yd²", "ярд²", 0.83612736, 0);
    private static final U AC = new U(AREA, IMPERIAL, "ac", "акр.", 4046.8564224, 0);
    private static final U MI2 = new U(AREA, IMPERIAL, "mi²", "миль²", 2589988.110336, 0);

    private static final U ML = new U(VOLUME, METRIC, "ml", "мл", 0.001, 0);
    private static final U L = new U(VOLUME, METRIC, "l", "л", 1, 0);
    private static final U M3 = new U(VOLUME, METRIC, "m³", "м³", 1000, 0);
    private static final U TSP = new U(VOLUME, IMPERIAL, "tsp", "ч. л.", 0.00492892159375, 0);
    private static final U TBSP = new U(VOLUME, IMPERIAL, "tbsp", "ст. л.", 0.01478676478125, 0);
    private static final U FLOZ = new U(VOLUME, IMPERIAL, "fl oz", "жидк. унц.", 0.0295735295625, 0);
    private static final U CUP = new U(VOLUME, IMPERIAL, "cups", "чаш.", 0.2365882365, 0);
    private static final U PT = new U(VOLUME, IMPERIAL, "pt", "пинт.", 0.473176473, 0);
    private static final U QT = new U(VOLUME, IMPERIAL, "qt", "кварт.", 0.946352946, 0);
    private static final U GAL = new U(VOLUME, IMPERIAL, "gal", "галл.", 3.785411784, 0);

    private static final U MSEC = new U(DURATION, NEUTRAL, "ms", "мс", 0.001, 0);
    private static final U SEC = new U(DURATION, NEUTRAL, "s", "с", 1, 0);
    private static final U MIN = new U(DURATION, NEUTRAL, "min", "мин", 60, 0);
    private static final U HOUR = new U(DURATION, NEUTRAL, "h", "ч", 3600, 0);
    private static final U DAY = new U(DURATION, NEUTRAL, "d", "д", 86400, 0);
    private static final U WEEK = new U(DURATION, NEUTRAL, "wk", "нед", 604800, 0);

    private static final U BYTE = new U(DATA, NEUTRAL, "B", "Б", 1, 0);
    private static final U KBYTE = new U(DATA, NEUTRAL, "KB", "КБ", 1024, 0);
    private static final U MBYTE = new U(DATA, NEUTRAL, "MB", "МБ", 1024 * 1024, 0);
    private static final U GBYTE = new U(DATA, NEUTRAL, "GB", "ГБ", 1024.0 * 1024 * 1024, 0);
    private static final U TBYTE = new U(DATA, NEUTRAL, "TB", "ТБ", 1024.0 * 1024 * 1024 * 1024, 0);

    private static void unit(U u, String... aliases) {
        for (String a : aliases) {
            UNIT_ALIASES.add(new Alias(a, u, null, 0));
        }
    }

    private static void money(String code, String symbol, String... aliases) {
        if (symbol != null) {
            CURRENCY_SYMBOLS.put(code, symbol);
        }
        for (String a : aliases) {
            final Alias alias = new Alias(a, null, code, 0);
            UNIT_ALIASES.add(alias);
            if (!Character.isLetter(alias.text.charAt(0))) {
                PREFIX_MONEY.add(alias);
            }
        }
    }

    private static void crypto(String code, String... aliases) {
        CRYPTO.add(code);
        money(code, null, aliases);
    }

    private static void multiplier(double value, String... aliases) {
        for (String a : aliases) {
            MULTIPLIERS.add(new Alias(a, null, null, value));
        }
    }

    static {
        unit(MM, "mm", "millimeter*", "millimetre*", "мм", "миллиметр*");
        unit(CM, "cm", "centimeter*", "centimetre*", "см", "сантиметр*");
        unit(M, "m", "meter", "meters", "metre", "metres", "м", "метр", "метра", "метров", "метрах", "метры");
        unit(KM, "km", "kilometer*", "kilometre*", "км", "километр*");
        unit(IN, "in", "inch", "inches", "\"", "″", "дюйм*");
        unit(FT, "ft", "foot", "feet", "′", "фут", "фута", "футов", "футах");
        unit(YD, "yd", "yds", "yard*", "ярд*");
        unit(MI, "mi", "mile", "miles", "миля", "мили", "миль", "милях", "милю");
        unit(NMI, "nmi", "nautical mile*", "морск* мил*");

        unit(MG, "mg", "milligram*", "мг", "миллиграмм*");
        unit(G, "g", "gr", "gram", "grams", "gramme*", "г", "гр", "грамм*");
        unit(KG, "kg", "kgs", "kilo", "kilos", "kilogram*", "кг", "кило", "килограмм*");
        unit(T, "t", "tonne*", "ton", "tons", "т", "тонн*", "тонна");
        unit(OZ, "oz", "ounce*", "унци*", "унц");
        unit(LB, "lb", "lbs", "pound", "pounds", "фунт", "фунта", "фунтов", "фунтах");
        unit(ST, "stone", "stones", "стоун*");

        unit(C, "°c", "°с", "℃", "!C", "!С", "celsius", "цельси*", "градус* цельси*", "degrees celsius", "deg c");
        unit(F, "°f", "℉", "!F", "fahrenheit", "фаренгейт*", "градус* фаренгейт*", "degrees fahrenheit", "deg f");
        unit(K, "kelvin*", "кельвин*", "°k");

        unit(KMH, "km/h", "kmh", "kph", "km/hr", "kmph", "км/ч", "кмч", "км/час");
        unit(MS, "m/s", "mps", "м/с", "м/сек");
        unit(MPH, "mph", "mi/h", "миль/ч", "миль в час");
        unit(FPS, "ft/s", "fps");
        unit(KN, "kn", "knot", "knots", "узел", "узла", "узлов", "узлах");

        unit(MM2, "mm²", "mm2", "sq mm", "мм²", "мм2", "кв мм", "кв. мм");
        unit(CM2, "cm²", "cm2", "sq cm", "см²", "см2", "кв см", "кв. см");
        unit(M2, "m²", "m2", "sq m", "sqm", "м²", "м2", "кв м", "кв. м", "квадратн* метр*");
        unit(HA, "ha", "hectare*", "га", "гектар*");
        unit(KM2, "km²", "km2", "sq km", "км²", "км2", "кв км", "кв. км");
        unit(IN2, "in²", "in2", "sq in");
        unit(FT2, "ft²", "ft2", "sq ft", "sqft");
        unit(YD2, "yd²", "yd2", "sq yd");
        unit(AC, "ac", "acre*", "акр*");
        unit(MI2, "mi²", "mi2", "sq mi");

        unit(ML, "ml", "milliliter*", "millilitre*", "мл", "миллилитр*");
        unit(L, "l", "liter*", "litre*", "л", "литр*");
        unit(M3, "m³", "m3", "cubic meter*", "cubic metre*", "м³", "м3", "куб. м", "кубометр*");
        unit(TSP, "tsp", "teaspoon*");
        unit(TBSP, "tbsp", "tablespoon*");
        unit(FLOZ, "fl oz", "floz", "fl. oz", "fluid ounce*");
        unit(CUP, "cup", "cups");
        unit(PT, "pt", "pint*", "пинт*");
        unit(QT, "qt", "quart*", "кварт*");
        unit(GAL, "gal", "gallon*", "галлон*");

        unit(MSEC, "ms", "msec", "millisecond*", "мс", "миллисекунд*");
        unit(SEC, "s", "sec", "secs", "second*", "с", "сек", "секунд*");
        unit(MIN, "min", "mins", "minute*", "мин", "минут*");
        unit(HOUR, "h", "hr", "hrs", "hour*", "ч", "час", "часа", "часов", "часах");
        unit(DAY, "d", "day", "days", "д", "дн", "день", "дня", "дней", "днях", "сут", "сутки", "суток");
        unit(WEEK, "wk", "wks", "week*", "нед", "недел*");

        unit(BYTE, "!B", "byte*", "!Б", "байт*");
        unit(KBYTE, "kb", "kib", "kilobyte*", "кб", "килобайт*");
        unit(MBYTE, "mb", "mib", "megabyte*", "мб", "мегабайт*", "мегов");
        unit(GBYTE, "gb", "gib", "gigabyte*", "гб", "гигабайт*", "гигов");
        unit(TBYTE, "tb", "tib", "terabyte*", "тб", "терабайт*");

        money("USD", "$", "$", "us$", "usd", "dollar*", "buck*", "доллар*", "долл", "долл.", "бакс*");
        money("EUR", "€", "€", "eur", "euro*", "евро");
        money("RUB", "₽", "₽", "rub", "ruble*", "rouble*", "руб", "руб.", "рубл*", "!р", "р.");
        money("GBP", "£", "£", "gbp", "pound sterling", "pounds sterling", "фунт стерлингов", "фунта стерлингов", "фунтов стерлингов", "фунтах стерлингов");
        money("JPY", "¥", "¥", "jpy", "yen", "иен*", "йен*");
        money("CNY", null, "cny", "rmb", "yuan", "юан*", "元");
        money("UAH", "₴", "₴", "uah", "hryvni*", "hryvna", "гривн*", "грн");
        money("KZT", "₸", "₸", "kzt", "tenge", "тенге");
        money("BYN", null, "byn", "белорусск* рубл*", "бел. руб", "бел руб");
        money("TRY", "₺", "₺", "lira", "liras", "лир*");
        money("INR", "₹", "₹", "inr", "rupee*", "рупи*");
        money("KRW", "₩", "₩", "krw", "won");
        money("ILS", "₪", "₪", "ils", "shekel*", "шекел*");
        money("GEL", "₾", "₾", "gel", "lari", "лари");
        money("AZN", "₼", "₼", "azn", "manat*", "манат*");
        money("AMD", "֏", "֏", "amd", "dram", "drams", "драм", "драма", "драмов");
        money("UZS", null, "uzs", "сум", "сума", "сумов");
        money("KGS", null, "kgs", "сом", "сома", "сомов");
        money("TJS", null, "tjs", "сомони");
        money("MDL", null, "mdl", "лей", "леев");
        money("PLN", null, "zł", "pln", "zloty", "zlotys", "злот*");
        money("CHF", null, "chf", "franc*", "франк*");
        money("CAD", null, "cad", "c$");
        money("AUD", null, "aud", "a$");
        money("THB", "฿", "฿", "thb", "baht", "бат", "бата", "батов");
        money("VND", "₫", "₫", "vnd", "dong");
        money("NGN", "₦", "₦", "ngn", "naira");
        money("PHP", "₱", "₱", "php");
        money("BRL", null, "r$", "brl", "реал*");
        money("AED", null, "aed", "dirham*", "дирхам*");
        money("CZK", null, "czk", "kč");
        money("XTR", "⭐", "⭐️", "⭐", "★", "xtr", "star", "stars", "звезд*", "звёзд*", "старс*");
        crypto("BTC", "₿", "btc", "bitcoin*", "биткоин*", "биткойн*", "битк*");
        crypto("ETH", "eth", "ether", "ethereum", "эфир*");
        crypto("GRAM", "!GRAM", "!Gram");
        crypto("TON", "!TON", "!Ton", "toncoin*", "тонкоин*");
        crypto("USDT", "usdt", "tether", "тезер*");
        crypto("USDC", "usdc");
        crypto("SOL", "!SOL", "solana", "солан*");
        crypto("BNB", "bnb");
        crypto("XRP", "xrp", "ripple");
        crypto("DOGE", "doge", "dogecoin*", "доги*", "догикоин*");
        crypto("TRX", "trx", "tron");
        crypto("LTC", "ltc", "litecoin*", "лайткоин*");
        crypto("NOT", "!NOT", "notcoin*", "ноткоин*");
        crypto("ADA", "!ADA", "cardano");
        CURRENCY_SYMBOLS.put("BTC", "BTC");

        multiplier(1e6, "kk", "!KK", "кк", "!КК", "million", "millions", "mln", "mio", "млн", "миллион*");
        multiplier(1e3, "k", "!K", "к", "!К", "thousand", "thousands", "тыс", "тыс.", "тысяч*", "тыща", "тыщ", "косар*");
        multiplier(1e9, "billion", "billions", "bn", "млрд", "миллиард*");
        multiplier(1e12, "trillion", "trillions", "трлн", "триллион*");

        final java.util.Comparator<Alias> longestFirst = (a, b) -> {
            if (a.text.length() != b.text.length()) {
                return b.text.length() - a.text.length();
            }
            return Boolean.compare(b.caseSensitive, a.caseSensitive);
        };
        Collections.sort(UNIT_ALIASES, longestFirst);
        Collections.sort(MULTIPLIERS, longestFirst);
        Collections.sort(PREFIX_MONEY, longestFirst);
    }

    private static U moneyUnit(String code) {
        U u = MONEY_UNITS.get(code);
        if (u == null) {
            u = new U(code);
            MONEY_UNITS.put(code, u);
        }
        return u;
    }

    /** Where the alias ends when it is written at pos, or -1. */
    private static int matchAlias(String s, int pos, Alias a) {
        final String t = a.text;
        if (pos + t.length() > s.length() || !s.regionMatches(!a.caseSensitive, pos, t, 0, t.length())) {
            return -1;
        }
        int end = pos + t.length();
        if (a.stem) {
            while (end < s.length() && Character.isLetter(s.charAt(end))) {
                end++;
            }
        }
        final char last = t.charAt(t.length() - 1);
        if ((Character.isLetterOrDigit(last) || a.stem) && end < s.length() && Character.isLetterOrDigit(s.charAt(end))) {
            return -1;
        }
        return end;
    }

    // ------------------------------------------------------------------------------------------
    // The sum

    private static final class Q {
        double v;
        U unit;
        /** 2, 8 or 16 when it is to be written in that base, as hex(255) is. */
        int base = 10;
        /** A bare percentage, "15%": after + or − it is taken from the number on its left. */
        boolean percent;

        Q(double v, U unit) {
            this.v = v;
            this.unit = unit;
        }
    }

    private static final class Fail extends RuntimeException {
        Fail() {
            super(null, null, false, false);
        }
    }

    private static final String[] CONVERT_WORDS = {"into", "to", "in", "->", "→", "=>", "в", "во", "на"};
    private static final String[] OF_WORDS = {"of", "от", "из"};

    private static final class Parser {
        final String s;
        final Options o;
        int pos;
        /** Something was worked out: an operation, a function, a shorthand, a conversion. */
        boolean op;
        /** "1,5": the result is written with a comma too. */
        boolean comma;
        Q value;
        U target;
        int targetBase;

        Parser(String s, Options o) {
            this.s = s;
            this.o = o;
        }

        Fail fail() {
            return new Fail();
        }

        void parse() {
            value = expression();
            skip();
            if (pos < s.length()) {
                if (!word(CONVERT_WORDS)) {
                    throw fail();
                }
                skip();
                final int b = baseWord();
                if (b != 0) {
                    targetBase = b;
                } else {
                    target = unitAt(true);
                    if (target == null) {
                        throw fail();
                    }
                }
                op = true;
                skip();
                if (pos < s.length()) {
                    throw fail();
                }
            }
        }

        String result(boolean ru) {
            final Q q = value;
            if (Double.isNaN(q.v) || Double.isInfinite(q.v)) {
                return null;
            }
            if (q.unit == null) {
                if (!o.calculator || target != null) {
                    return null;
                }
                final int base = targetBase != 0 ? targetBase : q.base;
                if (base != 10) {
                    return formatBase(q.v, base);
                }
                if (!op) {
                    return null;
                }
                return formatPlain(q.v, comma, o.groupDigits);
            }
            if (targetBase != 0) {
                return null;
            }
            final U from = q.unit;
            if (from.dim == MONEY) {
                if (!o.currencies) {
                    return null;
                }
                final U to = target != null ? target : moneyUnit(o.defaultCurrency);
                if (to.dim != MONEY) {
                    return null;
                }
                final double v = convertMoney(q.v, from.code, to.code);
                if (Double.isNaN(v) || Double.isInfinite(v)) {
                    return null;
                }
                if (!op && to == from) {
                    return null;
                }
                return formatMoney(v, to);
            }
            if (!o.units) {
                return null;
            }
            final double base = from.toBase(q.v);
            final String source = formatUnit(q.v, from, ru);
            final String result;
            if (target != null) {
                if (target.dim != from.dim) {
                    return null;
                }
                result = formatUnit(target.fromBase(base), target, ru);
            } else if (from.dim == DURATION) {
                result = formatDuration(base, ru);
            } else {
                final U to = handy(from, base);
                result = formatUnit(to.fromBase(base), to, ru);
            }
            if (!op && result.equals(source)) {
                return null;
            }
            return result;
        }

        /** The unit to show a value in without "to": the user's own system, at a handy size. */
        U handy(U from, double base) {
            final int own = o.imperial ? IMPERIAL : METRIC;
            final double a = Math.abs(base);
            switch (from.dim) {
                case TEMPERATURE:
                    return own == METRIC ? C : F;
                case SPEED:
                    if (from.system == NEUTRAL || from.system == own) {
                        return from;
                    }
                    return own == METRIC ? KMH : MPH;
                case LENGTH:
                    if (from.system == NEUTRAL) {
                        return from;
                    }
                    if (own == METRIC) {
                        return a < 0.01 ? MM : a < 1 ? CM : a < 1000 ? M : KM;
                    }
                    return a < 0.3048 ? IN : a < 1609.344 ? FT : MI;
                case MASS:
                    if (own == METRIC) {
                        return a < 0.001 ? MG : a < 1 ? G : a < 1000 ? KG : T;
                    }
                    return a < 0.45359237 ? OZ : LB;
                case AREA:
                    if (own == METRIC) {
                        return a < 1e-4 ? MM2 : a < 1 ? CM2 : a < 1e4 ? M2 : a < 1e6 ? HA : KM2;
                    }
                    return a < 0.09290304 ? IN2 : a < 4046.8564224 ? FT2 : a < 2589988.110336 ? AC : MI2;
                case VOLUME:
                    if (own == METRIC) {
                        return a < 1 ? ML : a < 1000 ? L : M3;
                    }
                    return a < 3.785411784 ? FLOZ : GAL;
                case DATA:
                    return a < 1024 ? BYTE : a < 1024 * 1024 ? KBYTE : a < 1024.0 * 1024 * 1024 ? MBYTE : a < 1024.0 * 1024 * 1024 * 1024 ? GBYTE : TBYTE;
                default:
                    return from;
            }
        }

        double convertMoney(double v, String from, String to) {
            if (from.equals(to)) {
                return v;
            }
            final Map<String, Double> rates = o.perUsd;
            if (rates == null) {
                return Double.NaN;
            }
            final Double a = rates.get(from), b = rates.get(to);
            if (a == null || b == null || a <= 0 || b <= 0) {
                return Double.NaN;
            }
            return v / a * b;
        }

        // ---- lexing

        void skip() {
            while (pos < s.length() && isSpace(s.charAt(pos))) {
                pos++;
            }
        }

        boolean peek(char c) {
            skip();
            return pos < s.length() && s.charAt(pos) == c;
        }

        boolean eat(char... options) {
            skip();
            if (pos < s.length()) {
                final char c = s.charAt(pos);
                for (char x : options) {
                    if (c == x) {
                        pos++;
                        return true;
                    }
                }
            }
            return false;
        }

        /** One of the words, as a whole word, case aside. */
        boolean word(String... words) {
            skip();
            for (String w : words) {
                if (s.regionMatches(true, pos, w, 0, w.length())) {
                    final int end = pos + w.length();
                    final boolean letters = Character.isLetter(w.charAt(w.length() - 1));
                    if (!letters || end >= s.length() || !Character.isLetterOrDigit(s.charAt(end))) {
                        pos = end;
                        return true;
                    }
                }
            }
            return false;
        }

        int baseWord() {
            final int save = pos;
            if (word("hex", "hexadecimal")) {
                return 16;
            } else if (word("bin", "binary")) {
                return 2;
            } else if (word("oct", "octal")) {
                return 8;
            } else if (word("dec", "decimal")) {
                return 10;
            }
            pos = save;
            return 0;
        }

        /** A unit or currency written at pos, moving past it; null when there is none. */
        U unitAt(boolean target) {
            skip();
            for (int i = 0, n = UNIT_ALIASES.size(); i < n; i++) {
                final Alias a = UNIT_ALIASES.get(i);
                if (a.money != null ? !o.currencies : !o.units) {
                    continue;
                }
                final int end = matchAlias(s, pos, a);
                if (end < 0) {
                    continue;
                }
                if (!target && a.unit == IN && a.text.equals("in")) {
                    // "5 kg in lb": "in" before a unit is the word, not inches
                    final int save = pos;
                    pos = end;
                    skip();
                    final boolean unitNext = pos < s.length() && (baseWord() != 0 || unitAt(true) != null);
                    pos = save;
                    if (unitNext) {
                        continue;
                    }
                }
                pos = end;
                return a.unit != null ? a.unit : moneyUnit(a.money);
            }
            if (o.currencies && o.perUsd != null) {
                // any code the rates know, written in capitals: "100 CHF"
                int end = pos;
                while (end < s.length() && end - pos < 6 && s.charAt(end) >= 'A' && s.charAt(end) <= 'Z') {
                    end++;
                }
                if (end - pos >= 3 && (end >= s.length() || !Character.isLetterOrDigit(s.charAt(end)))) {
                    final String code = s.substring(pos, end);
                    if (o.perUsd.containsKey(code)) {
                        pos = end;
                        return moneyUnit(code);
                    }
                }
                // and the usual ones in any case: "100 usd"
                end = pos;
                while (end < s.length() && end - pos < 5 && Character.isLetter(s.charAt(end))) {
                    end++;
                }
                if (end - pos == 3 && (end >= s.length() || !Character.isLetterOrDigit(s.charAt(end)))) {
                    final String code = s.substring(pos, end).toUpperCase(Locale.ROOT);
                    if (CURRENCY_SYMBOLS.containsKey(code) || COMMON_CODES.contains(code)) {
                        if (o.perUsd.containsKey(code)) {
                            pos = end;
                            return moneyUnit(code);
                        }
                    }
                }
            }
            return null;
        }

        double multiplierAt() {
            skip();
            for (int i = 0, n = MULTIPLIERS.size(); i < n; i++) {
                final Alias a = MULTIPLIERS.get(i);
                final int end = matchAlias(s, pos, a);
                if (end >= 0) {
                    pos = end;
                    return a.multiplier;
                }
            }
            return 0;
        }

        /** A number at pos: 12, 1.5, 1,5, 25 000, 1e6, 0xFF, 0b101, 0o17. NaN when there is none. */
        double number() {
            skip();
            final int start = pos;
            if (pos + 2 < s.length() && s.charAt(pos) == '0') {
                final char p = Character.toLowerCase(s.charAt(pos + 1));
                final int radix = p == 'x' ? 16 : p == 'b' ? 2 : p == 'o' ? 8 : 0;
                if (radix != 0 && Character.digit(s.charAt(pos + 2), radix) >= 0) {
                    int end = pos + 2;
                    while (end < s.length() && Character.digit(s.charAt(end), radix) >= 0) {
                        end++;
                    }
                    if (end < s.length() && Character.isLetterOrDigit(s.charAt(end))) {
                        throw fail();
                    }
                    final String digits = s.substring(pos + 2, end);
                    if (digits.length() > 15) {
                        throw fail();
                    }
                    pos = end;
                    op = true;
                    return Long.parseLong(digits, radix);
                }
            }
            final StringBuilder b = new StringBuilder();
            int run = 0;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) {
                b.append(s.charAt(pos++));
                run++;
            }
            // groups of three after a space: "25 000", "1 955"
            if (run >= 1 && run <= 3) {
                while (pos < s.length() && isSpace(s.charAt(pos))) {
                    final int g = pos + 1;
                    if (g + 3 <= s.length() && Character.isDigit(s.charAt(g)) && Character.isDigit(s.charAt(g + 1)) && Character.isDigit(s.charAt(g + 2))
                        && (g + 3 == s.length() || !Character.isDigit(s.charAt(g + 3)))) {
                        b.append(s, g, g + 3);
                        pos = g + 3;
                    } else {
                        break;
                    }
                }
                // "1,000,000" and "1,000.5" with commas between thousands
                while (pos + 4 <= s.length() && s.charAt(pos) == ',' && threeDigits(pos + 1)
                    && (pos + 4 < s.length() && (s.charAt(pos + 4) == ',' && threeDigits(pos + 5) || s.charAt(pos + 4) == '.') || b.length() > 3 && (pos + 4 == s.length() || !Character.isDigit(s.charAt(pos + 4))))) {
                    b.append(s, pos + 1, pos + 4);
                    pos += 4;
                }
            }
            if (pos < s.length() && (s.charAt(pos) == '.' || s.charAt(pos) == ',') && pos + 1 < s.length() && Character.isDigit(s.charAt(pos + 1))) {
                if (s.charAt(pos) == ',') {
                    comma = true;
                }
                b.append('.');
                pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) {
                    b.append(s.charAt(pos++));
                }
            }
            if (b.length() == 0) {
                pos = start;
                return Double.NaN;
            }
            // 1e6, 2.5e-3
            if (pos + 1 < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                int e = pos + 1;
                if (e < s.length() && (s.charAt(e) == '+' || s.charAt(e) == '-')) {
                    e++;
                }
                if (e < s.length() && Character.isDigit(s.charAt(e))) {
                    int end = e;
                    while (end < s.length() && Character.isDigit(s.charAt(end))) {
                        end++;
                    }
                    if (end >= s.length() || !Character.isLetter(s.charAt(end))) {
                        b.append('e').append(s, pos + 1, end);
                        pos = end;
                        op = true;
                    }
                }
            }
            return Double.parseDouble(b.toString());
        }

        boolean threeDigits(int at) {
            return at + 3 <= s.length() && Character.isDigit(s.charAt(at)) && Character.isDigit(s.charAt(at + 1)) && Character.isDigit(s.charAt(at + 2));
        }

        // ---- grammar
        // expression := term (('+' | '-') term)*
        // term := unary (('*' | '/') unary)*
        // unary := ('-' | '+') unary | power
        // power := postfix ('^' unary)?
        // postfix := primary ('%' | '!')* ('of' unary)?

        Q expression() {
            Q left = term();
            while (true) {
                final boolean plus;
                if (eat('+')) {
                    plus = true;
                } else if (eat('-', '−', '–')) {
                    plus = false;
                } else {
                    return left;
                }
                final Q right = term();
                op = true;
                if (right.percent && right.unit == null) {
                    // "2300 - 15%": the percentage of the number on its left
                    left = new Q(left.v * (plus ? 1 + right.v : 1 - right.v), left.unit);
                    continue;
                }
                left = add(left, right, plus);
            }
        }

        Q add(Q a, Q b, boolean plus) {
            final U unit;
            double bv = b.v;
            if (a.unit == null && b.unit == null) {
                unit = null;
            } else if (a.unit == null || b.unit == null) {
                // "100$ + 20": the bare number is in the other's unit
                unit = a.unit != null ? a.unit : b.unit;
            } else {
                if (a.unit.dim != b.unit.dim) {
                    throw fail();
                }
                unit = a.unit;
                bv = convert(b.v, b.unit, a.unit);
            }
            return new Q(plus ? a.v + bv : a.v - bv, unit);
        }

        double convert(double v, U from, U to) {
            if (from == to) {
                return v;
            }
            if (from.dim == MONEY) {
                final double r = convertMoney(v, from.code, to.code);
                if (Double.isNaN(r)) {
                    throw fail();
                }
                return r;
            }
            return to.fromBase(from.toBase(v));
        }

        Q term() {
            Q left = unary();
            while (true) {
                final boolean multiply;
                if (eat('*', '×', '·', '∙')) {
                    multiply = true;
                } else if (eat('/', '÷')) {
                    multiply = false;
                } else if (timesLetter()) {
                    multiply = true;
                } else {
                    return left;
                }
                final Q right = unary();
                op = true;
                if (multiply) {
                    if (left.unit != null && right.unit != null) {
                        throw fail();
                    }
                    left = new Q(left.v * right.v, left.unit != null ? left.unit : right.unit);
                } else {
                    if (right.v == 0) {
                        throw fail();
                    }
                    if (right.unit != null) {
                        if (left.unit == null || left.unit.dim != right.unit.dim) {
                            throw fail();
                        }
                        // "1 h / 15 min": how many times
                        left = new Q(left.v / convert(right.v, right.unit, left.unit), null);
                    } else {
                        left = new Q(left.v / right.v, left.unit);
                    }
                }
            }
        }

        /** "2x3", "2 x 3": an x between numbers multiplies. */
        boolean timesLetter() {
            skip();
            if (pos + 1 < s.length()) {
                final char c = s.charAt(pos);
                if ((c == 'x' || c == 'X' || c == 'х' || c == 'Х') && !Character.isLetter(s.charAt(pos + 1))) {
                    pos++;
                    return true;
                }
            }
            return false;
        }

        Q unary() {
            if (eat('-', '−', '–')) {
                final Q q = unary();
                q.v = -q.v;
                q.base = 10;
                return q;
            }
            if (eat('+')) {
                return unary();
            }
            return power();
        }

        Q power() {
            final Q base = postfix();
            skip();
            final boolean pow;
            if (s.startsWith("**", pos)) {
                pos += 2;
                pow = true;
            } else {
                pow = eat('^');
            }
            if (pow) {
                final Q exponent = unary();
                if (base.unit != null || exponent.unit != null) {
                    throw fail();
                }
                op = true;
                return new Q(Math.pow(base.v, exponent.v), null);
            }
            return base;
        }

        Q postfix() {
            Q q = primary();
            while (true) {
                if (eat('%')) {
                    op = true;
                    q = new Q(q.v / 100, q.unit);
                    q.percent = true;
                    if (word(OF_WORDS)) {
                        // "12% of 1200"
                        final Q of = unary();
                        q = new Q(q.v * of.v, of.unit);
                    }
                } else if (peek('!') && (pos + 1 >= s.length() || s.charAt(pos + 1) != '=')) {
                    pos++;
                    if (q.unit != null || q.v < 0 || q.v > 170 || q.v != Math.floor(q.v)) {
                        throw fail();
                    }
                    double f = 1;
                    for (int i = 2; i <= (int) q.v; i++) {
                        f *= i;
                    }
                    op = true;
                    q = new Q(f, null);
                } else {
                    return q;
                }
            }
        }

        Q primary() {
            skip();
            if (pos >= s.length()) {
                throw fail();
            }
            final char c = s.charAt(pos);
            if (c == '(') {
                pos++;
                final Q q = expression();
                if (!eat(')')) {
                    throw fail();
                }
                q.percent = false;
                final U unit = q.unit == null ? unitAt(false) : null;
                if (unit != null) {
                    q.unit = unit;
                }
                return q;
            }
            if (c == '√' || c == '∛') {
                pos++;
                final Q arg = postfix();
                if (arg.unit != null || arg.v < 0 && c == '√') {
                    throw fail();
                }
                op = true;
                return new Q(c == '√' ? Math.sqrt(arg.v) : Math.cbrt(arg.v), null);
            }
            if (c == 'π') {
                pos++;
                op = true;
                return new Q(Math.PI, null);
            }
            // "$100", "€5k"
            if (o.currencies) {
                for (int i = 0, n = PREFIX_MONEY.size(); i < n; i++) {
                    final Alias a = PREFIX_MONEY.get(i);
                    if (s.regionMatches(!a.caseSensitive, pos, a.text, 0, a.text.length())) {
                        final int save = pos;
                        pos += a.text.length();
                        skip();
                        final double v = number();
                        if (Double.isNaN(v)) {
                            pos = save;
                            break;
                        }
                        final double m = multiplierAt();
                        if (m != 0) {
                            op = true;
                        }
                        return new Q(m != 0 ? v * m : v, moneyUnit(a.money));
                    }
                }
            }
            if (Character.isDigit(c) || c == '.' && pos + 1 < s.length() && Character.isDigit(s.charAt(pos + 1))) {
                return quantity();
            }
            if (Character.isLetter(c)) {
                return named();
            }
            throw fail();
        }

        /** A number with its shorthand and unit: "300k", "5 kg", "2 h 30 min". */
        Q quantity() {
            double v;
            if (s.charAt(pos) == '.') {
                pos++;
                final int start = pos;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) {
                    pos++;
                }
                v = Double.parseDouble("0." + s.substring(start, pos));
            } else {
                v = number();
            }
            if (Double.isNaN(v)) {
                throw fail();
            }
            final double m = multiplierAt();
            if (m != 0) {
                v *= m;
                op = true;
            }
            final U unit = unitAt(false);
            final Q q = new Q(v, unit);
            if (unit != null && unit.dim != MONEY && unit.dim != TEMPERATURE) {
                // "2 h 30 min", "5 ft 3 in": one quantity
                while (true) {
                    final int save = pos;
                    skip();
                    if (pos >= s.length() || !Character.isDigit(s.charAt(pos))) {
                        pos = save;
                        break;
                    }
                    final double next = number();
                    final U nextUnit = Double.isNaN(next) ? null : unitAt(false);
                    if (nextUnit == null || nextUnit.dim != unit.dim) {
                        pos = save;
                        break;
                    }
                    q.v += convert(next, nextUnit, unit);
                    op = true;
                }
            }
            return q;
        }

        Q named() {
            final int start = pos;
            while (pos < s.length() && Character.isLetter(s.charAt(pos))) {
                pos++;
            }
            final String name = s.substring(start, pos).toLowerCase(Locale.ROOT);
            switch (name) {
                case "pi":
                case "пи":
                    op = true;
                    return new Q(Math.PI, null);
                case "e":
                    return new Q(Math.E, null);
                case "tau":
                    op = true;
                    return new Q(Math.PI * 2, null);
            }
            if (!FUNCTIONS.contains(name)) {
                throw fail();
            }
            op = true;
            if ("dec".equals(name)) {
                return dec();
            }
            final ArrayList<Q> args = new ArrayList<>();
            if (eat('(')) {
                if (!peek(')')) {
                    args.add(expression());
                    while (separator()) {
                        args.add(expression());
                    }
                }
                if (!eat(')')) {
                    throw fail();
                }
            } else {
                // "sin 30", "sqrt 16"
                args.add(power());
            }
            return call(name, args);
        }

        /** Arguments go apart at ";" or at a comma with a space after it, "max(12, 40)"; "1,5" is a number. */
        boolean separator() {
            skip();
            if (pos < s.length() && s.charAt(pos) == ';') {
                pos++;
                return true;
            }
            if (pos < s.length() && s.charAt(pos) == ',' && (pos + 1 >= s.length() || !Character.isDigit(s.charAt(pos + 1)))) {
                pos++;
                return true;
            }
            return false;
        }

        /** dec(ff, 16): ff read in base 16; dec(0xff): a number written in decimal. */
        Q dec() {
            if (!eat('(')) {
                throw fail();
            }
            skip();
            final int start = pos;
            while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '-')) {
                pos++;
            }
            final String raw = s.substring(start, pos).trim();
            skip();
            if (pos < s.length() && (s.charAt(pos) == ';' || s.charAt(pos) == ',')) {
                pos++;
                final Q radix = expression();
                if (!eat(')') || radix.unit != null) {
                    throw fail();
                }
                final int r = (int) radix.v;
                if (r != radix.v || r < 2 || r > 36 || raw.isEmpty() || raw.length() > 15) {
                    throw fail();
                }
                return new Q(Long.parseLong(raw, r), null);
            }
            pos = start;
            final Q q = expression();
            if (!eat(')') || q.unit != null) {
                throw fail();
            }
            q.base = 10;
            return q;
        }

        Q call(String name, ArrayList<Q> args) {
            if (args.isEmpty()) {
                throw fail();
            }
            final Q a = args.get(0);
            switch (name) {
                case "min":
                case "max": {
                    Q best = a;
                    for (int i = 1; i < args.size(); i++) {
                        final Q b = args.get(i);
                        if ((best.unit == null) != (b.unit == null) || best.unit != null && best.unit.dim != b.unit.dim) {
                            throw fail();
                        }
                        final double bv = best.unit != null ? convert(b.v, b.unit, best.unit) : b.v;
                        if ("min".equals(name) ? bv < best.v : bv > best.v) {
                            best = new Q(bv, best.unit);
                        }
                    }
                    return new Q(best.v, best.unit);
                }
                case "abs":
                    one(args);
                    return new Q(Math.abs(a.v), a.unit);
                case "round":
                case "floor":
                case "ceil": {
                    if (args.size() > 2) {
                        throw fail();
                    }
                    final int digits = args.size() == 2 ? (int) plain(args.get(1)) : 0;
                    if (digits < 0 || digits > 10) {
                        throw fail();
                    }
                    final RoundingMode mode = "round".equals(name) ? RoundingMode.HALF_UP : "floor".equals(name) ? RoundingMode.FLOOR : RoundingMode.CEILING;
                    return new Q(new BigDecimal(a.v).setScale(digits, mode).doubleValue(), a.unit);
                }
                case "bin":
                case "oct":
                case "hex": {
                    one(args);
                    final Q q = new Q(plain(a), null);
                    q.base = "bin".equals(name) ? 2 : "oct".equals(name) ? 8 : 16;
                    return q;
                }
            }
            if ("log".equals(name) && args.size() == 2) {
                final double x = plain(a), b = plain(args.get(1));
                if (x <= 0 || b <= 0 || b == 1) {
                    throw fail();
                }
                return new Q(Math.log(x) / Math.log(b), null);
            }
            one(args);
            final double x = plain(a);
            final double angle = o.radians ? x : Math.toRadians(x);
            final double r;
            switch (name) {
                case "sqrt":
                    if (x < 0) {
                        throw fail();
                    }
                    r = Math.sqrt(x);
                    break;
                case "cbrt":
                    r = Math.cbrt(x);
                    break;
                case "log":
                case "lg":
                    if (x <= 0) {
                        throw fail();
                    }
                    r = Math.log10(x);
                    break;
                case "ln":
                    if (x <= 0) {
                        throw fail();
                    }
                    r = Math.log(x);
                    break;
                case "exp":
                    r = Math.exp(x);
                    break;
                case "sin":
                    r = exact(Math.sin(angle));
                    break;
                case "cos":
                    r = exact(Math.cos(angle));
                    break;
                case "tan":
                case "tg": {
                    final double cos = exact(Math.cos(angle));
                    if (cos == 0) {
                        throw fail();
                    }
                    r = exact(Math.sin(angle)) / cos;
                    break;
                }
                case "asin":
                case "acos":
                case "atan": {
                    if (!"atan".equals(name) && (x < -1 || x > 1)) {
                        throw fail();
                    }
                    final double rad = "asin".equals(name) ? Math.asin(x) : "acos".equals(name) ? Math.acos(x) : Math.atan(x);
                    r = o.radians ? rad : Math.toDegrees(rad);
                    break;
                }
                default:
                    throw fail();
            }
            return new Q(r, null);
        }

        /** sin(180°) is 0, not 1.2e-16. */
        double exact(double v) {
            return Math.abs(v) < 1e-12 ? 0 : v;
        }

        void one(ArrayList<Q> args) {
            if (args.size() != 1) {
                throw fail();
            }
        }

        double plain(Q q) {
            if (q.unit != null) {
                throw fail();
            }
            return q.v;
        }
    }

    private static final Set<String> FUNCTIONS = new HashSet<>();
    private static final Set<String> COMMON_CODES = new HashSet<>();

    static {
        Collections.addAll(FUNCTIONS, "round", "floor", "ceil", "min", "max", "abs", "sqrt", "cbrt", "log", "lg", "ln", "exp",
            "sin", "cos", "tan", "tg", "asin", "acos", "atan", "bin", "oct", "hex", "dec");
        Collections.addAll(COMMON_CODES, "USD", "EUR", "RUB", "GBP", "JPY", "CNY", "UAH", "KZT", "BYN", "INR", "KRW", "ILS", "GEL",
            "AZN", "AMD", "UZS", "KGS", "TJS", "MDL", "PLN", "CHF", "CAD", "AUD", "THB", "VND", "NGN", "BRL", "AED", "CZK", "SEK",
            "NOK", "DKK", "HUF", "RON", "BGN", "RSD", "HKD", "SGD", "NZD", "MXN", "ARS", "CLP", "COP", "PEN", "IDR", "MYR", "EGP",
            "SAR", "QAR", "KWD", "ZAR", "TWD", "PKR", "BDT", "LKR", "MNT", "IRR", "TMT", "BTC", "ETH", "XTR", "BNB", "XRP", "TRX", "LTC");
    }

    // ------------------------------------------------------------------------------------------
    // Writing results

    /** Up to 10 decimals, no float noise: 0.1 + 0.2 is 0.3. */
    static String formatPlain(double v, boolean comma, boolean group) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return null;
        }
        final double a = Math.abs(v);
        String s;
        if (a >= 1e15 || a != 0 && a < 1e-10) {
            s = new BigDecimal(v).round(new MathContext(10, RoundingMode.HALF_UP)).stripTrailingZeros().toString()
                .replace("E+", "e").replace("E", "e");
            return comma ? s.replace('.', ',') : s;
        }
        BigDecimal b = new BigDecimal(v).round(new MathContext(12, RoundingMode.HALF_UP));
        b = b.setScale(10, RoundingMode.HALF_UP).stripTrailingZeros();
        s = b.toPlainString();
        return finish(s, comma, group);
    }

    static String formatFixed(double v, int decimals, boolean strip, boolean comma, boolean group) {
        BigDecimal b = new BigDecimal(v).round(new MathContext(15, RoundingMode.HALF_UP)).setScale(decimals, RoundingMode.HALF_UP);
        if (strip) {
            b = b.stripTrailingZeros();
        }
        return finish(b.toPlainString(), comma, group);
    }

    /** "-0" is 0; digits grouped by three with no-break spaces from 1 000; the decimal comma kept. */
    private static String finish(String s, boolean comma, boolean group) {
        boolean negative = s.startsWith("-");
        if (negative) {
            s = s.substring(1);
        }
        final int dot = s.indexOf('.');
        String integer = dot >= 0 ? s.substring(0, dot) : s;
        final String fraction = dot >= 0 ? s.substring(dot + 1) : "";
        if (group && integer.length() >= 4) {
            final StringBuilder b = new StringBuilder();
            final int first = integer.length() % 3;
            for (int i = 0; i < integer.length(); i++) {
                if (i > 0 && (i - first) % 3 == 0) {
                    b.append(NBSP);
                }
                b.append(integer.charAt(i));
            }
            integer = b.toString();
        }
        boolean zero = true;
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c >= '1' && c <= '9') {
                zero = false;
                break;
            }
        }
        if (zero) {
            negative = false;
        }
        final StringBuilder out = new StringBuilder();
        if (negative) {
            out.append('-');
        }
        out.append(integer);
        if (!fraction.isEmpty()) {
            out.append(comma ? ',' : '.').append(fraction);
        }
        return out.toString();
    }

    static String formatBase(double v, int base) {
        if (Double.isNaN(v) || v != Math.floor(v) || Math.abs(v) >= 9.007199254740992E15) {
            return null;
        }
        final long n = (long) v;
        if (base == 10) {
            return Long.toString(n);
        }
        final String prefix = base == 16 ? "0x" : base == 8 ? "0o" : "0b";
        final String digits = Long.toString(Math.abs(n), base).toUpperCase(Locale.ROOT);
        return (n < 0 ? "-" : "") + prefix + digits;
    }

    private static String formatUnit(double v, U unit, boolean ru) {
        return formatFixed(v, 2, true, false, true) + " " + unit.name(ru);
    }

    private static String formatMoney(double v, U unit) {
        final String number;
        final double a = Math.abs(v);
        if ("XTR".equals(unit.code)) {
            number = formatFixed(v, a >= 100 ? 0 : 2, true, false, true);
        } else if (CRYPTO.contains(unit.code)) {
            if (a >= 1 || a == 0) {
                number = formatFixed(v, 4, false, false, true);
            } else {
                // four significant digits, eight decimals at most
                final int decimals = Math.min(8, 3 - (int) Math.floor(Math.log10(a)));
                number = formatFixed(v, Math.max(4, decimals), true, false, true);
            }
        } else {
            number = formatFixed(v, 2, false, false, true);
        }
        return number + " " + unit.en;
    }

    /** 5400 s is "1 h 30 min": days, hours, minutes and seconds. */
    private static String formatDuration(double seconds, boolean ru) {
        final double a = Math.abs(seconds);
        if (a < 1) {
            return formatFixed(seconds * 1000, 2, true, false, true) + " " + MSEC.name(ru);
        }
        long total = Math.round(a);
        final long days = total / 86400;
        total %= 86400;
        final long hours = total / 3600;
        total %= 3600;
        final long minutes = total / 60;
        final long secs = total % 60;
        final StringBuilder b = new StringBuilder();
        if (seconds < 0) {
            b.append('-');
        }
        appendPart(b, days, DAY, ru);
        appendPart(b, hours, HOUR, ru);
        appendPart(b, minutes, MIN, ru);
        appendPart(b, secs, SEC, ru);
        return b.toString();
    }

    private static void appendPart(StringBuilder b, long n, U unit, boolean ru) {
        if (n == 0) {
            return;
        }
        if (b.length() > 0 && b.charAt(b.length() - 1) != '-') {
            b.append(' ');
        }
        b.append(finish(Long.toString(n), false, true)).append(' ').append(unit.name(ru));
    }

    // ------------------------------------------------------------------------------------------
    // Time zones

    private static final class Times {

        private static final Map<String, String> ZONES = new HashMap<>();
        /** Russian names: also matched with their endings, "в Москве", "в Нью-Йорке". */
        private static final Map<String, String> RU_CITIES = new HashMap<>();
        private static Set<String> available;
        private static boolean citiesAdded;

        static {
            zones("Etc/UTC", "utc", "gmt", "z", "zulu", "всемирное");
            zones("America/New_York", "est", "edt", "et", "eastern", "nyc", "ny", "new york", "washington", "boston", "miami", "atlanta", "philadelphia");
            zones("America/Chicago", "cst", "cdt", "ct", "central", "dallas", "houston", "austin");
            zones("America/Denver", "mst", "mdt", "mt", "mountain");
            zones("America/Los_Angeles", "pst", "pdt", "pt", "pacific", "la", "san francisco", "sf", "seattle", "las vegas", "san diego", "silicon valley");
            zones("America/Anchorage", "akst", "akdt", "alaska");
            zones("Pacific/Honolulu", "hst", "hawaii");
            zones("America/Halifax", "ast", "adt", "atlantic");
            zones("America/St_Johns", "nst", "ndt");
            zones("America/Toronto", "montreal", "ottawa");
            zones("America/Sao_Paulo", "brt", "rio", "rio de janeiro", "brasilia");
            zones("America/Argentina/Buenos_Aires", "art", "buenos aires");
            zones("America/Mexico_City", "mexico");
            zones("Europe/London", "bst", "uk", "britain", "england");
            zones("Europe/Lisbon", "wet", "west", "portugal");
            zones("Europe/Paris", "cet", "cest", "met", "europe");
            zones("Europe/Athens", "eet", "eest");
            zones("Europe/Kiev", "kyiv", "kiev", "kharkiv", "odesa", "odessa");
            zones("Europe/Moscow", "msk", "мск", "moscow", "saint petersburg", "st petersburg", "petersburg", "spb", "спб", "kazan", "sochi", "nizhny novgorod", "minsk time");
            zones("Europe/Samara", "samt");
            zones("Asia/Yekaterinburg", "yekt", "ekaterinburg", "yekaterinburg", "chelyabinsk", "perm", "ufa", "tyumen");
            zones("Asia/Omsk", "omst");
            zones("Asia/Novosibirsk", "novt");
            zones("Asia/Krasnoyarsk", "krat");
            zones("Asia/Irkutsk", "irkt");
            zones("Asia/Yakutsk", "yakt");
            zones("Asia/Vladivostok", "vlat", "khabarovsk");
            zones("Asia/Magadan", "magt");
            zones("Asia/Kamchatka", "pett", "kamchatka");
            zones("Asia/Kolkata", "ist", "india", "delhi", "new delhi", "mumbai", "bangalore", "bengaluru", "chennai");
            zones("Asia/Karachi", "pkt");
            zones("Asia/Dubai", "gst", "abu dhabi", "uae");
            zones("Asia/Tehran", "irst");
            zones("Europe/Istanbul", "trt", "ankara", "antalya");
            zones("Asia/Tokyo", "jst", "japan", "osaka");
            zones("Asia/Seoul", "kst", "korea");
            zones("Asia/Shanghai", "china", "beijing", "shenzhen", "guangzhou");
            zones("Asia/Hong_Kong", "hkt");
            zones("Asia/Singapore", "sgt");
            zones("Asia/Bangkok", "ict", "phuket", "pattaya");
            zones("Asia/Jakarta", "wib");
            zones("Asia/Makassar", "bali", "wita");
            zones("Asia/Manila", "pht");
            zones("Asia/Almaty", "almt", "astana");
            zones("Asia/Tashkent", "uzt", "samarkand");
            zones("Asia/Tbilisi", "get");
            zones("Asia/Baku", "azt");
            zones("Asia/Bishkek", "kgt");
            zones("Asia/Yerevan", "yerevan");
            zones("Asia/Jerusalem", "idt", "israel", "tel aviv");
            zones("Australia/Sydney", "aest", "aedt", "canberra");
            zones("Australia/Adelaide", "acst", "acdt");
            zones("Australia/Perth", "awst");
            zones("Pacific/Auckland", "nzst", "nzdt", "new zealand");
            zones("Africa/Nairobi", "eat");
            zones("Africa/Maputo", "cat");
            zones("Africa/Lagos", "wat");
            zones("Africa/Johannesburg", "sast");
            zones("Africa/Cairo", "egypt");

            ru("Europe/Moscow", "москва", "питер", "санкт-петербург", "петербург", "казань", "сочи", "нижний новгород", "краснодар", "ростов", "воронеж", "ярославль", "мурманск", "архангельск", "симферополь", "крым");
            ru("Europe/Kaliningrad", "калининград");
            ru("Europe/Samara", "самара", "ижевск");
            ru("Europe/Volgograd", "волгоград");
            ru("Asia/Yekaterinburg", "екатеринбург", "екб", "челябинск", "пермь", "уфа", "тюмень", "оренбург");
            ru("Asia/Omsk", "омск");
            ru("Asia/Novosibirsk", "новосибирск");
            ru("Asia/Krasnoyarsk", "красноярск");
            ru("Asia/Irkutsk", "иркутск");
            ru("Asia/Yakutsk", "якутск");
            ru("Asia/Vladivostok", "владивосток", "хабаровск");
            ru("Asia/Magadan", "магадан");
            ru("Asia/Kamchatka", "камчатка", "петропавловск-камчатский");
            ru("Europe/London", "лондон", "англия", "великобритания");
            ru("Europe/Paris", "париж", "франция");
            ru("Europe/Berlin", "берлин", "германия", "мюнхен");
            ru("Europe/Rome", "рим", "италия", "милан");
            ru("Europe/Madrid", "мадрид", "испания", "барселона");
            ru("Europe/Amsterdam", "амстердам");
            ru("Europe/Brussels", "брюссель");
            ru("Europe/Zurich", "цюрих", "женева", "швейцария");
            ru("Europe/Vienna", "вена", "австрия");
            ru("Europe/Prague", "прага", "чехия");
            ru("Europe/Warsaw", "варшава", "польша");
            ru("Europe/Stockholm", "стокгольм");
            ru("Europe/Helsinki", "хельсинки", "финляндия");
            ru("Europe/Oslo", "осло");
            ru("Europe/Copenhagen", "копенгаген");
            ru("Europe/Lisbon", "лиссабон", "португалия");
            ru("Europe/Athens", "афины", "греция");
            ru("Europe/Riga", "рига", "латвия");
            ru("Europe/Vilnius", "вильнюс", "литва");
            ru("Europe/Tallinn", "таллин", "таллинн", "эстония");
            ru("Europe/Belgrade", "белград", "сербия");
            ru("Europe/Bucharest", "бухарест");
            ru("Europe/Sofia", "софия", "болгария");
            ru("Europe/Budapest", "будапешт");
            ru("Europe/Chisinau", "кишинев", "кишинёв", "молдова");
            ru("Europe/Kiev", "киев", "киiв", "харьков", "одесса", "украина");
            ru("Europe/Minsk", "минск", "беларусь");
            ru("Europe/Istanbul", "стамбул", "анкара", "анталья", "турция");
            ru("America/New_York", "нью-йорк", "вашингтон", "майами", "бостон");
            ru("America/Chicago", "чикаго", "даллас", "хьюстон");
            ru("America/Denver", "денвер");
            ru("America/Los_Angeles", "лос-анджелес", "сан-франциско", "лас-вегас", "сиэтл");
            ru("America/Toronto", "торонто", "монреаль");
            ru("America/Mexico_City", "мехико");
            ru("America/Sao_Paulo", "сан-паулу", "рио-де-жанейро", "рио");
            ru("America/Argentina/Buenos_Aires", "буэнос-айрес");
            ru("Asia/Tokyo", "токио", "япония", "осака");
            ru("Asia/Shanghai", "пекин", "шанхай", "китай");
            ru("Asia/Hong_Kong", "гонконг");
            ru("Asia/Singapore", "сингапур");
            ru("Asia/Seoul", "сеул", "корея");
            ru("Asia/Dubai", "дубай", "абу-даби", "оаэ");
            ru("Asia/Kolkata", "дели", "нью-дели", "мумбаи", "индия", "гоа");
            ru("Asia/Bangkok", "бангкок", "пхукет", "паттайя", "таиланд", "тайланд");
            ru("Asia/Ho_Chi_Minh", "хошимин", "вьетнам", "нячанг");
            ru("Asia/Jakarta", "джакарта");
            ru("Asia/Makassar", "бали");
            ru("Asia/Almaty", "алматы", "алма-ата", "астана", "казахстан");
            ru("Asia/Tashkent", "ташкент", "самарканд", "узбекистан");
            ru("Asia/Bishkek", "бишкек", "киргизия", "кыргызстан");
            ru("Asia/Dushanbe", "душанбе");
            ru("Asia/Baku", "баку", "азербайджан");
            ru("Asia/Tbilisi", "тбилиси", "батуми", "грузия");
            ru("Asia/Yerevan", "ереван", "армения");
            ru("Asia/Jerusalem", "иерусалим", "тель-авив", "израиль");
            ru("Africa/Cairo", "каир", "египет", "хургада", "шарм-эль-шейх");
            ru("Australia/Sydney", "сидней", "австралия");
            ru("Australia/Melbourne", "мельбурн");
        }

        private static void zones(String id, String... names) {
            for (String n : names) {
                ZONES.put(n, id);
            }
        }

        private static void ru(String id, String... names) {
            for (String n : names) {
                RU_CITIES.put(normalize(n), id);
            }
        }

        private static String normalize(String s) {
            final StringBuilder b = new StringBuilder();
            boolean space = false;
            for (int i = 0; i < s.length(); i++) {
                char c = Character.toLowerCase(s.charAt(i));
                if (c == 'ё') {
                    c = 'е';
                }
                if (isSpace(c) || c == '-' || c == '_') {
                    space = b.length() > 0;
                    continue;
                }
                if (space) {
                    b.append(' ');
                    space = false;
                }
                b.append(c);
            }
            return b.toString();
        }

        private static synchronized TimeZone zone(String name) {
            final String n = normalize(name);
            if (n.isEmpty()) {
                return null;
            }
            // UTC+3, GMT-5, UTC +5:30
            final java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(?:utc|gmt|utс)? ?([+-]) ?(\\d{1,2})(?::?(\\d{2}))?$").matcher(n);
            if (m.matches() && (n.startsWith("utc") || n.startsWith("gmt"))) {
                final int h = Integer.parseInt(m.group(2));
                final int min = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
                if (h > 14 || min >= 60) {
                    return null;
                }
                return TimeZone.getTimeZone(String.format(Locale.US, "GMT%s%02d:%02d", m.group(1), h, min));
            }
            String id = ZONES.get(n);
            if (id == null) {
                id = RU_CITIES.get(n);
            }
            if (id == null) {
                // Russian names with their endings: "в Москве", "в Лондоне"
                for (Map.Entry<String, String> e : RU_CITIES.entrySet()) {
                    final String city = e.getKey();
                    String stem = city;
                    final char last = stem.charAt(stem.length() - 1);
                    if (stem.length() > 3 && (last == 'а' || last == 'я' || last == 'ь' || last == 'й')) {
                        stem = stem.substring(0, stem.length() - 1);
                    }
                    if (n.startsWith(stem) && n.length() - stem.length() <= 2 && n.length() > stem.length() - 1) {
                        boolean letters = true;
                        for (int i = stem.length(); i < n.length(); i++) {
                            if (!Character.isLetter(n.charAt(i))) {
                                letters = false;
                                break;
                            }
                        }
                        if (letters) {
                            id = e.getValue();
                            break;
                        }
                    }
                }
            }
            if (id == null) {
                addCities();
                id = ZONES.get(n);
            }
            if (id == null) {
                return null;
            }
            if (available == null) {
                available = new HashSet<>();
                Collections.addAll(available, TimeZone.getAvailableIDs());
            }
            if (!available.contains(id)) {
                if ("Europe/Kiev".equals(id) && available.contains("Europe/Kyiv")) {
                    id = "Europe/Kyiv";
                } else if ("Etc/UTC".equals(id)) {
                    id = "UTC";
                } else {
                    return null;
                }
            }
            return TimeZone.getTimeZone(id);
        }

        /** Every city the time zone database names: "tokyo", "buenos aires". */
        private static void addCities() {
            if (citiesAdded) {
                return;
            }
            citiesAdded = true;
            for (String id : TimeZone.getAvailableIDs()) {
                final int slash = id.lastIndexOf('/');
                if (slash <= 0 || id.startsWith("Etc/") || id.startsWith("SystemV/") || id.startsWith("US/") || id.startsWith("Canada/")) {
                    continue;
                }
                final String city = normalize(id.substring(slash + 1));
                if (!ZONES.containsKey(city)) {
                    ZONES.put(city, id);
                }
            }
        }

        private static final java.util.regex.Pattern TIME = java.util.regex.Pattern.compile(
            "^(\\d{1,2})(?:[:.](\\d{2}))? ?(a\\.? ?m\\.?|p\\.? ?m\\.?|am|pm)?(?= |$)");

        static String evaluate(String text, Options o) {
            final String s = normalizeKeepDots(text);
            int hour = -1, minute = 0;
            boolean now = false, twelve = false;
            String rest;
            final java.util.regex.Matcher m = TIME.matcher(s);
            if (startsWithWord(s, "now") || startsWithWord(s, "сейчас") || startsWithWord(s, "time") || startsWithWord(s, "время")) {
                now = true;
                rest = s.substring(s.indexOf(' ') < 0 ? s.length() : s.indexOf(' ')).trim();
            } else if (startsWithWord(s, "noon") || startsWithWord(s, "полдень") || startsWithWord(s, "midnight") || startsWithWord(s, "полночь")) {
                hour = s.startsWith("noon") || s.startsWith("полдень") ? 12 : 0;
                rest = s.substring(s.indexOf(' ') < 0 ? s.length() : s.indexOf(' ')).trim();
            } else if (m.find()) {
                hour = Integer.parseInt(m.group(1));
                if (m.group(2) != null) {
                    minute = Integer.parseInt(m.group(2));
                }
                final String ampm = m.group(3);
                if (ampm == null && m.group(2) == null) {
                    return null;
                }
                if (ampm != null) {
                    twelve = true;
                    if (hour < 1 || hour > 12) {
                        return null;
                    }
                    final boolean pm = ampm.charAt(0) == 'p';
                    hour = hour % 12 + (pm ? 12 : 0);
                } else if (hour > 23) {
                    return null;
                }
                if (minute > 59) {
                    return null;
                }
                rest = s.substring(m.end()).trim();
            } else {
                return null;
            }
            String source = rest, destination = "";
            final String[] words = rest.isEmpty() ? new String[0] : rest.split(" ");
            for (int i = 0; i < words.length; i++) {
                final String w = words[i];
                if (w.equals("in") || w.equals("to") || w.equals("в") || w.equals("во") || w.equals("into") || w.equals("по")) {
                    source = join(words, 0, i);
                    destination = join(words, i + 1, words.length);
                    break;
                }
            }
            if (now && destination.isEmpty()) {
                destination = source;
                source = "";
            }
            if (source.isEmpty() && destination.isEmpty()) {
                return null;
            }
            final TimeZone local = o.zone != null ? o.zone : TimeZone.getDefault();
            final TimeZone from = source.isEmpty() ? local : zone(source);
            final TimeZone to = destination.isEmpty() ? local : zone(destination);
            if (from == null || to == null) {
                return null;
            }
            final Calendar start = Calendar.getInstance(from);
            start.setTimeInMillis(o.now);
            if (!now) {
                start.set(Calendar.HOUR_OF_DAY, hour);
                start.set(Calendar.MINUTE, minute);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);
            }
            final Calendar end = Calendar.getInstance(to);
            end.setTimeInMillis(start.getTimeInMillis());
            final Calendar reference;
            if (now) {
                reference = Calendar.getInstance(local);
                reference.setTimeInMillis(o.now);
            } else {
                reference = start;
            }
            final long days = epochDay(end) - epochDay(reference);
            final StringBuilder b = new StringBuilder();
            final int h = end.get(Calendar.HOUR_OF_DAY), min = end.get(Calendar.MINUTE);
            if (twelve || now && !o.use24h) {
                b.append(h % 12 == 0 ? 12 : h % 12).append(':').append(min < 10 ? "0" : "").append(min).append(h < 12 ? " am" : " pm");
            } else {
                b.append(h).append(':').append(min < 10 ? "0" : "").append(min);
            }
            if (days != 0) {
                b.append(" (").append(days > 0 ? "+" : "").append(days).append(')');
            }
            return b.toString();
        }

        private static boolean startsWithWord(String s, String w) {
            return s.startsWith(w) && (s.length() == w.length() || s.charAt(w.length()) == ' ');
        }

        private static String join(String[] words, int from, int to) {
            final StringBuilder b = new StringBuilder();
            for (int i = from; i < to; i++) {
                if (b.length() > 0) {
                    b.append(' ');
                }
                b.append(words[i]);
            }
            return b.toString();
        }

        private static String normalizeKeepDots(String s) {
            final StringBuilder b = new StringBuilder();
            boolean space = false;
            for (int i = 0; i < s.length(); i++) {
                final char c = Character.toLowerCase(s.charAt(i));
                if (isSpace(c)) {
                    space = b.length() > 0;
                    continue;
                }
                if (space) {
                    b.append(' ');
                    space = false;
                }
                b.append(c);
            }
            return b.toString();
        }

        private static long epochDay(Calendar c) {
            final long y = c.get(Calendar.YEAR), m = c.get(Calendar.MONTH) + 1, d = c.get(Calendar.DAY_OF_MONTH);
            // days from civil (Howard Hinnant)
            final long yy = m <= 2 ? y - 1 : y;
            final long era = (yy >= 0 ? yy : yy - 399) / 400;
            final long yoe = yy - era * 400;
            final long doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
            final long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
            return era * 146097 + doe - 719468;
        }
    }
}
