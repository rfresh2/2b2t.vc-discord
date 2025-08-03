package vc.config.watch;

import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import vc.config.watch.model.UserPlayerWatchConfig;

import java.util.List;

@Repository
public class UserPlayerWatchRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserPlayerWatchRepository.class);

    private final Jdbi jdbi;

    public UserPlayerWatchRepository(final Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public List<UserPlayerWatchConfig> getUserPlayerWatchConfigs() {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM user_player_watch_config")
                .mapTo(UserPlayerWatchConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving user watch configs", e);
            return List.of();
        }
    }

    public void writeUserPlayerWatchConfig(final UserPlayerWatchConfig config) {
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

    public void deleteUserPlayerWatchConfig(final String watchId) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("DELETE FROM user_player_watch_config WHERE watch_id = :watchId")
                .bind("watchId", watchId)
                .execute();
        }
    }
}
