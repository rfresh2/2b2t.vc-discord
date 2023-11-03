package vc.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.EmbedData;
import discord4j.rest.util.Color;
import org.springframework.stereotype.Component;
import vc.config.GuildConfigManager;
import vc.config.GuildConfigRecord;
import vc.live.dto.Connections;
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
                           final ObjectMapper objectMapper) {
        super(redisClient,
              discordClient,
              guildConfigManager,
              executorService,
              objectMapper);
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
        return asList(new InputQueue<>("ConnectionsQueue", Connections.class, this::buildConnectionsEmbed));
    }

    protected EmbedData buildConnectionsEmbed(final Connections con) {
        boolean isJoin = con.getConnection() == Connectiontype.JOIN;
        return EmbedCreateSpec.builder()
            .description("**" + escape(con.getPlayerName()) + "** " + (isJoin ? "connected" : "disconnected"))
            .footer("\u200b", avatarUrl(con.getPlayerUuid()).toString())
            .color(isJoin ? Color.SEA_GREEN : Color.RUBY)
            .timestamp(Instant.ofEpochSecond(con.getTime().toEpochSecond()))
            .build()
            .asRequest();
    }
}
