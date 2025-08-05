package vc.config.watch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import vc.config.watch.model.GuildChatWatchConfig;
import vc.config.watch.model.GuildPlayerWatchConfig;
import vc.config.watch.model.UserChatWatchConfig;
import vc.config.watch.model.UserPlayerWatchConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class WatchConfigStore implements DisposableBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchConfigStore.class);
    // user player watches
    private final Map<String, UserPlayerWatchConfig> userPlayerWatchConfigMap = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> targetToUserPlayerWatchMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> ownerToUserPlayerWatchMap = new ConcurrentHashMap<>();

    // guild player watches
    private final Map<String, GuildPlayerWatchConfig> guildPlayerWatchConfigMap = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> targetToGuildPlayerWatchMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> guildToGuildPlayerWatchMap = new ConcurrentHashMap<>();

    // user chat watches
    private final Map<String, UserChatWatchConfig> userChatWatchConfigMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> ownerToUserChatWatchMap = new ConcurrentHashMap<>();

    // guild chat watches
    private final Map<String, GuildChatWatchConfig> guildChatWatchConfigMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> guildToGuildChatWatchMap = new ConcurrentHashMap<>();

    private final GuildChatWatchRepository guildChatWatchRepository;
    private final GuildPlayerWatchRepository guildPlayerWatchRepository;
    private final UserChatWatchRepository userChatWatchRepository;
    private final UserPlayerWatchRepository userPlayerWatchRepository;

    public WatchConfigStore(
        final GuildChatWatchRepository guildChatWatchRepository,
        final GuildPlayerWatchRepository guildPlayerWatchRepository,
        final UserChatWatchRepository userChatWatchRepository,
        final UserPlayerWatchRepository userPlayerWatchRepository
    ) {
        this.guildChatWatchRepository = guildChatWatchRepository;
        this.guildPlayerWatchRepository = guildPlayerWatchRepository;
        this.userChatWatchRepository = userChatWatchRepository;
        this.userPlayerWatchRepository = userPlayerWatchRepository;
    }

    public synchronized void loadUserPlayerWatchConfigs() {
        List<UserPlayerWatchConfig> userWatchConfigs = userPlayerWatchRepository.getUserPlayerWatchConfigs();
        for (UserPlayerWatchConfig record : userWatchConfigs) {
            userPlayerWatchConfigMap.put(record.watchId(), record);
            targetToUserPlayerWatchMap.computeIfAbsent(record.targetUuid(), k -> new HashSet<>()).add(record.watchId());
            ownerToUserPlayerWatchMap.computeIfAbsent(record.ownerUserId(), k -> new HashSet<>()).add(record.watchId());
        }
    }

    public synchronized void loadGuildPlayerWatchConfigs() {
        List<GuildPlayerWatchConfig> guildWatchConfigs = guildPlayerWatchRepository.getGuildPlayerWatchConfigs();
        for (GuildPlayerWatchConfig record : guildWatchConfigs) {
            guildPlayerWatchConfigMap.put(record.watchId(), record);
            targetToGuildPlayerWatchMap.computeIfAbsent(record.targetUuid(), k -> new HashSet<>()).add(record.watchId());
            guildToGuildPlayerWatchMap.computeIfAbsent(record.guildId(), k -> new HashSet<>()).add(record.watchId());
        }
    }

    public synchronized void loadUserChatWatchConfigs() {
        List<UserChatWatchConfig> userChatWatchConfigs = userChatWatchRepository.getUserChatWatchConfigs();
        for (UserChatWatchConfig record : userChatWatchConfigs) {
            userChatWatchConfigMap.put(record.watchId(), record);
            ownerToUserChatWatchMap.computeIfAbsent(record.ownerUserId(), k -> new HashSet<>()).add(record.watchId());
        }
    }

    public synchronized void loadGuildChatWatchConfigs() {
        List<GuildChatWatchConfig> guildChatWatchConfigs = guildChatWatchRepository.getGuildChatWatchConfigs();
        for (GuildChatWatchConfig record : guildChatWatchConfigs) {
            guildChatWatchConfigMap.put(record.watchId(), record);
            guildToGuildChatWatchMap.computeIfAbsent(record.guildId(), k -> new HashSet<>()).add(record.watchId());
        }
    }

    public synchronized void writeAllWatchConfigs() {
        userPlayerWatchConfigMap.values().forEach(userPlayerWatchRepository::writeUserPlayerWatchConfig);
        guildPlayerWatchConfigMap.values().forEach(guildPlayerWatchRepository::writeGuildPlayerWatchConfig);
        userChatWatchConfigMap.values().forEach(userChatWatchRepository::writeUserChatWatchConfig);
        guildChatWatchConfigMap.values().forEach(guildChatWatchRepository::writeGuildChatWatchConfig);
    }

    public synchronized void writeUserPlayerWatchConfig(UserPlayerWatchConfig record) {
        userPlayerWatchConfigMap.put(record.watchId(), record);
        targetToUserPlayerWatchMap.computeIfAbsent(record.targetUuid(), k -> new HashSet<>()).add(record.watchId());
        ownerToUserPlayerWatchMap.computeIfAbsent(record.ownerUserId(), k -> new HashSet<>()).add(record.watchId());
        userPlayerWatchRepository.writeUserPlayerWatchConfig(record);
    }

    public synchronized void writeGuildPlayerWatchConfig(GuildPlayerWatchConfig record) {
        guildPlayerWatchConfigMap.put(record.watchId(), record);
        targetToGuildPlayerWatchMap.computeIfAbsent(record.targetUuid(), k -> new HashSet<>()).add(record.watchId());
        guildToGuildPlayerWatchMap.computeIfAbsent(record.guildId(), k -> new HashSet<>()).add(record.watchId());
        guildPlayerWatchRepository.writeGuildPlayerWatchConfig(record);
    }

    public synchronized void writeUserChatWatchConfig(UserChatWatchConfig record) {
        userChatWatchConfigMap.put(record.watchId(), record);
        ownerToUserChatWatchMap.computeIfAbsent(record.ownerUserId(), k -> new HashSet<>()).add(record.watchId());
        userChatWatchRepository.writeUserChatWatchConfig(record);
    }

    public synchronized void writeGuildChatWatchConfig(GuildChatWatchConfig record) {
        guildChatWatchConfigMap.put(record.watchId(), record);
        guildToGuildChatWatchMap.computeIfAbsent(record.guildId(), k -> new HashSet<>()).add(record.watchId());
        guildChatWatchRepository.writeGuildChatWatchConfig(record);
    }

    public synchronized void removeUserPlayerWatchConfig(UserPlayerWatchConfig record) {
        userPlayerWatchConfigMap.remove(record.watchId());
        var targetToUser = targetToUserPlayerWatchMap.computeIfAbsent(record.targetUuid(), k -> new HashSet<>());
        targetToUser.remove(record.watchId());
        if (targetToUser.isEmpty()) {
            targetToUserPlayerWatchMap.remove(record.targetUuid());
        }
        var ownerToUser = ownerToUserPlayerWatchMap.computeIfAbsent(record.ownerUserId(), k -> new HashSet<>());
        ownerToUser.remove(record.watchId());
        if (ownerToUser.isEmpty()) {
            ownerToUserPlayerWatchMap.remove(record.ownerUserId());
        }
        userPlayerWatchRepository.deleteUserPlayerWatchConfig(record.watchId());
    }

    public synchronized void removeGuildPlayerWatchConfig(GuildPlayerWatchConfig record) {
        guildPlayerWatchConfigMap.remove(record.watchId());
        var targetToGuild = targetToGuildPlayerWatchMap.computeIfAbsent(record.targetUuid(), k -> new HashSet<>());
        targetToGuild.remove(record.watchId());
        if (targetToGuild.isEmpty()) {
            targetToGuildPlayerWatchMap.remove(record.targetUuid());
        }
        var guildToGuild = guildToGuildPlayerWatchMap.computeIfAbsent(record.guildId(), k -> new HashSet<>());
        guildToGuild.remove(record.watchId());
        if (guildToGuild.isEmpty()) {
            guildToGuildPlayerWatchMap.remove(record.guildId());
        }
        guildPlayerWatchRepository.deleteGuildPlayerWatchConfig(record.watchId());
    }

    public synchronized void removeUserChatWatchConfig(UserChatWatchConfig record) {
        userChatWatchConfigMap.remove(record.watchId());
        var ownerToUser = ownerToUserChatWatchMap.computeIfAbsent(record.ownerUserId(), k -> new HashSet<>());
        ownerToUser.remove(record.watchId());
        if (ownerToUser.isEmpty()) {
            ownerToUserChatWatchMap.remove(record.ownerUserId());
        }
        userChatWatchRepository.deleteUserChatWatchConfig(record.watchId());
    }

    public synchronized void removeGuildChatWatchConfig(GuildChatWatchConfig record) {
        guildChatWatchConfigMap.remove(record.watchId());
        var guildToGuild = guildToGuildChatWatchMap.computeIfAbsent(record.guildId(), k -> new HashSet<>());
        guildToGuild.remove(record.watchId());
        if (guildToGuild.isEmpty()) {
            guildToGuildChatWatchMap.remove(record.guildId());
        }
        guildChatWatchRepository.deleteGuildChatWatchConfig(record.watchId());
    }

    public synchronized List<UserPlayerWatchConfig> getUserWatches(UUID targetUuid) {
        var watchIds = targetToUserPlayerWatchMap.getOrDefault(targetUuid, Collections.emptySet());
        if (watchIds.isEmpty()) {
            return Collections.emptyList();
        }
        var result = new ArrayList<UserPlayerWatchConfig>(watchIds.size());
        for (var watchId : watchIds) {
            var record = userPlayerWatchConfigMap.get(watchId);
            if (record != null) {
                result.add(record);
            } else {
                LOGGER.warn("User watch record not found for watchId {}", watchId);
            }
        }
        return result;
    }

    public Map<String, UserPlayerWatchConfig> getAllUserWatchConfigs() {
        return userPlayerWatchConfigMap;
    }

    public Map<String, GuildPlayerWatchConfig> getAllGuildWatchConfigs() {
        return guildPlayerWatchConfigMap;
    }

    public Map<String, UserChatWatchConfig> getAllUserChatWatchConfigs() {
        return userChatWatchConfigMap;
    }

    public Map<String, GuildChatWatchConfig> getAllGuildChatWatchConfigs() {
        return guildChatWatchConfigMap;
    }

    public synchronized List<UserPlayerWatchConfig> getUserWatchesByOwner(String ownerUserId) {
        var watchIds = ownerToUserPlayerWatchMap.getOrDefault(ownerUserId, Collections.emptySet());
        if (watchIds.isEmpty()) {
            return Collections.emptyList();
        }
        var result = new ArrayList<UserPlayerWatchConfig>(watchIds.size());
        for (var watchId : watchIds) {
            var record = userPlayerWatchConfigMap.get(watchId);
            if (record != null) {
                result.add(record);
            } else {
                LOGGER.warn("User watch record not found for watchId {}", watchId);
            }
        }
        return result;
    }

    public synchronized List<UserChatWatchConfig> getUserChatWatchesByOwner(String ownerUserId) {
        var watchIds = ownerToUserChatWatchMap.getOrDefault(ownerUserId, Collections.emptySet());
        if (watchIds.isEmpty()) {
            return Collections.emptyList();
        }
        var result = new ArrayList<UserChatWatchConfig>(watchIds.size());
        for (var watchId : watchIds) {
            var record = userChatWatchConfigMap.get(watchId);
            if (record != null) {
                result.add(record);
            } else {
                LOGGER.warn("User chat watch record not found for watchId {}", watchId);
            }
        }
        return result;
    }

    public synchronized List<GuildPlayerWatchConfig> getGuildWatches(UUID targetUuid) {
        var watchIds = targetToGuildPlayerWatchMap.getOrDefault(targetUuid, Collections.emptySet());
        if (watchIds.isEmpty()) {
            return Collections.emptyList();
        }
        var result = new ArrayList<GuildPlayerWatchConfig>(watchIds.size());
        for (var watchId : watchIds) {
            var record = guildPlayerWatchConfigMap.get(watchId);
            if (record != null) {
                result.add(record);
            } else {
                LOGGER.warn("Guild watch record not found for watchId {}", watchId);
            }
        }
        return result;
    }

    public synchronized List<GuildPlayerWatchConfig> getGuildWatchesByGuild(String guildId) {
        var watchIds = guildToGuildPlayerWatchMap.getOrDefault(guildId, Collections.emptySet());
        if (watchIds.isEmpty()) {
            return Collections.emptyList();
        }
        var result = new ArrayList<GuildPlayerWatchConfig>(watchIds.size());
        for (var watchId : watchIds) {
            var record = guildPlayerWatchConfigMap.get(watchId);
            if (record != null) {
                result.add(record);
            } else {
                LOGGER.warn("Guild watch record not found for watchId {}", watchId);
            }
        }
        return result;
    }

    public synchronized List<GuildChatWatchConfig> getGuildChatWatchesByGuild(String guildId) {
        var watchIds = guildChatWatchConfigMap.values().stream()
            .filter(record -> record.guildId().equals(guildId))
            .map(GuildChatWatchConfig::watchId)
            .collect(Collectors.toSet());
        if (watchIds.isEmpty()) {
            return Collections.emptyList();
        }
        var result = new ArrayList<GuildChatWatchConfig>(watchIds.size());
        for (var watchId : watchIds) {
            var record = guildChatWatchConfigMap.get(watchId);
            if (record != null) {
                result.add(record);
            } else {
                LOGGER.warn("Guild chat watch record not found for watchId {}", watchId);
            }
        }
        return result;
    }

    public synchronized void removeGuildWatchConfigs(final String guildId) {
        var watchesInGuild = guildToGuildPlayerWatchMap.getOrDefault(guildId, Collections.emptySet());
        if (watchesInGuild.isEmpty()) return;
        var copyOfWatchesInGuild = new HashSet<>(watchesInGuild); // Avoid concurrent modification
        for (var watch : copyOfWatchesInGuild) {
            var record = guildPlayerWatchConfigMap.get(watch);
            if (record != null) {
                removeGuildPlayerWatchConfig(record);
            } else {
                LOGGER.warn("Guild watch record not found for watchId {}", watch);
            }
        }
    }

    public synchronized void removeGuildChatWatchConfigs(final String guildId) {
        var watchesInGuild = guildChatWatchConfigMap.values().stream()
            .filter(record -> record.guildId().equals(guildId))
            .map(GuildChatWatchConfig::watchId)
            .collect(Collectors.toSet());
        if (watchesInGuild.isEmpty()) return;
        for (var watch : watchesInGuild) {
            var record = guildChatWatchConfigMap.get(watch);
            if (record != null) {
                removeGuildChatWatchConfig(record);
            } else {
                LOGGER.warn("Guild chat watch record not found for watchId {}", watch);
            }
        }
    }

    @Override
    public void destroy() throws Exception {
        LOGGER.info("Writing all watch configs on shutdown");
        try {
            writeAllWatchConfigs();
        } catch (Exception e) {
            LOGGER.error("Error while writing watch configs on shutdown", e);
        }
    }
}
