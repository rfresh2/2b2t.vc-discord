package vc.commands;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.live.LiveChat;

import java.util.Optional;

@Component
public class LiveChatCommand implements SlashCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("LiveChatCommand");
    private final LiveChat liveChat;

    public LiveChatCommand(final LiveChat liveChat) {
        this.liveChat = liveChat;
    }

    @Override
    public String getName() {
        return "livechat";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        Optional<Boolean> enabledBoolean = event.getOption("enabled")
            .flatMap(ApplicationCommandInteractionOption::getValue)
            .map(ApplicationCommandInteractionOptionValue::asBoolean);
        Optional<Mono<Channel>> channelArg = event.getOption("channel")
            .flatMap(ApplicationCommandInteractionOption::getValue)
            .map(ApplicationCommandInteractionOptionValue::asChannel);
        if (enabledBoolean.isEmpty() && channelArg.isEmpty()) return error(event, "At least 1 argument is required");
        Optional<Snowflake> guildId = event.getInteraction().getGuildId();
        if (guildId.isEmpty()) return error(event, "This command can only be used in a guild");
        if (enabledBoolean.orElse(true) && channelArg.isEmpty()) return error(event, "Channel is required when enabling live chat");
        if (enabledBoolean.orElse(true)) {
            try {
                final Channel channel = channelArg.get().block();
                if (!testPermissions(guildId.get().asString(), channel)) {
                    return error(event, "Bot must have permissions to send messages in: " + channel.getMention());
                }
                liveChat.enableLiveChat(guildId.get().asString(), channel.getId().asString());
                return event.createFollowup()
                    .withEmbeds(EmbedCreateSpec.builder()
                                    .title("Live Chat Enabled")
                                    .color(Color.CYAN)
                                    .addField("Channel", channel.getMention(), true)
                                    .build());
            } catch (final Throwable e) {
                return error(event, "Unable to enable live chat: " + e.getMessage());
            }
        } else {
            try {
                liveChat.disableLiveChat(guildId.get().asString());
                return event.createFollowup()
                    .withEmbeds(EmbedCreateSpec.builder()
                                    .title("Live Chat Disabled")
                                    .color(Color.CYAN)
                                    .build());
            } catch (final Throwable e) {
                return error(event, "Unable to disable live chat: " + e.getMessage());
            }
        }
    }

    private boolean testPermissions(final String guildId, final Channel channel) {
        try {
            channel.getRestChannel().createMessage(EmbedCreateSpec.builder()
                                                       .description("Live chat message permissions test")
                                                       .color(Color.CYAN)
                                                       .build().asRequest())
                .block();
            return true;
        } catch (final Throwable e) {
            LOGGER.warn("Failed testing permissions for guild: {}, in channel: {}", guildId, channel.getId().asString(), e);
        }
        return false;
    }
}
