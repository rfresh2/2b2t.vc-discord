package vc.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.Color;
import org.springframework.stereotype.Component;
import vc.process.GlobalCommandRegistrar;

import java.util.List;

@Component
public class CommandsCommand implements SlashCommand {
    private final List<GlobalCommandRegistrar.RegisteredCommand> commands;

    public CommandsCommand(final GlobalCommandRegistrar registrar) {
        this.commands = registrar.getCommands();
    }

    @Override
    public String getName() {
        return "commands";
    }

    @Override
    public WebhookMessageCreateAction<Message> handle(final SlashCommandInteractionEvent event) {
        var commandInfos = this.commands.stream()
            .map(c -> "`/" + c.name() + "` -> " + c.description())
            .toList();
        return event.getHook().sendMessageEmbeds(embed(event)
            .setTitle("Commands")
            .setDescription(String.join("\n", commandInfos))
            .setColor(Color.CYAN)
            .build());
    }
}
