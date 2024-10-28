package vc.api.model;

import java.util.UUID;

public interface ProfileData {
    String name();
    UUID uuid();

    default String getNameMCLink(UUID uuid) {
        return "https://namemc.com/profile/" + uuid.toString();
    }

    default String getAvatarURL() {
        return getAvatarURL(uuid().toString().replace("-", ""));
    }

    default String getAvatarURL(String playerName) {
        return String.format("https://crafthead.net/helm/%s/64", playerName);
    }
}
