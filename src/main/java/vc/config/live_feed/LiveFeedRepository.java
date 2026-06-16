package vc.config.live_feed;

import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import vc.config.live_feed.model.LiveFeedConfig;

import java.util.List;
import java.util.Optional;

@Repository
public class LiveFeedRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(LiveFeedRepository.class);
    static final String cacheId = "liveFeed";

    private final Jdbi jdbi;

    public LiveFeedRepository(final Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    @Cacheable(value = cacheId, key = "#root.methodName")
    public List<LiveFeedConfig> getAll() {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM live_feed_config")
                .mapTo(LiveFeedConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving live feed configs", e);
            return List.of();
        }
    }

    @Cacheable(value = cacheId, key = "#root.methodName")
    public List<LiveFeedConfig> getByLiveChatEnabled() {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM live_feed_config WHERE live_chat_enabled = 1")
                .mapTo(LiveFeedConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving live feed configs with live chat enabled", e);
            return List.of();
        }
    }

    @Cacheable(value = cacheId, key = "#root.methodName")
    public List<LiveFeedConfig> getByLiveConnectionsEnabled() {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM live_feed_config WHERE live_connections_enabled = 1")
                .mapTo(LiveFeedConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving live feed configs with live connections enabled", e);
            return List.of();
        }
    }

    @Cacheable(cacheId)
    public Optional<LiveFeedConfig> getByGuild(final String guildId) {
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

    @CacheEvict(cacheNames = cacheId, allEntries = true)
    public void write(final LiveFeedConfig config) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("""
                INSERT OR REPLACE INTO live_feed_config (
                    guild_id,
                    guild_name,
                    live_chat_enabled,
                    live_chat_channel_id,
                    live_connections_enabled,
                    live_connections_channel_id
                ) VALUES (
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

    @CacheEvict(cacheNames = cacheId, allEntries = true)
    public void deleteByGuildId(final String guildId) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("DELETE FROM live_feed_config WHERE guild_id = :guildId")
                .bind("guildId", guildId)
                .execute();
        } catch (final Exception e) {
            LOGGER.error("Error deleting live feed config for guild {}", guildId, e);
        }
    }

    @CacheEvict(cacheNames = cacheId, allEntries = true)
    public void delete(final LiveFeedConfig config) {
        deleteByGuildId(config.guildId());
    }
}
