package vc.config.live_feed;

import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import vc.config.live_feed.model.LiveFeedConfig;

import java.util.Optional;

@Repository
public class LiveFeedRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(LiveFeedRepository.class);

    private final Jdbi jdbi;

    public LiveFeedRepository(final Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public Optional<LiveFeedConfig> getLiveFeedConfig(final String guildId) {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM live_feed_config WHERE guild_id = :guildId")
                .bind("guildId", guildId)
                .mapTo(LiveFeedConfig.class)
                .findFirst();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving live feed config record for guild {}", guildId, e);
            return Optional.empty();
        }
    }

    public void writeLiveFeedConfig(final LiveFeedConfig config) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("""
                INSERT OR REPLACE INTO live_feed_config VALUES (
                    :guildId,
                    :guildName,
                    :liveChatEnabled,
                    :liveChatChannelId,
                    :liveConnectionsEnabled,
                    :liveConnectionsChannelId)
                """)
                .bind("guildId", config.guildId())
                .bind("guildName", config.guildName())
                .bind("liveChatEnabled", config.liveChatEnabled())
                .bind("liveChatChannelId", config.liveChatChannelId())
                .bind("liveConnectionsEnabled", config.liveConnectionsEnabled())
                .bind("liveConnectionsChannelId", config.liveConnectionsChannelId())
                .execute();
        }
    }
}
