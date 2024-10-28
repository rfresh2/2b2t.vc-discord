package vc.commands.options;

import vc.api.model.ProfileData;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.util.Optional;

public class PlayerLookupOption implements ChatInteractionOption {
    private final PlayerLookup playerLookup;

    public PlayerLookupOption(final PlayerLookup playerLookup) {
        this.playerLookup = playerLookup;
    }

    @Override
    public void apply(final ChatInteractionOptionContext context) {
        var playerNameOptional = context.event.getOptionAsString("player");
        if (playerNameOptional.isEmpty()) {
            context.setError("No player name");
            return;
        }
        String playerName = playerNameOptional.get();
        if (!Validator.isValidPlayerName(playerName)) {
            context.setError("Invalid player name");
            return;
        }
        Optional<ProfileData> playerIdentity = playerLookup.getPlayerIdentity(playerName);
        if (playerIdentity.isEmpty()) {
            context.setError("Player not found");
            return;
        }
        context.profileData = playerIdentity.get();
    }
}
