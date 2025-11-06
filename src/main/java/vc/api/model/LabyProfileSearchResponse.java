package vc.api.model;

import org.jspecify.annotations.Nullable;

import java.util.*;

public record LabyProfileSearchResponse(List<LabyProfile> users) {
    public @Nullable ProfileData currentProfile() {
        return users != null && !users.isEmpty() ? users.getFirst() : null;
    }

    public List<ProfileData> historicalProfiles() {
        if (users == null || users.size() < 2) return Collections.emptyList();
        return (List) users.subList(1, users.size());
    }

    public List<String> previousUsernames() {
        if (users == null || users.isEmpty()) return Collections.emptyList();
        var currentProfile = currentProfile();
        if (currentProfile == null) return Collections.emptyList();
        LabyProfile currentLabyProfile = users.getFirst();
        return currentLabyProfile.history().stream()
            .map(LabyNameHistoryProfile::name)
            .distinct()
            .filter(name -> !name.equals(currentProfile.name()))
            .toList();
    }

    public List<String> associatedUsernames() {
        if (users == null || users.size() < 2) return Collections.emptyList();
        var currentProfile = currentProfile();
        if (currentProfile == null) return Collections.emptyList();
        final Set<String> previousUsernames = new HashSet<>();
        previousUsernames().forEach(u -> {
            previousUsernames.add(u.toLowerCase());
        });
        previousUsernames.add(currentProfile.name().toLowerCase());
        List<String> usernames = new ArrayList<>();
        var historicalProfiles = users.subList(1, users.size());
        for (LabyProfile profile : historicalProfiles) {
            var name = profile.name();
            previousUsernames.add(name.toLowerCase());
            var historyList = profile.history();
            if (historyList != null) {
                for (var h : historyList) {
                    var hName = h.name();
                    if (!previousUsernames.contains(hName.toLowerCase())) {
                        usernames.add(hName);
                    }
                }
            }
        }
        return usernames.stream().distinct().toList();
    }
}
