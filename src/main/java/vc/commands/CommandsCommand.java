package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CommandsCommand implements SlashCommand {
    private final Collection<SlashCommand> commands;

    public CommandsCommand(final Collection<SlashCommand> commands) {
        this.commands = commands;
    }

    @Override
    public String getName() {
        return "commands";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        // todo: add descriptions for each command
        //   we should be able to grab this from the json

        List<String> commandNames = this.commands.stream()
                .map(SlashCommand::getName)
                .toList();
        return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                        .title("Commands")
                        .description(commandNames.stream().collect(Collectors.joining("\n/", "/", "")))
                        .color(Color.CYAN)
                        .build());
    }
}
