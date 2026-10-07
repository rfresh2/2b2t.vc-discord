package vc.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.Color;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vc.openapi.handler.TabListApi;

import java.io.File;
import java.nio.file.Files;

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
        byte[] image = null;
        File file = null;
        try {
            file = tabListApi.tablistRender();
            if (file != null) image = Files.readAllBytes(file.toPath());
        } catch (final Exception e) {
            LOGGER.error("Failed to get tablist", e);
        } finally {
            if (file != null && !file.delete()) {
                LOGGER.warn("Failed to delete tablist temp file: {}", file);
            }
        }
        if (image == null || image.length == 0) {
            return error(event, "Unable to resolve current tablist");
        }
        return event.getHook().sendFiles(FileUpload.fromData(image, "tablist.png"))
            .addEmbeds(embed(event)
                .setTitle("2b2t Tablist")
                .setImage("attachment://tablist.png")
                .setColor(Color.CYAN)
                .build());
    }
}
