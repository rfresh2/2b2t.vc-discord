package vc.commands.options;

import vc.api.model.ProfileData;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.util.Optional;

public class PlayerLookupOption implements ChatInteractionOption {
    private final PlayerLookup playerLookup;
    private final boolean optional;

    public PlayerLookupOption(final PlayerLookup playerLookup, final boolean optional) {
        this.playerLookup = playerLookup;
        this.optional = optional;
    }

    public PlayerLookupOption(final PlayerLookup playerLookup) {
        this(playerLookup, false);
    }

    @Override
    public void apply(final ChatInteractionOptionContext context) {
        var playerNameOptional = context.event.getOptionAsString("player");
        if (playerNameOptional.isEmpty()) {
            if (!optional) context.setError("Player name required");
            return;
        }
        String playerName = playerNameOptional.get().trim();
        if (!Validator.isValidPlayerName(playerName)) {
            context.setError("Invalid player name");
            return;
        }
        Optional<ProfileData> playerIdentity = playerLookup.getPlayerIdentity(playerName);
        if (playerIdentity.isEmpty()) {
            context.setError("No player named `" + playerName + "` exists");
            return;
        }
        context.profileData = playerIdentity.get();
    }
}
