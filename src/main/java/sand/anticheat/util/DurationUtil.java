package sand.anticheat.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationUtil {

    private static final Pattern TOKEN = Pattern.compile("(\\d+)([a-zA-Zа-яА-Я]+)");

    private DurationUtil() {
    }

    public static boolean isPermanent(String input) {
        String normalized = normalize(input);
        return normalized.equals("навсегда")
                || normalized.equals("perm")
                || normalized.equals("permanent")
                || normalized.equals("forever");
    }

    public static long parseDurationMillis(String input) {
        String normalized = normalize(input);
        if (normalized.isEmpty()) {
            return -1L;
        }

        Matcher matcher = TOKEN.matcher(normalized);
        long total = 0L;
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() != lastEnd) {
                return -1L;
            }

            long amount = Long.parseLong(matcher.group(1));
            long unit = resolveUnitMillis(matcher.group(2));
            if (unit <= 0L) {
                return -1L;
            }

            total += amount * unit;
            lastEnd = matcher.end();
        }

        if (lastEnd != normalized.length() || total <= 0L) {
            return -1L;
        }

        return total;
    }

    public static String formatDuration(long millis) {
        if (millis <= 0L) {
            return "0 сек.";
        }

        long seconds = millis / 1000L;
        long years = seconds / (365L * 24L * 60L * 60L);
        seconds %= 365L * 24L * 60L * 60L;
        long months = seconds / (30L * 24L * 60L * 60L);
        seconds %= 30L * 24L * 60L * 60L;
        long weeks = seconds / (7L * 24L * 60L * 60L);
        seconds %= 7L * 24L * 60L * 60L;
        long days = seconds / (24L * 60L * 60L);
        seconds %= 24L * 60L * 60L;
        long hours = seconds / (60L * 60L);
        seconds %= 60L * 60L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        StringBuilder builder = new StringBuilder();
        append(builder, years, "г.");
        append(builder, months, "мес.");
        append(builder, weeks, "н.");
        append(builder, days, "д.");
        append(builder, hours, "ч.");
        append(builder, minutes, "м.");
        append(builder, seconds, "сек.");

        String value = builder.toString().trim();
        return value.isEmpty() ? "0 сек." : value;
    }

    private static void append(StringBuilder builder, long amount, String suffix) {
        if (amount <= 0L || builder.length() > 0 && builder.toString().split(" ").length >= 3) {
            return;
        }

        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(amount).append(' ').append(suffix);
    }

    private static long resolveUnitMillis(String unit) {
        String normalized = normalize(unit);
        if (normalized.equals("сек") || normalized.equals("sec") || normalized.equals("secs") || normalized.equals("second") || normalized.equals("seconds") || normalized.equals("s")) {
            return 1000L;
        }
        if (normalized.equals("м") || normalized.equals("мин") || normalized.equals("min") || normalized.equals("mins") || normalized.equals("minute") || normalized.equals("minutes") || normalized.equals("m")) {
            return 60L * 1000L;
        }
        if (normalized.equals("ч") || normalized.equals("час") || normalized.equals("h") || normalized.equals("hr") || normalized.equals("hour") || normalized.equals("hours")) {
            return 60L * 60L * 1000L;
        }
        if (normalized.equals("д") || normalized.equals("day") || normalized.equals("days") || normalized.equals("d")) {
            return 24L * 60L * 60L * 1000L;
        }
        if (normalized.equals("н") || normalized.equals("нед") || normalized.equals("week") || normalized.equals("weeks") || normalized.equals("w")) {
            return 7L * 24L * 60L * 60L * 1000L;
        }
        if (normalized.equals("мес") || normalized.equals("месяц") || normalized.equals("month") || normalized.equals("months") || normalized.equals("mo")) {
            return 30L * 24L * 60L * 60L * 1000L;
        }
        if (normalized.equals("г") || normalized.equals("год") || normalized.equals("years") || normalized.equals("year") || normalized.equals("y") || normalized.equals("yr") || normalized.equals("с")) {
            return 365L * 24L * 60L * 60L * 1000L;
        }
        return -1L;
    }

    private static String normalize(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }
}
