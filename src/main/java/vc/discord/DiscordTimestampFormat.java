package vc.discord;

import java.time.Instant;

public final class DiscordTimestampFormat {
    public static final TimestampFormatter SHORT_DATE_TIME = instant -> "<t:" + instant.getEpochSecond() + ":f>";

    private DiscordTimestampFormat() {
    }

    @FunctionalInterface
    public interface TimestampFormatter {
        String format(Instant instant);
    }
}
