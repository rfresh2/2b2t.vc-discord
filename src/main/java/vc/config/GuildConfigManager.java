package vc.config;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import discord4j.discordjson.json.GuildData;
import discord4j.discordjson.json.GuildFields;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

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
    private final GatewayDiscordClient gatewayDiscordClient;

    public GuildConfigManager(final GuildConfigDatabase guildConfigDatabase,
                              final ScheduledExecutorService scheduledExecutorService,
                              final GatewayDiscordClient gatewayDiscordClient) {
        this.scheduledExecutorService = scheduledExecutorService;
        this.gatewayDiscordClient = gatewayDiscordClient;
        this.guildConfigMap = new ConcurrentHashMap<>();
        this.guildConfigDatabase = guildConfigDatabase;
        this.scheduledExecutorService.scheduleAtFixedRate(this::writeAllGuildConfigs, 1, 1, TimeUnit.DAYS);
    }

    public void loadGuild(final GuildFields guildFields) {
        final GuildConfigRecord guildConfigRecord = guildConfigDatabase.getGuildConfigRecord(guildFields.id().asString())
            .orElse(new GuildConfigRecord(guildFields.id().asString(), guildFields.name(), false, "", false, ""));
        guildConfigMap.put(guildFields.id().asString(), guildConfigRecord);
    }

    public Mono<GuildData> loadGuild(final String guildId) {
        return gatewayDiscordClient.getGuildById(Snowflake.of(guildId))
            .map(Guild::getData)
            .doOnNext(this::loadGuild);
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
