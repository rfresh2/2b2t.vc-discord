package vc.api.laby.model;

import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public record LabyProfileSearch(String username, LabyProfileSearchResponse response) {
    List<LabyProfile> users() {
        return response == null || response.users() == null ? Collections.emptyList() : response.users();
    }

    public @Nullable LabyProfile currentProfile() {
        if (users().isEmpty()) return null;
        for (var user : users()) {
            if (user.name().equalsIgnoreCase(username)) {
                return user;
            }
        }
        return null;
    }

    public List<LabyProfile> historicalProfiles() {
        var users = users();
        if (users.isEmpty()) return Collections.emptyList();
        var currentProfile = currentProfile();
        if (currentProfile != null && users.size() == 1) return Collections.emptyList();
        return users.stream()
            .filter(user -> !Objects.equals(currentProfile, user))
            .collect(Collectors.toList());
    }

    public List<String> previousUsernames() {
        var currentProfile = currentProfile();
        if (currentProfile == null) return Collections.emptyList();
        return currentProfile.nameHistory().stream()
            .filter(name -> !name.equalsIgnoreCase(username))
            .distinct()
            .toList();
    }

    public List<String> associatedUsernames() {
        var historicalProfiles = historicalProfiles();
        if (historicalProfiles.isEmpty()) return Collections.emptyList();


        Set<String> currentProfileNames = new HashSet<>();
        currentProfileNames.add(username);
        var currentProfile = currentProfile();
        if (currentProfile != null) {
            currentProfileNames.addAll(currentProfile.nameHistory());
        }
        Set<String> associatedUsernames = new HashSet<>();
        for (var profile : historicalProfiles) {
            var name = profile.name();
            if (!currentProfileNames.contains(name)) {
                associatedUsernames.add(name);
            }
            var historyList = profile.history();
            if (historyList != null) {
                for (var h : historyList) {
                    var hName = h.name();
                    if (!currentProfileNames.contains(hName)) {
                        associatedUsernames.add(hName);
                    }
                }
            }
        }
        return new ArrayList<>(associatedUsernames);
    }
}
