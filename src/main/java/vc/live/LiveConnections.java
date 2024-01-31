package vc.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.EmbedData;
import discord4j.rest.util.Color;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vc.config.GuildConfigManager;
import vc.config.GuildConfigRecord;
import vc.live.dto.ConnectionsRecord;
import vc.live.dto.enums.Connectiontype;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.Arrays.asList;

@Component
public class LiveConnections extends LiveFeed {
    public LiveConnections(final RedisClient redisClient,
                           final GatewayDiscordClient discordClient,
                           final GuildConfigManager guildConfigManager,
                           final ScheduledExecutorService executorService,
                           final ObjectMapper objectMapper,
                           @Value("${LIVE_FEEDS}")
                           final String liveFeedsEnabled
    ) {
        super(redisClient,
              discordClient,
              guildConfigManager,
              executorService,
              objectMapper,
              Boolean.parseBoolean(liveFeedsEnabled));
    }

    @Override
    public boolean channelEnabledPredicate(final GuildConfigRecord guildConfigRecord) {
        return guildConfigRecord.liveConnectionsEnabled();
    }

    @Override
    protected String liveChannelId(final GuildConfigRecord guildConfigRecord) {
        return guildConfigRecord.liveConnectionsChannelId();
    }

    @Override
    public GuildConfigRecord disableRecordInternal(final GuildConfigRecord in) {
        return new GuildConfigRecord(in.guildId(), in.guildName(), in.liveChatEnabled(), in.liveChatChannelId(), false, in.liveConnectionsChannelId());
    }

    @Override
    protected GuildConfigRecord enableRecordInternal(final GuildConfigRecord in, final String guildId, final String channelId) {
        return new GuildConfigRecord(in.guildId(), in.guildName(), in.liveChatEnabled(), in.liveChatChannelId(), true, channelId);
    }

    @Override
    protected List<InputQueue> inputQueues() {
        return asList(new InputQueue<>("ConnectionsQueue",
                                       ConnectionsRecord.class,
                                       this::buildConnectionsEmbed,
                                       this::getConnectionTimestamp));
    }

    protected EmbedData buildConnectionsEmbed(final ConnectionsRecord con) {
        boolean isJoin = con.connection() == Connectiontype.JOIN;
        return EmbedCreateSpec.builder()
            .description("**" + escape(con.playerName()) + "** " + (isJoin ? "connected" : "disconnected"))
            .footer("\u200b", avatarUrl(con.playerUuid()).toString())
            .color(isJoin ? Color.SEA_GREEN : Color.RUBY)
            .timestamp(Instant.ofEpochSecond(con.time().toEpochSecond()))
            .build()
            .asRequest();
    }

    protected long getConnectionTimestamp(final ConnectionsRecord con) {
        return con.time().toEpochSecond();
    }
}
