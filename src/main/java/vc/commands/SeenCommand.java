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
import vc.swagger.mojang_api.model.ProfileLookup;
import vc.swagger.vc.handler.SeenApi;
import vc.swagger.vc.model.SeenResponse;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.isNull;

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
                .flatMap(playerLookup::getPlayerProfile)
                .map(profile -> resolveSeen(event, profile))
                .orElse(error(event, "Unable to find player"));
    }

    private Mono<Message> resolveSeen(final ChatInputInteractionEvent event, final ProfileLookup profile) {
        UUID uuid = playerLookup.getProfileUUID(profile);
        SeenResponse seenResponse = seenApi.seen(uuid);
        if (isNull(seenResponse)) return error(event, "Player has not been seen");
        SeenResponse firstSeenResponse = seenApi.firstSeen(uuid);
        if (isNull(firstSeenResponse)) return error(event, "Player has not been seen");
        OffsetDateTime lastSeen = seenResponse.getTime();
        OffsetDateTime firstSeen = firstSeenResponse.getTime();
        return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                        .title("Seen: " + escape(profile.getName()))
                        .color(Color.CYAN)
                        .addField("First seen", "<t:" + firstSeen.toEpochSecond() + ":f>", false)
                        .addField("Last seen", "<t:" + lastSeen.toEpochSecond() + ":f>", false)
                        .thumbnail(playerLookup.getAvatarURL(uuid).toString())
                        .build());
    }
}
