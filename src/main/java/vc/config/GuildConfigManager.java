package vc.config;

import discord4j.discordjson.json.GuildFields;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class GuildConfigManager {
    private final Map<String, GuildConfigRecord> guildConfigMap;
    private final GuildConfigDatabase guildConfigDatabase;
    private final ScheduledExecutorService scheduledExecutorService;

    public GuildConfigManager(final GuildConfigDatabase guildConfigDatabase, final ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
        this.guildConfigMap = new ConcurrentHashMap<>();
        this.guildConfigDatabase = guildConfigDatabase;
        this.scheduledExecutorService.scheduleAtFixedRate(this::writeAllGuildConfigs, 20, 60 * 24, TimeUnit.MINUTES);
    }

    public void loadGuild(final GuildFields guildFields) {
        final GuildConfigRecord guildConfigRecord = guildConfigDatabase.getGuildConfigRecord(guildFields.id().asString())
            .orElse(new GuildConfigRecord(guildFields.id().asString(), guildFields.name(), false, "", false, ""));
        guildConfigMap.put(guildFields.id().asString(), guildConfigRecord);
    }

    public Optional<GuildConfigRecord> getGuildConfig(final String guildId) {
        return Optional.ofNullable(guildConfigMap.get(guildId));
    }

    public void writeAllGuildConfigs() {
        guildConfigDatabase.backupDatabase();
        guildConfigMap.values().forEach(guildConfigDatabase::writeGuildConfigRecord);
    }

    public void writeGuildConfig(final String guildId) {
        Optional.ofNullable(guildConfigMap.get(guildId)).ifPresent(guildConfigDatabase::writeGuildConfigRecord);
    }

    public void updateGuildConfig(final GuildConfigRecord guildConfigRecord) {
        guildConfigMap.put(guildConfigRecord.guildId(), guildConfigRecord);
        writeGuildConfig(guildConfigRecord.guildId());
    }

    public List<GuildConfigRecord> getAllGuildConfigs() {
        return new ArrayList<>(guildConfigMap.values());
    }
}
