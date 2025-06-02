package vc.commands;

import de.siegmar.fastcsv.writer.CsvWriter;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateFields;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.openapi.handler.TabListApi;
import vc.openapi.model.TablistEntry;
import vc.openapi.model.TablistResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Comparator;

@Component
public class TablistCommand implements SlashCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(TablistCommand.class);

    private final TabListApi tabListApi;

    public TablistCommand(final TabListApi tabListApi) {
        this.tabListApi = tabListApi;
    }

    @Override
    public String getName() {
        return "tablist";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        TablistResponse response = null;
        try {
            response = tabListApi.onlinePlayers();
        } catch (final Exception e) {
            LOGGER.error("Failed to get tablist", e);
        }
        if (response == null || response.getPlayers() == null || response.getPlayers().isEmpty())
            return error(event, "Unable to resolve current tablist");
        var entries = response.getPlayers().stream()
            .map(TablistEntry::getPlayerName)
            .distinct()
            .sorted(Comparator.comparing((v) -> v, String::compareToIgnoreCase))
            .toList();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (CsvWriter csv = CsvWriter.builder().build(bos)) {
            for (var entry : entries) {
                csv.writeRecord(entry);
            }
        } catch (final Exception e) {
            LOGGER.error("Failed to write CSV", e);
        }
        return event.createFollowup()
            .withFiles(MessageCreateFields.File.of("tablist.csv", new ByteArrayInputStream(bos.toByteArray())))
            .withEmbeds(EmbedCreateSpec.builder()
                .description(escape(response.getHeader()))
                .addField("Player Count", entries.size()+"", false)
                .color(Color.CYAN)
                .build());
    }
}
