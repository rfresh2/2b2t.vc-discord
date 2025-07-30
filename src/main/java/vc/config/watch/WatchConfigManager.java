package vc.config.watch;

import discord4j.core.GatewayDiscordClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import vc.config.ConfigDatabase;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

@Component
public class WatchConfigManager implements DisposableBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchConfigManager.class);
    private final Map<String, UserWatchConfigRecord> userWatchConfigMap = new ConcurrentHashMap<>();
    private final Map<String, GuildWatchConfigRecord> guildWatchConfigMap = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> targetToUserWatchMap = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> targetToGuildWatchMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> ownerToUserWatchMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> guildToGuildWatchMap = new ConcurrentHashMap<>();
    private final ConfigDatabase configDatabase;
    private final ScheduledExecutorService scheduledExecutorService;
    private final GatewayDiscordClient gatewayDiscordClient;

    public WatchConfigManager(
        ConfigDatabase configDatabase,
        ScheduledExecutorService scheduledExecutorService,
        GatewayDiscordClient gatewayDiscordClient
    ) {
        this.scheduledExecutorService = scheduledExecutorService;
        this.configDatabase = configDatabase;
        this.gatewayDiscordClient = gatewayDiscordClient;
        loadUserWatchConfigs();
    }

    public synchronized void loadUserWatchConfigs() {
        List<UserWatchConfigRecord> userWatchConfigs = configDatabase.getUserWatchConfigs();
        for (UserWatchConfigRecord record : userWatchConfigs) {
            userWatchConfigMap.put(record.watchId(), record);
            targetToUserWatchMap.computeIfAbsent(record.targetUuid(), k -> new HashSet<>()).add(record.watchId());
            ownerToUserWatchMap.computeIfAbsent(record.ownerUserId(), k -> new HashSet<>()).add(record.watchId());
        }
    }

    public synchronized void loadGuildWatchConfigs() {
        List<GuildWatchConfigRecord> guildWatchConfigs = configDatabase.getGuildWatchConfigs();
        for (GuildWatchConfigRecord record : guildWatchConfigs) {
            guildWatchConfigMap.put(record.watchId(), record);
            targetToGuildWatchMap.computeIfAbsent(record.targetUuid(), k -> new HashSet<>()).add(record.watchId());
            guildToGuildWatchMap.computeIfAbsent(record.guildId(), k -> new HashSet<>()).add(record.watchId());
        }
    }

    public synchronized void writeAllWatchConfigs() {
        userWatchConfigMap.values().forEach(configDatabase::writeUserWatchConfig);
        guildWatchConfigMap.values().forEach(configDatabase::writeGuildWatchConfig);
    }

    public synchronized void updateUserWatchConfig(UserWatchConfigRecord record) {
        userWatchConfigMap.put(record.watchId(), record);
        targetToUserWatchMap.computeIfAbsent(record.targetUuid(), k -> new HashSet<>()).add(record.watchId());
        ownerToUserWatchMap.computeIfAbsent(record.ownerUserId(), k -> new HashSet<>()).add(record.watchId());
        configDatabase.writeUserWatchConfig(record);
    }

    public synchronized void updateGuildWatchConfig(GuildWatchConfigRecord record) {
        guildWatchConfigMap.put(record.watchId(), record);
        targetToGuildWatchMap.computeIfAbsent(record.targetUuid(), k -> new HashSet<>()).add(record.watchId());
        guildToGuildWatchMap.computeIfAbsent(record.guildId(), k -> new HashSet<>()).add(record.watchId());
        configDatabase.writeGuildWatchConfig(record);
    }

    public synchronized void removeUserWatchConfig(UserWatchConfigRecord record) {
        userWatchConfigMap.remove(record.watchId());
        var targetToUser = targetToUserWatchMap.computeIfAbsent(record.targetUuid(), k -> new HashSet<>());
        targetToUser.remove(record.watchId());
        if (targetToUser.isEmpty()) {
            targetToUserWatchMap.remove(record.targetUuid());
        }
        var ownerToUser = ownerToUserWatchMap.computeIfAbsent(record.ownerUserId(), k -> new HashSet<>());
        ownerToUser.remove(record.watchId());
        if (ownerToUser.isEmpty()) {
            ownerToUserWatchMap.remove(record.ownerUserId());
        }
        configDatabase.deleteUserWatchConfig(record.watchId());
    }

    public synchronized void removeGuildWatchConfig(GuildWatchConfigRecord record) {
        guildWatchConfigMap.remove(record.watchId());
        var targetToGuild = targetToGuildWatchMap.computeIfAbsent(record.targetUuid(), k -> new HashSet<>());
        targetToGuild.remove(record.watchId());
        if (targetToGuild.isEmpty()) {
            targetToGuildWatchMap.remove(record.targetUuid());
        }
        var guildToGuild = guildToGuildWatchMap.computeIfAbsent(record.guildId(), k -> new HashSet<>());
        guildToGuild.remove(record.watchId());
        if (guildToGuild.isEmpty()) {
            guildToGuildWatchMap.remove(record.guildId());
        }
        configDatabase.deleteGuildWatchConfig(record.watchId());
    }

    public synchronized List<UserWatchConfigRecord> getUserWatches(UUID targetUuid) {
        var watchIds = targetToUserWatchMap.getOrDefault(targetUuid, Collections.emptySet());
        if (watchIds.isEmpty()) {
            return Collections.emptyList();
        }
        var result = new ArrayList<UserWatchConfigRecord>(watchIds.size());
        for (var watchId : watchIds) {
            var record = userWatchConfigMap.get(watchId);
            if (record != null) {
                result.add(record);
            } else {
                LOGGER.warn("User watch record not found for watchId {}", watchId);
            }
        }
        return result;
    }

    public Map<String, UserWatchConfigRecord> getAllUserWatchConfigs() {
        return userWatchConfigMap;
    }

    public Map<String, GuildWatchConfigRecord> getAllGuildWatchConfigs() {
        return guildWatchConfigMap;
    }

    public synchronized List<UserWatchConfigRecord> getUserWatchesByOwner(String ownerUserId) {
        var watchIds = ownerToUserWatchMap.getOrDefault(ownerUserId, Collections.emptySet());
        if (watchIds.isEmpty()) {
            return Collections.emptyList();
        }
        var result = new ArrayList<UserWatchConfigRecord>(watchIds.size());
        for (var watchId : watchIds) {
            var record = userWatchConfigMap.get(watchId);
            if (record != null) {
                result.add(record);
            } else {
                LOGGER.warn("User watch record not found for watchId {}", watchId);
            }
        }
        return result;
    }

    public synchronized List<GuildWatchConfigRecord> getGuildWatches(UUID targetUuid) {
        var watchIds = targetToGuildWatchMap.getOrDefault(targetUuid, Collections.emptySet());
        if (watchIds.isEmpty()) {
            return Collections.emptyList();
        }
        var result = new ArrayList<GuildWatchConfigRecord>(watchIds.size());
        for (var watchId : watchIds) {
            var record = guildWatchConfigMap.get(watchId);
            if (record != null) {
                result.add(record);
            } else {
                LOGGER.warn("Guild watch record not found for watchId {}", watchId);
            }
        }
        return result;
    }

    public synchronized List<GuildWatchConfigRecord> getGuildWatchesByGuild(String guildId) {
        var watchIds = guildToGuildWatchMap.getOrDefault(guildId, Collections.emptySet());
        if (watchIds.isEmpty()) {
            return Collections.emptyList();
        }
        var result = new ArrayList<GuildWatchConfigRecord>(watchIds.size());
        for (var watchId : watchIds) {
            var record = guildWatchConfigMap.get(watchId);
            if (record != null) {
                result.add(record);
            } else {
                LOGGER.warn("Guild watch record not found for watchId {}", watchId);
            }
        }
        return result;
    }

    public synchronized void removeGuildWatchConfigs(final String guildId) {
        var watchesInGuild = guildToGuildWatchMap.getOrDefault(guildId, Collections.emptySet());
        if (watchesInGuild.isEmpty()) return;
        var copyOfWatchesInGuild = new HashSet<>(watchesInGuild); // Avoid concurrent modification
        for (var watch : copyOfWatchesInGuild) {
            var record = guildWatchConfigMap.get(watch);
            if (record != null) {
                removeGuildWatchConfig(record);
            } else {
                LOGGER.warn("Guild watch record not found for watchId {}", watch);
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
