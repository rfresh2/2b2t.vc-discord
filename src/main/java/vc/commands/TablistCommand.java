package vc.commands;

import de.siegmar.fastcsv.writer.CsvWriter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.Color;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vc.openapi.handler.TabListApi;
import vc.openapi.model.TablistEntry;
import vc.openapi.model.TablistResponse;

import java.io.ByteArrayOutputStream;
import java.util.Comparator;

import static vc.util.DiscordMarkdownEscape.escape;

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
    public WebhookMessageCreateAction<Message> handle(final SlashCommandInteractionEvent event) {
        TablistResponse response = null;
        try {
            response = tabListApi.onlinePlayers();
        } catch (final Exception e) {
            LOGGER.error("Failed to get tablist", e);
        }
        if (response == null || response.getPlayers() == null || response.getPlayers().isEmpty()) {
            return error(event, "Unable to resolve current tablist");
        }
        var entries = response.getPlayers().stream()
            .map(TablistEntry::getPlayerName)
            .distinct()
            .sorted(Comparator.comparing(v -> v, String::compareToIgnoreCase))
            .toList();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (CsvWriter csv = CsvWriter.builder().build(bos)) {
            for (var entry : entries) {
                csv.writeRecord(entry);
            }
        } catch (final Exception e) {
            LOGGER.error("Failed to write CSV", e);
        }
        return event.getHook().sendFiles(FileUpload.fromData(bos.toByteArray(), "tablist.csv"))
            .addEmbeds(embed(event)
                .setDescription(escape(response.getHeader()))
                .addField("Player Count", String.valueOf(entries.size()), false)
                .setColor(Color.CYAN)
                .build());
    }
}
