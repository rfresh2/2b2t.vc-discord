package vc.commands;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import discord4j.rest.util.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import vc.live.LiveFeed;

import java.util.Optional;

public abstract class LiveFeedCommand implements SlashCommand {
    private final Logger LOGGER = LoggerFactory.getLogger(getClass().getSimpleName());
    private final LiveFeed liveFeed;

    public LiveFeedCommand(final LiveFeed liveFeed) {
        this.liveFeed = liveFeed;
    }

    public abstract String feedName();

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        if (!validateUserPermissions(event)) return error(event, "You must have permission: " + Permission.MANAGE_MESSAGES
            + " to use this command");
        Optional<Boolean> enabledBoolean = event.getOption("enabled")
            .flatMap(ApplicationCommandInteractionOption::getValue)
            .map(ApplicationCommandInteractionOptionValue::asBoolean);
        Optional<Mono<Channel>> channelArg = event.getOption("channel")
            .flatMap(ApplicationCommandInteractionOption::getValue)
            .map(ApplicationCommandInteractionOptionValue::asChannel);
        if (enabledBoolean.isEmpty() && channelArg.isEmpty()) return error(event, "At least 1 argument is required");
        Optional<Snowflake> guildId = event.getInteraction().getGuildId();
        if (guildId.isEmpty()) return error(event, "This command can only be used in a guild");
        if (enabledBoolean.orElse(true) && channelArg.isEmpty()) return error(event, "Channel is required when enabling " + feedName());
        if (enabledBoolean.orElse(true)) {
            try {
                final Channel channel = channelArg.get().block();
                if (!testPermissions(guildId.get().asString(), channel)) {
                    return error(event, "Bot must have permissions to send messages in: " + channel.getMention());
                }
                liveFeed.enableFeed(guildId.get().asString(), channel.getId().asString());
                return event.createFollowup()
                    .withEmbeds(EmbedCreateSpec.builder()
                                    .title(feedName() + " Enabled")
                                    .color(Color.CYAN)
                                    .addField("Channel", channel.getMention(), true)
                                    .build());
            } catch (final Throwable e) {
                return error(event, "Unable to enable " + feedName() + ": " + e.getMessage());
            }
        } else {
            try {
                liveFeed.disableFeed(guildId.get().asString());
                return event.createFollowup()
                    .withEmbeds(EmbedCreateSpec.builder()
                                    .title(feedName() + " Disabled")
                                    .color(Color.CYAN)
                                    .build());
            } catch (final Throwable e) {
                return error(event, "Unable to disable " + feedName() + ": " + e.getMessage());
            }
        }
    }

    private boolean validateUserPermissions(final ChatInputInteractionEvent event) {
        return event.getInteraction().getMember()
            .map(member -> member.getBasePermissions().block())
            .map(perms -> perms.contains(Permission.MANAGE_MESSAGES) || perms.contains(Permission.ADMINISTRATOR))
            .orElse(false);
    }

    private boolean testPermissions(final String guildId, final Channel channel) {
        try {
            channel.getRestChannel().createMessage(EmbedCreateSpec.builder()
                                                       .description(feedName() + " message permissions test")
                                                       .color(Color.CYAN)
                                                       .build().asRequest())
                .block();
            return true;
        } catch (final Throwable e) {
            LOGGER.warn("Failed testing permissions for feed: {}, guild: {}, in channel: {}", feedName(), guildId, channel.getId().asString(), e);
        }
        return false;
    }
}
