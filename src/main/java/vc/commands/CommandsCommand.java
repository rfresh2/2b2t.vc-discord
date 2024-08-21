package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.util.Color;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.process.GlobalCommandRegistrar;

import java.util.List;

@Component
public class CommandsCommand implements SlashCommand {
    List<ApplicationCommandRequest> commands;

    public CommandsCommand(final GlobalCommandRegistrar registrar) {
        this.commands = registrar.getCommands();
    }

    @Override
    public String getName() {
        return "commands";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        var commandInfos = this.commands.stream()
                .map(c -> "`/" + c.name() + "` -> " + c.description().toOptional().orElse(""))
                .toList();
        return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                        .title("Commands")
                        .description(String.join("\n", commandInfos))
                        .color(Color.CYAN)
                        .build());
    }
}
