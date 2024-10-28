package vc.commands.options;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;

import java.util.ArrayList;
import java.util.List;

public class ChatInteractionOptionResolver {
    private final List<ChatInteractionOptionInstance> traits = new ArrayList<>();

    public ChatInteractionOptionResolver registerTrait(ChatInteractionOptionInstance trait) {
        traits.add(trait);
        return this;
    }

    public ChatInputInteractionCommandContext execute(ChatInputInteractionEvent event) {
        var ctx = new ChatInputInteractionCommandContext(event);
        for (ChatInteractionOptionInstance trait : traits) {
            if (ctx.errorSet) break;
            trait.apply(ctx);
        }
        return ctx;
    }
}
