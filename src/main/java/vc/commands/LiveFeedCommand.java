package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.Color;
import discord4j.rest.util.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import vc.live.LiveFeed;

public abstract class LiveFeedCommand implements SlashCommand {
    private final Logger LOGGER = LoggerFactory.getLogger(getClass().getSimpleName());
    private final LiveFeed liveFeed;

    public LiveFeedCommand(final LiveFeed liveFeed) {
        this.liveFeed = liveFeed;
    }

    public abstract String feedName();

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        if (event.getInteraction().getGuildId().isEmpty()) return error(event, "This command can only be used inside a discord server");
        if (!validateUserPermissions(event)) return error(event, "You must have permission: " + Permission.MANAGE_MESSAGES + " to use this command");
        var enableOption = event.getOption("enable");
        var isEnableSubCommand = enableOption.isPresent();
        var isDisableSubCommand = event.getOption("disable").isPresent();
        if (isEnableSubCommand && isDisableSubCommand) return error(event, "Cannot enable and disable at the same time");
        if (!isEnableSubCommand && !isDisableSubCommand) return error(event, "Must specify either enable or disable");
        var guildId = event.getInteraction().getGuildId().get().asString();
        if (isEnableSubCommand) {
            return enableOption
                .flatMap(sub -> sub.getOption("channel"))
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asChannel)
                .map(channelMono -> channelMono.flatMap(channel -> {
                    if (channel == null) return error(event, "Channel is required when enabling " + feedName());
                    try {
                        if (!testPermissions(guildId, channel)) {
                            return error(event, "Bot must have permissions to send messages in: " + channel.getMention());
                        }
                        liveFeed.enableFeed(guildId, channel.getId().asString());
                        return event.createFollowup()
                            .withEmbeds(EmbedCreateSpec.builder()
                                            .title(feedName() + " Enabled")
                                            .color(Color.CYAN)
                                            .addField("Channel", channel.getMention(), true)
                                            .build());
                    } catch (final Throwable e) {
                        return error(event, "Unable to enable " + feedName() + ": " + e.getMessage());
                    }}))
                .orElseGet(() -> error(event, "Channel is required when enabling " + feedName()));
        } else {
            try {
                liveFeed.disableFeed(guildId);
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
            var embed = EmbedCreateSpec.builder()
                .description("✔ " + feedName() + " Permissions Test Success! ✔")
                .color(Color.MEDIUM_SEA_GREEN)
                .build().asRequest();
            channel.getRestChannel().createMessage(embed)
                .block();
            return true;
        } catch (final ClientException clientException) {
            if (clientException.getStatus().code() == 403) {
                LOGGER.warn("Missing permissions for feed: {}, guild: {}, in channel: {}", feedName(), guildId, channel.getId().asString());
                return false;
            } else {
                LOGGER.warn("Failed testing permissions for feed: {}, guild: {}, in channel: {} - [{}] {}", feedName(), guildId, channel.getId().asString(), clientException.getStatus().code(), clientException.getMessage());
            }
        } catch (final Throwable e) {
            LOGGER.warn("Failed testing permissions for feed: {}, guild: {}, in channel: {}", feedName(), guildId, channel.getId().asString(), e);
        }
        return false;
    }
}
