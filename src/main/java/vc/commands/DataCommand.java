package vc.commands;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateFields;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.api.VcDataDumpApi;
import vc.api.model.ProfileData;
import vc.commands.options.ChatInteractionOptionResolver;
import vc.commands.options.PlayerLookupOption;
import vc.openapi.handler.ApiException;
import vc.util.PlayerLookup;

import java.io.ByteArrayInputStream;
import java.net.http.HttpTimeoutException;

import static org.slf4j.LoggerFactory.getLogger;

@Component
public class DataCommand implements SlashCommand {
    private static final Logger LOGGER = getLogger(DataCommand.class);
    private final VcDataDumpApi vcDataDumpApi;
    private final ChatInteractionOptionResolver resolver;

    public DataCommand(final PlayerLookup playerLookup, final VcDataDumpApi vcDataDumpApi) {
        this.vcDataDumpApi = vcDataDumpApi;
        this.resolver = new ChatInteractionOptionResolver()
            .registerOption(new PlayerLookupOption(playerLookup));
    }

    @Override
    public String getName() {
        return "data";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        var ctx = this.resolver.resolveOptions(event);
        return resolvePlayerDataDump(event, ctx.profileData);
    }

    public Mono<Message> resolvePlayerDataDump(ChatInputInteractionEvent event, ProfileData identity) {
        String playerDataDump = null;
        try {
            playerDataDump = vcDataDumpApi.getPlayerDataDump(identity.uuid(), null);
        } catch (final Exception e) {
            if (e instanceof ApiException apiException) {
                if (apiException.getCause() instanceof MismatchedInputException || apiException.getCode() == 204) {
                    return event.createFollowup()
                        .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                                        .color(Color.RUBY)
                                        .description("No Data")
                                        .thumbnail(identity.getAvatarURL())
                                        .build());
                } else if (apiException.getCause() instanceof HttpTimeoutException httpTimeoutException) {
                    LOGGER.error("Timeout searching for data dump: {}", identity.uuid(), httpTimeoutException);
                    return error(event, "Timeout searching for data. Try again in a minute");
                }
            } else {
                LOGGER.error("Failed to get player data dump", e);
                throw new RuntimeException(e);
            }
        }
        if (playerDataDump == null)
            return event.createFollowup()
                .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                                .color(Color.RUBY)
                                .description("No Data")
                                .thumbnail(identity.getAvatarURL())
                                .build());
        return event.createFollowup()
            .withFiles(MessageCreateFields.File.of(identity.name() + ".csv", new ByteArrayInputStream(playerDataDump.getBytes())))
            .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                            .addField("Data Count", ""+playerDataDump.lines().count(), true)
                            .description("CSV Generated!")
                            .color(Color.CYAN)
                            .thumbnail(identity.getAvatarURL())
                            .build());
    }
}
