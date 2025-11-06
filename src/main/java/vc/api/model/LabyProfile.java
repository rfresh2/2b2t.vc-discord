package vc.api.model;

import java.util.List;
import java.util.UUID;

public record LabyProfile(String name, UUID uuid, List<LabyNameHistoryProfile> history) implements ProfileData {
    public List<String> nameHistory() {
        return history.stream().map(LabyNameHistoryProfile::name).toList();
    }
}
