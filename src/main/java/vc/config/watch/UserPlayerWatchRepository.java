package vc.config.watch;

import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import vc.config.watch.model.UserPlayerWatchConfig;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Repository
public class UserPlayerWatchRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserPlayerWatchRepository.class);
    static final String cacheId = "userPlayerWatch";

    private final Jdbi jdbi;

    public UserPlayerWatchRepository(final Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    @Cacheable(value = cacheId, key = "#root.methodName")
    public List<UserPlayerWatchConfig> getAll() {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM user_player_watch_config")
                .mapTo(UserPlayerWatchConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving user watch configs", e);
            return List.of();
        }
    }

    @Cacheable(cacheId)
    public List<UserPlayerWatchConfig> getByTargetUuid(final UUID targetUuid) {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM user_player_watch_config WHERE target_uuid = :targetUuid")
                .bind("targetUuid", targetUuid)
                .mapTo(UserPlayerWatchConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving user watch configs for target UUID: {}", targetUuid, e);
            return Collections.emptyList();
        }
    }

    @Cacheable(cacheId)
    public List<UserPlayerWatchConfig> getByOwnerId(String ownerUserId) {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM user_player_watch_config WHERE owner_user_id = :ownerUserId")
                .bind("ownerUserId", ownerUserId)
                .mapTo(UserPlayerWatchConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving user watch configs for owner user ID: {}", ownerUserId, e);
            return Collections.emptyList();
        }
    }

    @CacheEvict(cacheNames = cacheId, allEntries = true)
    public void write(final UserPlayerWatchConfig config) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("""
                INSERT OR REPLACE INTO user_player_watch_config VALUES (
                    :watchId,
                    :ownerUserId,
                    :ownerUserName,
                    :joins,
                    :leaves,
                    :chats,
                    :deaths,
                    :kills,
                    :targetUuid,
                    :targetName
                );
                """)
                .bind("watchId", config.watchId())
                .bind("ownerUserId", config.ownerUserId())
                .bind("ownerUserName", config.ownerUserName())
                .bind("joins", config.joins())
                .bind("leaves", config.leaves())
                .bind("chats", config.chats())
                .bind("deaths", config.deaths())
                .bind("kills", config.kills())
                .bind("targetUuid", config.targetUuid())
                .bind("targetName", config.targetName())
                .execute();
        }
    }

    @CacheEvict(cacheNames = cacheId, allEntries = true)
    public void deleteByWatchId(final String watchId) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("DELETE FROM user_player_watch_config WHERE watch_id = :watchId")
                .bind("watchId", watchId)
                .execute();
        }
    }

    @CacheEvict(cacheNames = cacheId, allEntries = true)
    public void delete(final UserPlayerWatchConfig config) {
        deleteByWatchId(config.watchId());
    }
}
