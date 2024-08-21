package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.api.model.ProfileData;
import vc.openapi.vc.handler.SeenApi;
import vc.openapi.vc.model.SeenResponse;
import vc.util.PlayerLookup;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;
import java.util.UUID;

import static discord4j.common.util.TimestampFormat.SHORT_DATE_TIME;
import static java.util.Objects.isNull;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class SeenCommand extends PlayerLookupCommand {
    private static final Logger LOGGER = getLogger(SeenCommand.class);
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
        return resolveData(event, this::resolveSeen);
    }

    private Mono<Message> resolveSeen(final ChatInputInteractionEvent event, final ProfileData identity) {
        UUID uuid = identity.uuid();
        SeenResponse seenResponse = null;
        try {
            seenResponse = seenApi.seen(uuid, null);
        } catch (final Exception e) {
            LOGGER.error("Failed to get seen for player: " + uuid, e);
        }
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
