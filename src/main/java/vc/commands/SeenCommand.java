package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.api.model.ProfileData;
import vc.openapi.vc.handler.SeenApi;
import vc.openapi.vc.model.SeenResponse;
import vc.util.PlayerLookup;
import vc.util.Validator;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static discord4j.common.util.TimestampFormat.SHORT_DATE_TIME;
import static java.util.Objects.isNull;

@Component
public class SeenCommand extends PlayerLookupCommand {

    private final SeenApi seenApi;

    public SeenCommand(final SeenApi seenApi, final PlayerLookup playerLookup) {
        super(playerLookup);
        this.seenApi = seenApi;
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

    private Mono<Message> resolveSeen(final ChatInputInteractionEvent event, final ProfileData identity) {
        UUID uuid = identity.uuid();
        SeenResponse seenResponse = seenApi.seen(uuid, null);
        if (isNull(seenResponse)) return error(event, "Player has not been seen");
        return event.createFollowup()
                .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                        .title("Seen")
                        .color(Color.CYAN)
                        .addField("First seen", getSeenString(seenResponse.getFirstSeen()), false)
                        .addField("Last seen", getSeenString(seenResponse.getLastSeen()), false)
                        .thumbnail(playerLookup.getAvatarURL(uuid).toString())
                        .build());
    }

    private String getSeenString(@Nullable final OffsetDateTime seen) {
        return seen != null ? SHORT_DATE_TIME.format(seen.toInstant()) : "Never";
    }
}
