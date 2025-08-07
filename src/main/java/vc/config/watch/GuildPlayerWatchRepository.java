package vc.config.watch;

import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import vc.config.watch.model.GuildPlayerWatchConfig;

import java.util.List;
import java.util.UUID;

@Repository
public class GuildPlayerWatchRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildPlayerWatchRepository.class);
    static final String cacheId = "guildPlayerWatch";

    private final Jdbi jdbi;

    public GuildPlayerWatchRepository(final Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    @Cacheable(value = cacheId, key = "#root.methodName")
    public List<GuildPlayerWatchConfig> getAll() {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM guild_player_watch_config")
                .mapTo(GuildPlayerWatchConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving guild watch configs", e);
            return List.of();
        }
    }

    @Cacheable(cacheId)
    public List<GuildPlayerWatchConfig> getByTargetUuid(final UUID targetUuid) {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM guild_player_watch_config WHERE target_uuid = :targetUuid")
                .bind("targetUuid", targetUuid)
                .mapTo(GuildPlayerWatchConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving guild watch configs for target UUID: {}", targetUuid, e);
            return List.of();
        }
    }

    @Cacheable(cacheId)
    public List<GuildPlayerWatchConfig> getByGuildId(final String guildId) {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM guild_player_watch_config WHERE guild_id = :guildId")
                .bind("guildId", guildId)
                .mapTo(GuildPlayerWatchConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving guild watch configs for guild ID: {}", guildId, e);
            return List.of();
        }
    }

    @CacheEvict(cacheNames = cacheId, allEntries = true)
    public void write(final GuildPlayerWatchConfig config) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("""
                INSERT OR REPLACE INTO guild_player_watch_config VALUES (
                    :watchId,
                    :guildId,
                    :guildName,
                    :channelId,
                    :joins,
                    :leaves,
                    :chats,
                    :deaths,
                    :kills,
                    :mentionUserId,
                    :mentionRoleId,
                    :targetUuid,
                    :targetName
                );
                """)
                .bind("watchId", config.watchId())
                .bind("guildId", config.guildId())
                .bind("guildName", config.guildName())
                .bind("channelId", config.channelId())
                .bind("joins", config.joins())
                .bind("leaves", config.leaves())
                .bind("chats", config.chats())
                .bind("deaths", config.deaths())
                .bind("kills", config.kills())
                .bind("mentionUserId", config.mentionUserId())
                .bind("mentionRoleId", config.mentionRoleId())
                .bind("targetUuid", config.targetUuid())
                .bind("targetName", config.targetName())
                .execute();
        }
    }

    @CacheEvict(cacheNames = cacheId, allEntries = true)
    public void deleteByWatchId(final String watchId) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("DELETE FROM guild_player_watch_config WHERE watch_id = :watchId")
                .bind("watchId", watchId)
                .execute();
        }
    }

    @CacheEvict(cacheNames = cacheId, allEntries = true)
    public void deleteByGuildId(final String guildId) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("DELETE FROM guild_player_watch_config WHERE guild_id = :guildId")
                .bind("guildId", guildId)
                .execute();
        } catch (final Exception e) {
            LOGGER.error("Error deleting guild player watch configs for guild ID: {}", guildId, e);
        }
    }

    @CacheEvict(cacheNames = cacheId, allEntries = true)
    public void delete(final GuildPlayerWatchConfig config) {
        deleteByWatchId(config.watchId());
    }
}
