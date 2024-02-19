package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateFields;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.api.VcDataDumpApi;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.io.ByteArrayInputStream;
import java.util.Optional;

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
        Optional<String> playerNameOptional = event.getOption("player")
            .flatMap(ApplicationCommandInteractionOption::getValue)
            .map(ApplicationCommandInteractionOptionValue::asString);
        if (playerNameOptional.isEmpty())
            return error(event, "No player name provided");
        if (!Validator.isValidPlayerName(playerNameOptional.get()))
            return error(event, "Invalid player name");
        var playerIdentityOptional = playerLookup.getPlayerIdentity(playerNameOptional.get());
        if (playerIdentityOptional.isEmpty())
            return error(event, "Unable to find player");
        String playerDataDump = null;
        try {
            playerDataDump = vcDataDumpApi.getPlayerDataDump(playerIdentityOptional.get().uuid(), null);
        } catch (final Exception e){
            LOGGER.error("Failed to get player data dump", e);
        }
        if (playerDataDump == null)
            return error(event, "Unable to find player");
        return event.createFollowup()
            .withFiles(MessageCreateFields.File.of(playerIdentityOptional.get().name() + ".csv", new ByteArrayInputStream(playerDataDump.getBytes())))
            .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), playerIdentityOptional.get())
                            .title("Data Dump")
                            .addField("Data Count", ""+playerDataDump.lines().count(), true)
                            .description("CSV Generated!")
                            .color(Color.CYAN)
                            .thumbnail(playerLookup.getAvatarURL(playerIdentityOptional.get().uuid()).toString())
                            .build());
    }
}
