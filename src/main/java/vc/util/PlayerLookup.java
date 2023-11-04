package vc.util;

import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import vc.swagger.minetools_api.handler.UuidApi;
import vc.swagger.minetools_api.model.UUIDAndPlayerName;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.UUID;

@Component
public class PlayerLookup {
    private final UuidApi uuidApi = new UuidApi();
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("PlayerLookup");

    public record PlayerIdentity(UUID uuid, String playerName) { }

    public Optional<PlayerIdentity> getPlayerIdentity(final String playerName) {
        if (!validPlayerName(playerName)) {
            LOGGER.info("Invalid playerName: {}", playerName);
            return Optional.empty();
        }
        final UUIDAndPlayerName uuidAndPlayerName = uuidApi.getUUIDAndPlayerName(playerName.trim());
        if (uuidAndPlayerName == null) return Optional.empty();
        if (uuidAndPlayerName.getStatus() != UUIDAndPlayerName.StatusEnum.OK) return Optional.empty();
        return Optional.of(new PlayerIdentity(UUID.fromString(uuidAndPlayerName
                .getId()
                .replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5")
        ), uuidAndPlayerName.getName()));
    }

    public URL getAvatarURL(UUID uuid) {
        return getAvatarURL(uuid.toString().replace("-", ""));
    }

    public URL getAvatarURL(String playerName) {
        try {
            return new URL(String.format("https://minotar.net/helm/%s/64", playerName));
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean validPlayerName(String username) {
        return username.matches("[a-zA-Z0-9_]{3,16}");
    }
}
