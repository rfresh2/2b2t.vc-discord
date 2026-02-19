package vc.commands;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
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

import static java.util.Objects.isNull;
import static org.slf4j.LoggerFactory.getLogger;
import static vc.discord.DiscordTimestampFormat.SHORT_DATE_TIME;

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
    public WebhookMessageCreateAction<Message> handle(final SlashCommandInteractionEvent event) {
        var ctx = resolver.resolveOptions(event);
        if (ctx.isErrorSet()) return error(event, ctx.getErrorMessage());
        return resolveSeen(event, ctx.profileData);
    }

    private WebhookMessageCreateAction<Message> resolveSeen(final SlashCommandInteractionEvent event, final ProfileData identity) {
        UUID uuid = identity.uuid();
        SeenResponse seenResponse = null;
        try {
            seenResponse = seenApi.seen(uuid, null);
        } catch (final Exception e) {
            if (e instanceof ApiException apiException) {
                if (apiException.getCause() instanceof MismatchedInputException || apiException.getCode() == 204) {
                    return event.getHook().sendMessageEmbeds(populateIdentity(embed(event), identity)
                        .setColor(Color.RUBY)
                        .setDescription("Never Seen")
                        .setThumbnail(identity.getAvatarURL())
                        .build());
                } else if (apiException.getCause() instanceof HttpTimeoutException httpTimeoutException) {
                    LOGGER.error("Timeout getting seen for: {}", identity.uuid(), httpTimeoutException);
                    return error(event, "Timeout getting seen data. Try again in a minute");
                }
            }
            LOGGER.error("Failed to get seen for player: {}", uuid, e);
            throw new RuntimeException(e);
        }
        if (isNull(seenResponse)) {
            return event.getHook().sendMessageEmbeds(populateIdentity(embed(event), identity)
                .setColor(Color.RUBY)
                .setDescription("Never Seen")
                .setThumbnail(identity.getAvatarURL())
                .build());
        }
        return event.getHook().sendMessageEmbeds(populateIdentity(embed(event), identity)
            .addField("First seen", getSeenString(seenResponse.getFirstSeen()), false)
            .addField("Last seen", getSeenString(seenResponse.getLastSeen()), false)
            .setColor(Color.CYAN)
            .setThumbnail(identity.getAvatarURL())
            .build());
    }

    private String getSeenString(@Nullable final OffsetDateTime seen) {
        return seen != null ? SHORT_DATE_TIME.format(seen.toInstant()) : "Never";
    }
}
