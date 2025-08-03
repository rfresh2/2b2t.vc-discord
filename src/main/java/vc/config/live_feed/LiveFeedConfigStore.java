package vc.config.live_feed;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import discord4j.discordjson.json.GuildData;
import discord4j.discordjson.json.GuildFields;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.config.live_feed.model.LiveFeedConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LiveFeedConfigStore {
    private final Map<String, LiveFeedConfig> guildConfigMap;
    private final LiveFeedRepository liveFeedRepository;
    private final GatewayDiscordClient gatewayDiscordClient;

    public LiveFeedConfigStore(
        LiveFeedRepository liveFeedRepository,
        GatewayDiscordClient gatewayDiscordClient
    ) {
        this.gatewayDiscordClient = gatewayDiscordClient;
        this.guildConfigMap = new ConcurrentHashMap<>();
        this.liveFeedRepository = liveFeedRepository;
    }

    public void loadGuild(final GuildFields guildFields) {
        final LiveFeedConfig guildConfigRecord = liveFeedRepository.getLiveFeedConfig(guildFields.id().asString())
            .orElse(new LiveFeedConfig(guildFields.id().asString(), guildFields.name(), false, "", false, ""));
        guildConfigMap.put(guildFields.id().asString(), guildConfigRecord);
    }

    public Mono<GuildData> loadGuild(final String guildId) {
        return gatewayDiscordClient.getGuildById(Snowflake.of(guildId))
            .map(Guild::getData)
            .doOnNext(this::loadGuild);
    }

    public Optional<LiveFeedConfig> getLiveFeedConfig(final String guildId) {
        return Optional.ofNullable(guildConfigMap.get(guildId));
    }

    public void writeLiveFeedConfig(final String guildId) {
        Optional.ofNullable(guildConfigMap.get(guildId)).ifPresent(liveFeedRepository::writeLiveFeedConfig);
    }

    public void updateLiveFeedConfig(final LiveFeedConfig guildConfigRecord) {
        guildConfigMap.put(guildConfigRecord.guildId(), guildConfigRecord);
        writeLiveFeedConfig(guildConfigRecord.guildId());
    }

    public List<LiveFeedConfig> getAllGuildConfigs() {
        return new ArrayList<>(guildConfigMap.values());
    }
}
