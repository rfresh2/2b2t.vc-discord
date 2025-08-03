package vc.config.watch;

import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import vc.config.watch.model.GuildPlayerWatchConfig;

import java.util.List;

@Repository
public class GuildPlayerWatchRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildPlayerWatchRepository.class);

    private final Jdbi jdbi;

    public GuildPlayerWatchRepository(final Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public List<GuildPlayerWatchConfig> getGuildPlayerWatchConfigs() {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM guild_player_watch_config")
                .mapTo(GuildPlayerWatchConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving guild watch configs", e);
            return List.of();
        }
    }

    public void writeGuildPlayerWatchConfig(final GuildPlayerWatchConfig config) {
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

    public void deleteGuildPlayerWatchConfig(final String watchId) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("DELETE FROM guild_player_watch_config WHERE watch_id = :watchId")
                .bind("watchId", watchId)
                .execute();
        }
    }
}
