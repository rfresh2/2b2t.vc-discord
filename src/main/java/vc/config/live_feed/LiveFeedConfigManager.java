package vc.config.live_feed;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import discord4j.discordjson.json.GuildData;
import discord4j.discordjson.json.GuildFields;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.config.ConfigDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class LiveFeedConfigManager {
    private final Map<String, LiveFeedConfigRecord> guildConfigMap;
    private final ConfigDatabase configDatabase;
    private final ScheduledExecutorService scheduledExecutorService;
    private final GatewayDiscordClient gatewayDiscordClient;

    public LiveFeedConfigManager(
        final ConfigDatabase configDatabase,
        final ScheduledExecutorService scheduledExecutorService,
        final GatewayDiscordClient gatewayDiscordClient
    ) {
        this.scheduledExecutorService = scheduledExecutorService;
        this.gatewayDiscordClient = gatewayDiscordClient;
        this.guildConfigMap = new ConcurrentHashMap<>();
        this.configDatabase = configDatabase;
        this.scheduledExecutorService.scheduleAtFixedRate(this::writeAllLiveFeedConfigs, 1, 1, TimeUnit.DAYS);
    }

    public void loadGuild(final GuildFields guildFields) {
        final LiveFeedConfigRecord guildConfigRecord = configDatabase.getLiveFeedConfigRecord(guildFields.id().asString())
            .orElse(new LiveFeedConfigRecord(guildFields.id().asString(), guildFields.name(), false, "", false, ""));
        guildConfigMap.put(guildFields.id().asString(), guildConfigRecord);
    }

    public Mono<GuildData> loadGuild(final String guildId) {
        return gatewayDiscordClient.getGuildById(Snowflake.of(guildId))
            .map(Guild::getData)
            .doOnNext(this::loadGuild);
    }

    public Optional<LiveFeedConfigRecord> getLiveFeedConfig(final String guildId) {
        return Optional.ofNullable(guildConfigMap.get(guildId));
    }

    public void writeAllLiveFeedConfigs() {
        guildConfigMap.values().forEach(configDatabase::writeGuildConfigRecord);
        configDatabase.backupDatabase();
    }

    public void writeLiveFeedConfig(final String guildId) {
        Optional.ofNullable(guildConfigMap.get(guildId)).ifPresent(configDatabase::writeGuildConfigRecord);
    }

    public void updateLiveFeedConfig(final LiveFeedConfigRecord guildConfigRecord) {
        guildConfigMap.put(guildConfigRecord.guildId(), guildConfigRecord);
        writeLiveFeedConfig(guildConfigRecord.guildId());
    }

    public List<LiveFeedConfigRecord> getAllGuildConfigs() {
        return new ArrayList<>(guildConfigMap.values());
    }
}
