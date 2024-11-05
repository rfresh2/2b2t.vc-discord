package vc.commands;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.commands.options.ChatInteractionOptionResolver;
import vc.commands.options.PlayerLookupOption;
import vc.openapi.handler.ApiException;
import vc.openapi.handler.StatsApi;
import vc.openapi.model.PlayerStats;
import vc.util.PlayerLookup;

import java.net.http.HttpTimeoutException;

import static discord4j.common.util.TimestampFormat.SHORT_DATE_TIME;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class PlayerStatsCommand implements SlashCommand {
    private static final Logger LOGGER = getLogger(PlayerStatsCommand.class);
    private final StatsApi statsApi;
    private final ChatInteractionOptionResolver resolver;

    public PlayerStatsCommand(final PlayerLookup playerLookup, final StatsApi statsApi) {
        this.statsApi = statsApi;
        this.resolver = new ChatInteractionOptionResolver()
            .registerOption(new PlayerLookupOption(playerLookup));
    }

    @Override
    public String getName() {
        return "stats";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        var ctx = resolver.resolveOptions(event);
        if (ctx.isErrorSet()) return error(event, ctx.getErrorMessage());
        var identity = ctx.profileData;
        PlayerStats playerStats = null;
        try {
            playerStats = statsApi.playerStats(identity.uuid(), null);
        } catch (final Exception e) {
            if (e instanceof ApiException apiException) {
                if (apiException.getCause() instanceof MismatchedInputException || apiException.getCode() == 204) {
                    return event.createFollowup()
                        .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                                        .color(Color.RUBY)
                                        .description("No Data")
                                        .thumbnail(identity.getAvatarURL())
                                        .build());
                } else if (apiException.getCause() instanceof HttpTimeoutException httpTimeoutException) {
                    LOGGER.error("Timeout getting stats for: {}", identity.uuid(), httpTimeoutException);
                    return error(event, "Timeout getting stats. Try again in a minute");
                }
            } else {
                LOGGER.error("Failed to get stats for player: {}", identity.uuid(), e);
                throw new RuntimeException(e);
            }
        }
        if (playerStats == null)
            return event.createFollowup()
                .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                                .color(Color.RUBY)
                                .description("No Data")
                                .thumbnail(identity.getAvatarURL())
                                .build());
        return event.createFollowup()
            .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                            .color(Color.CYAN)
                            .addField("Joins", ""+playerStats.getJoinCount(), true)
                            .addField("Leaves", ""+playerStats.getLeaveCount(), true)
                            .addField("\u200B", "\u200B", true)
                            .addField("First Seen", playerStats.getFirstSeen() != null
                                          ? SHORT_DATE_TIME.format(playerStats.getFirstSeen().toInstant())
                                          : "Never",
                                      true)
                            .addField("Last Seen", playerStats.getLastSeen() != null
                                          ? SHORT_DATE_TIME.format(playerStats.getLastSeen().toInstant())
                                          : "Never",
                                      true)
                            .addField("\u200B", "\u200B", true)
                            .addField("Playtime", formatDuration(playerStats.getPlaytimeSeconds()), true)
                            .addField("Playtime (Last 30 Days)", formatDuration(playerStats.getPlaytimeSecondsMonth()), true)
                            .addField("\u200B", "\u200B", true)
                            .addField("Deaths", ""+playerStats.getDeathCount(), true)
                            .addField("Kills", ""+playerStats.getKillCount(), true)
                            .addField("\u200B", "\u200B", true)
                            .addField("Chats", ""+playerStats.getChatsCount(), true)
                            .addField("Priority Queue", Boolean.TRUE.equals(playerStats.getPrio()) ? "Yes" : "No", true)
                            .addField("\u200B", "\u200B", true)
                            .thumbnail(identity.getAvatarURL())
                            .build());
    }

    private String formatDuration(long durationInSeconds) {
        var secondsInMinute = 60L;
        var secondsInHour = secondsInMinute * 60L;
        var secondsInDay = secondsInHour * 24L;
        var secondsInMonth = secondsInDay * 30L; // assuming 30 days per month

        var months = durationInSeconds / secondsInMonth;
        var days = (durationInSeconds % secondsInMonth) / secondsInDay;
        var hours = (durationInSeconds % secondsInDay) / secondsInHour;
        final StringBuilder sb = new StringBuilder();
        sb.append((months > 0) ? months + " month" + (months != 1 ? "s" : "") + ", " : "");
        sb.append((days > 0) ? days + " day" + (days != 1 ? "s" : "") + ", " : "");
        sb.append(hours + " hour" + (hours != 1 ? "s" : ""));
        return sb.toString();
    }
}
