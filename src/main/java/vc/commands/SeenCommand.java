package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.springframework.stereotype.Component;
import org.threeten.bp.OffsetDateTime;
import reactor.core.publisher.Mono;
import vc.swagger.vc.handler.SeenApi;
import vc.swagger.vc.model.SeenResponse;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.isNull;

@Component
public class SeenCommand implements SlashCommand {

    private final SeenApi seenApi;
    private final PlayerLookup playerLookup;

    public SeenCommand(final SeenApi seenApi, final PlayerLookup playerLookup) {
        this.seenApi = seenApi;
        this.playerLookup = playerLookup;
    }

    @Override
    public String getName() {
        return "seen";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        Optional<String> playerNameOptional = event.getOption("playername")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString);
        return playerNameOptional
                .filter(Validator::isValidPlayerName)
                .flatMap(playerLookup::getPlayerIdentity)
                .map(identity -> resolveSeen(event, identity))
                .orElse(error(event, "Unable to find player"));
    }

    private Mono<Message> resolveSeen(final ChatInputInteractionEvent event, final PlayerLookup.PlayerIdentity identity) {
        UUID uuid = identity.uuid();
        SeenResponse seenResponse = seenApi.seen(uuid, null);
        if (isNull(seenResponse)) return error(event, "Player has not been seen");
        SeenResponse firstSeenResponse = seenApi.firstSeen(uuid, null);
        if (isNull(firstSeenResponse)) return error(event, "Player has not been seen");
        OffsetDateTime lastSeen = seenResponse.getTime();
        OffsetDateTime firstSeen = firstSeenResponse.getTime();
        return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                        .title("Seen: " + escape(identity.playerName()))
                        .color(Color.CYAN)
                        .addField("First seen", "<t:" + firstSeen.toEpochSecond() + ":f>", false)
                        .addField("Last seen", "<t:" + lastSeen.toEpochSecond() + ":f>", false)
                        .thumbnail(playerLookup.getAvatarURL(uuid).toString())
                        .build());
    }
}
