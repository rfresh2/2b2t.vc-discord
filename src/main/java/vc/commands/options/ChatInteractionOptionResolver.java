package vc.commands.options;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;

import java.util.ArrayList;
import java.util.List;

public class ChatInteractionOptionResolver {
    private final List<ChatInteractionOptionInstance> optionInstances = new ArrayList<>();

    public ChatInteractionOptionResolver registerOption(ChatInteractionOptionInstance optionInstance) {
        optionInstances.add(optionInstance);
        return this;
    }

    public ChatInputInteractionCommandContext resolveOptions(ChatInputInteractionEvent event) {
        var ctx = new ChatInputInteractionCommandContext(event);
        for (ChatInteractionOptionInstance optionInstance : optionInstances) {
            if (ctx.errorSet) break;
            optionInstance.apply(ctx);
        }
        return ctx;
    }
}
