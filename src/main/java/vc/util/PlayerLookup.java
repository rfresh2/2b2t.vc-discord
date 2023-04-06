package vc.util;

import vc.swagger.mojang_api.handler.ProfilesApi;
import vc.swagger.mojang_api.model.InlineResponse2001;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PlayerLookup {
    private final ProfilesApi profilesApi = new ProfilesApi();

    public Optional<UUID> getPlayerUUID(final String username) {
        List<InlineResponse2001> profileUuid = profilesApi.getProfileUuid(List.of(username));
        return profileUuid.stream().findFirst()
                .map(InlineResponse2001::getId)
                .map(s -> s.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"))
                .map(UUID::fromString);
    }
}
