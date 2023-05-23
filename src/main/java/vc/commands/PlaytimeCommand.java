package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.swagger.vc.handler.PlaytimeApi;
import vc.swagger.vc.model.PlaytimeResponse;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.isNull;

@Component
public class PlaytimeCommand implements SlashCommand {

    private final PlaytimeApi playtimeApi = new PlaytimeApi();
    private final PlayerLookup playerLookup;

    public PlaytimeCommand(final PlayerLookup playerLookup) {
        this.playerLookup = playerLookup;
    }

    @Override
    public String getName() {
        return "playtime";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        Optional<String> playerNameOptional = event.getOption("playername")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString);
        return playerNameOptional
                .filter(Validator::isValidPlayerName)
                .flatMap(playerLookup::getPlayerIdentity)
                .map(identity -> resolvePlaytime(event, identity))
                .orElse(error(event, "Unable to find player"));
    }

    private Mono<Message> resolvePlaytime(ChatInputInteractionEvent event, final PlayerLookup.PlayerIdentity identity) {
        UUID profileUUID = identity.uuid();
        PlaytimeResponse playtime = playtimeApi.playtime(profileUUID, null);
        if (isNull(playtime)) return error(event, "No playtime found");
        Integer playtimeSeconds = playtime.getPlaytimeSeconds();
        String durationStr = formatDuration(playtimeSeconds);
        return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                        .title("Playtime: " + escape(identity.playerName()))
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
