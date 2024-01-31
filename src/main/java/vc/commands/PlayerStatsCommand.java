package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.openapi.vc.handler.StatsApi;
import vc.openapi.vc.model.PlayerStats;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.util.Optional;

import static discord4j.common.util.TimestampFormat.SHORT_DATE_TIME;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class PlayerStatsCommand extends PlayerLookupCommand {
    private static final Logger LOGGER = getLogger(PlayerStatsCommand.class);
    private final StatsApi statsApi;

    public PlayerStatsCommand(final PlayerLookup playerLookup, final StatsApi statsApi) {
        super(playerLookup);
        this.statsApi = statsApi;
    }

    @Override
    public String getName() {
        return "stats";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        Optional<String> playerNameOptional = event.getOption("player")
            .flatMap(ApplicationCommandInteractionOption::getValue)
            .map(ApplicationCommandInteractionOptionValue::asString);
        if (playerNameOptional.isEmpty())
            return error(event, "No player name provided");
        if (!Validator.isValidPlayerName(playerNameOptional.get()))
            return error(event, "Invalid player name");
        var playerIdentityOptional = playerLookup.getPlayerIdentity(playerNameOptional.get());
        if (playerIdentityOptional.isEmpty())
            return error(event, "Unable to find player");
        PlayerStats playerStats = null;
        try {
            playerStats = statsApi.playerStats(playerIdentityOptional.get().uuid(), null);
        } catch (final Exception e) {
            LOGGER.error("Failed to get stats for player: " + playerIdentityOptional.get().uuid(), e);
        }
        if (playerStats == null)
            return error(event, "Unable to find player");
        return event.createFollowup()
            .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), playerIdentityOptional.get())
                            .title("Player Stats")
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
                            .addField("\u200B", "\u200B", true)
                            .addField("\u200B", "\u200B", true)
                            .thumbnail(playerLookup.getAvatarURL(playerIdentityOptional.get().uuid()).toString())
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
