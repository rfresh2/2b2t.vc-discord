package vc.api.model;

import java.util.UUID;

public interface ProfileData {
    String name();
    UUID uuid();

    default String getNameMCLink(UUID uuid) {
        return "https://namemc.com/profile/" + uuid.toString();
    }

    default String getAvatarURL() {
        return String.format("https://api.mineatar.io/body/full/%s", uuid());
    }

    default String getHeadURL() {
        return String.format("https://crafthead.net/helm/%s/64", uuid().toString().replace("-", ""));
    }
}
