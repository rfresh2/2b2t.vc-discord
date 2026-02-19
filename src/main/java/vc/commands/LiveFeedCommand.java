package vc.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vc.live.LiveFeed;

public abstract class LiveFeedCommand implements SlashCommand {
    private final Logger LOGGER = LoggerFactory.getLogger(getClass().getSimpleName());
    private final LiveFeed liveFeed;

    public LiveFeedCommand(final LiveFeed liveFeed) {
        this.liveFeed = liveFeed;
    }

    public abstract String feedName();

    @Override
    public WebhookMessageCreateAction<Message> handle(final SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) return error(event, "This command can only be used inside a discord server");
        if (!validateUserPermissions(event)) return error(event, "You must have permission: " + Permission.MESSAGE_MANAGE + " to use this command");

        var subcommand = event.getSubcommandName();
        if (subcommand == null) return error(event, "Must specify either enable or disable");

        var guildId = event.getGuild().getId();
        if ("enable".equals(subcommand)) {
            Channel channel = event.getOption("channel", OptionMapping::getAsChannel);
            if (channel == null) return error(event, "Channel is required when enabling " + feedName());
            if (!(channel instanceof GuildMessageChannel messageChannel)) {
                return error(event, "Selected channel must be a text channel");
            }
            try {
                if (!testPermissions(guildId, messageChannel)) {
                    return error(event, "Bot must have permissions to send messages in: " + messageChannel.getAsMention());
                }
                liveFeed.enableFeed(guildId, messageChannel.getId());
                return event.getHook().sendMessageEmbeds(embed(event)
                    .setTitle(feedName() + " Enabled")
                    .setColor(Color.CYAN)
                    .addField("Channel", messageChannel.getAsMention(), true)
                    .build());
            } catch (final Throwable e) {
                return error(event, "Unable to enable " + feedName() + ": " + e.getMessage());
            }
        }
        if (!"disable".equals(subcommand)) {
            return error(event, "Must specify either enable or disable");
        }
        try {
            liveFeed.disableFeed(guildId);
            return event.getHook().sendMessageEmbeds(embed(event)
                .setTitle(feedName() + " Disabled")
                .setColor(Color.CYAN)
                .build());
        } catch (final Throwable e) {
            return error(event, "Unable to disable " + feedName() + ": " + e.getMessage());
        }
    }

    private boolean validateUserPermissions(final SlashCommandInteractionEvent event) {
        var member = event.getMember();
        if (member == null) return false;
        return member.hasPermission(Permission.MESSAGE_MANAGE) || member.hasPermission(Permission.ADMINISTRATOR);
    }

    private boolean testPermissions(final String guildId, final GuildMessageChannel channel) {
        try {
            var testMessage = channel.sendMessageEmbeds(new EmbedBuilder()
                .setDescription("✔ " + feedName() + " Permissions Test Success! ✔")
                .setColor(Color.MEDIUM_SEA_GREEN)
                .build()).submit().get();
//            testMessage.delete().queue();
            return true;
        } catch (final Throwable e) {
            LOGGER.warn("Failed testing permissions for feed: {}, guild: {}, in channel: {}", feedName(), guildId, channel.getId(), e);
            return false;
        }
    }
}
