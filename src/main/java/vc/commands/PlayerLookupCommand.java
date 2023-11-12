package vc.commands;

import discord4j.core.spec.EmbedCreateSpec;
import vc.util.PlayerLookup;

public abstract class PlayerLookupCommand implements SlashCommand {
    protected final PlayerLookup playerLookup;

    public PlayerLookupCommand(final PlayerLookup playerLookup) {
        this.playerLookup = playerLookup;
    }

    EmbedCreateSpec.Builder populateIdentity(final EmbedCreateSpec.Builder builder, PlayerLookup.PlayerIdentity identity) {
        return builder
            .addField("Player", "[" + identity.playerName() + "](" + playerLookup.getNameMCLink(identity.uuid()) + ")", true)
            .addField("\u200B", "\u200B", true)
            .addField("\u200B", "\u200B", true);
    }
}
