package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.api.model.ProfileData;
import vc.openapi.handler.PlaytimeApi;
import vc.openapi.model.PlaytimeResponse;
import vc.util.PlayerLookup;

import java.util.UUID;

import static java.util.Objects.isNull;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class PlaytimeCommand extends PlayerLookupCommand {
    private static final Logger LOGGER = getLogger(PlaytimeCommand.class);
    private final PlaytimeApi playtimeApi;

    public PlaytimeCommand(final PlaytimeApi playtimeApi, final PlayerLookup playerLookup) {
        super(playerLookup);
        this.playtimeApi = playtimeApi;
    }

    @Override
    public String getName() {
        return "playtime";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        return resolveData(event, this::resolvePlaytime);
    }

    private Mono<Message> resolvePlaytime(ChatInputInteractionEvent event, final ProfileData identity) {
        UUID profileUUID = identity.uuid();
        PlaytimeResponse playtime = null;
        try {
            playtime = playtimeApi.playtime(profileUUID, null);
        } catch (final Exception e) {
            LOGGER.error("Failed to get playtime for player: {}", profileUUID, e);
        }
        if (isNull(playtime)) return error(event, "No playtime found");
        Integer playtimeSeconds = playtime.getPlaytimeSeconds();
        String durationStr = formatDuration(playtimeSeconds);
        return event.createFollowup()
                .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                        .title("Playtime")
                        .color(Color.CYAN)
                        .description(durationStr)
                        .thumbnail(playerLookup.getAvatarURL(profileUUID).toString())
                        .build());
    }

    private String formatDuration(long durationInSeconds) {
        var secondsInMinute = 60L;
        var secondsInHour = secondsInMinute * 60L;
        var secondsInDay = secondsInHour * 24L;
        var secondsInMonth = secondsInDay * 30L; // assuming 30 days per month
        var secondsInYear = secondsInMonth * 12L;

        var years = durationInSeconds / secondsInYear;
        var months = (durationInSeconds % secondsInYear) / secondsInMonth;
        var days = (durationInSeconds % secondsInMonth) / secondsInDay;
        var hours = (durationInSeconds % secondsInDay) / secondsInHour;
        final StringBuilder sb = new StringBuilder();
        sb.append((years > 0) ? years + " year" + (years != 1 ? "s" : "") + ", " : "");
        sb.append((months > 0) ? months + " month" + (months != 1 ? "s" : "") + ", " : "");
        sb.append((days > 0) ? days + " day" + (days != 1 ? "s" : "") + ", " : "");
        sb.append(hours + " hour" + (hours != 1 ? "s" : ""));
        return sb.toString();
    }
}
