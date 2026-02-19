package vc.commands.options;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ChatInteractionOptionResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatInteractionOptionResolver.class);
    private final List<ChatInteractionOption> options = new ArrayList<>();

    public ChatInteractionOptionResolver registerOption(ChatInteractionOption optionInstance) {
        options.add(optionInstance);
        return this;
    }

    public ChatInteractionOptionContext resolveOptions(SlashCommandInteractionEvent event) {
        var ctx = new ChatInteractionOptionContext(event);
        try {
            for (ChatInteractionOption option : options) {
                if (ctx.isErrorSet()) break;
                option.apply(ctx);
            }
        } catch (final Exception e) {
            LOGGER.error("Error while resolving options for event: {}", event.getName(), e);
            if (!ctx.isErrorSet()) ctx.setError("Error while resolving command options");
        }
        return ctx;
    }
}
