package vc.util;

import java.time.Instant;

public record QueueETA(double factor, double pow, Instant lastUpdate) {
    public static QueueETA INSTANCE = new QueueETA(0, 0, Instant.EPOCH);

    public long getEtaSeconds(final int queuePos) {
        return (long) (factor * (Math.pow(queuePos, pow)));
    }

    public String getEtaStringFromSeconds(final double totalSeconds) {
        final int hour = (int) (totalSeconds / 3600);
        final int minutes = (int) ((totalSeconds / 60) % 60);
        final int seconds = (int) (totalSeconds % 60);
        final String hourStr = hour >= 10 ? "" + hour : "0" + hour;
        final String minutesStr = minutes >= 10 ? "" + minutes : "0" + minutes;
        final String secondsStr = seconds >= 10 ? "" + seconds : "0" + seconds;
        return hourStr + ":" + minutesStr + ":" + secondsStr;
    }

    public String getEtaString(final int queuePos) {
        long totalSeconds = getEtaSeconds(queuePos);
        return getEtaStringFromSeconds(totalSeconds);
    }
}
