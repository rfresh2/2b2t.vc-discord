package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import org.springframework.stereotype.Component;
import org.threeten.bp.OffsetDateTime;
import reactor.core.publisher.Mono;
import vc.swagger.vc.handler.SeenApi;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.util.Optional;
import java.util.UUID;

@Component
public class SeenCommand implements SlashCommand {

    private final SeenApi seenApi = new SeenApi();
    private final PlayerLookup playerLookup;

    public SeenCommand(final PlayerLookup playerLookup) {
        this.playerLookup = playerLookup;
    }

    @Override
    public String getName() {
        return "seen";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        Optional<String> playerNameOptional = event.getOption("username")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString);
        return playerNameOptional
                .filter(Validator::isValidUsername)
                .flatMap(playerLookup::getPlayerUUID)
                .map(uuid -> resolveSeen(event, uuid))
                .orElse(error(event, "Unable to find player"));
    }

    private Mono<Message> resolveSeen(final ChatInputInteractionEvent event, final UUID uuid) {
        OffsetDateTime lastSeen = seenApi.seen(uuid).getTime();
        OffsetDateTime firstSeen = seenApi.firstSeen(uuid).getTime();
        return event.createFollowup()
                .withContent("First seen: <t:" + firstSeen.toEpochSecond() + ":f>" + "\nLast seen: <t:" + lastSeen.toEpochSecond() + ":f>");
    }
}
