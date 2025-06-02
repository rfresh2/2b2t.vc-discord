package vc.commands;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.api.model.ProfileData;
import vc.commands.options.ChatInteractionOptionResolver;
import vc.commands.options.PlayerLookupOption;
import vc.openapi.handler.ApiException;
import vc.openapi.handler.SeenApi;
import vc.openapi.model.SeenResponse;
import vc.util.PlayerLookup;

import javax.annotation.Nullable;
import java.net.http.HttpTimeoutException;
import java.time.OffsetDateTime;
import java.util.UUID;

import static discord4j.common.util.TimestampFormat.SHORT_DATE_TIME;
import static java.util.Objects.isNull;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class SeenCommand implements SlashCommand {
    private static final Logger LOGGER = getLogger(SeenCommand.class);
    private final SeenApi seenApi;
    private final ChatInteractionOptionResolver resolver;

    public SeenCommand(final SeenApi seenApi, final PlayerLookup playerLookup) {
        this.seenApi = seenApi;
        this.resolver = new ChatInteractionOptionResolver()
            .registerOption(new PlayerLookupOption(playerLookup));
    }

    @Override
    public String getName() {
        return "seen";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        var ctx = resolver.resolveOptions(event);
        if (ctx.isErrorSet()) return error(event, ctx.getErrorMessage());
        return resolveSeen(event, ctx.profileData);
    }

    private Mono<Message> resolveSeen(final ChatInputInteractionEvent event, final ProfileData identity) {
        UUID uuid = identity.uuid();
        SeenResponse seenResponse = null;
        try {
            seenResponse = seenApi.seen(uuid, null);
        } catch (final Exception e) {
            if (e instanceof ApiException apiException) {
                if (apiException.getCause() instanceof MismatchedInputException || apiException.getCode() == 204) {
                    return event.createFollowup()
                        .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                            .color(Color.RUBY)
                            .description("Never Seen")
                            .thumbnail(identity.getAvatarURL())
                            .build());
                } else if (apiException.getCause() instanceof HttpTimeoutException httpTimeoutException) {
                    LOGGER.error("Timeout getting seen for: {}", identity.uuid(), httpTimeoutException);
                    return error(event, "Timeout getting seen data. Try again in a minute");
                }
            } else {
                LOGGER.error("Failed to get seen for player: {}", uuid, e);
                throw new RuntimeException(e);
            }
        }
        if (isNull(seenResponse))
            return event.createFollowup()
                .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                    .color(Color.RUBY)
                    .description("Never Seen")
                    .thumbnail(identity.getAvatarURL())
                    .build());
        return event.createFollowup()
            .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                .addField("First seen", getSeenString(seenResponse.getFirstSeen()), false)
                .addField("Last seen", getSeenString(seenResponse.getLastSeen()), false)
                .color(Color.CYAN)
                .thumbnail(identity.getAvatarURL())
                .build());
    }

    private String getSeenString(@Nullable final OffsetDateTime seen) {
        return seen != null ? SHORT_DATE_TIME.format(seen.toInstant()) : "Never";
    }
}
