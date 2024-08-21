package vc.commands;

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
import vc.util.PlayerLookup;

import java.io.ByteArrayInputStream;

import static org.slf4j.LoggerFactory.getLogger;

@Component
public class DataCommand extends PlayerLookupCommand {
    private static final Logger LOGGER = getLogger(DataCommand.class);
    private final VcDataDumpApi vcDataDumpApi;

    public DataCommand(final PlayerLookup playerLookup, final VcDataDumpApi vcDataDumpApi) {
        super(playerLookup);
        this.vcDataDumpApi = vcDataDumpApi;
    }

    @Override
    public String getName() {
        return "data";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        return resolveData(event, this::resolvePlayerDataDump);
    }

    public Mono<Message> resolvePlayerDataDump(ChatInputInteractionEvent event, ProfileData playerIdentity) {
        String playerDataDump = null;
        try {
            playerDataDump = vcDataDumpApi.getPlayerDataDump(playerIdentity.uuid(), null);
        } catch (final Exception e){
            LOGGER.error("Failed to get player data dump", e);
        }
        if (playerDataDump == null)
            return error(event, "Unable to find player");
        return event.createFollowup()
            .withFiles(MessageCreateFields.File.of(playerIdentity.name() + ".csv", new ByteArrayInputStream(playerDataDump.getBytes())))
            .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), playerIdentity)
                            .title("Data Dump")
                            .addField("Data Count", ""+playerDataDump.lines().count(), true)
                            .description("CSV Generated!")
                            .color(Color.CYAN)
                            .thumbnail(playerLookup.getAvatarURL(playerIdentity.uuid()).toString())
                            .build());
    }
}
