package vc.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.Color;
import vc.api.model.ProfileData;

import java.time.Instant;

public interface SlashCommand {

    String getName();

    WebhookMessageCreateAction<Message> handle(SlashCommandInteractionEvent event);

    default WebhookMessageCreateAction<Message> error(SlashCommandInteractionEvent event, final String message) {
        return error(event.getHook(), message);
    }

    default WebhookMessageCreateAction<Message> error(InteractionHook hook, final String message) {
        return hook.sendMessageEmbeds(new EmbedBuilder()
            .setTitle("Error")
            .setColor(Color.RUBY)
            .setDescription(message)
            .build());
    }

    default EmbedBuilder populateIdentity(final EmbedBuilder builder, ProfileData identity) {
        return builder
            .addField("Player", identity.toDiscordFieldValue(), true)
            .addField("\u200B", "\u200B", true)
            .addField("\u200B", "\u200B", true);
    }

    default EmbedBuilder defaultDecoration(final EmbedBuilder builder, SlashCommandInteractionEvent event) {
        return builder
            .setFooter("Requested by @" + event.getUser().getName(), event.getUser().getEffectiveAvatarUrl())
            .setTimestamp(Instant.now());
    }

    default EmbedBuilder embed(SlashCommandInteractionEvent event) {
        return defaultDecoration(new EmbedBuilder(), event);
    }

    default EmbedBuilder embed(InteractionHook hook) {
        var interaction = hook.getInteraction();
        return new EmbedBuilder()
            .setFooter("Requested by @" + interaction.getUser().getName(), interaction.getUser().getEffectiveAvatarUrl())
            .setTimestamp(Instant.now());
    }
}
