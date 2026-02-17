package vc.listeners;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vc.config.live_feed.LiveFeedRepository;
import vc.live.LiveFeedManager;
import vc.live.watch.WatchManager;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class GuildListener extends ListenerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("GuildListener");
    private final LiveFeedRepository liveFeedRepository;
    private final LiveFeedManager liveFeedManager;
    private final WatchManager watchManager;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public GuildListener(
        final JDA jda,
        final LiveFeedRepository liveFeedRepository,
        final LiveFeedManager liveFeedManager,
        final WatchManager watchManager
    ) {
        this.liveFeedRepository = liveFeedRepository;
        this.liveFeedManager = liveFeedManager;
        this.watchManager = watchManager;
        jda.addEventListener(this);
        var guilds = jda.getGuilds();
        LOGGER.info("Connected to {} guilds", guilds.size());
        liveFeedManager.onAllGuildsLoaded();
        watchManager.onAllGuildsLoaded(guilds);
        initialized.set(true);
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        if (initialized.get()) LOGGER.info("Joined guild: {}", event.getGuild().getName());
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        if (initialized.get()) LOGGER.info("Left guild: ({}) {}", event.getGuild().getId(), event.getGuild().getName());
        liveFeedManager.disableFeedsInGuild(event.getGuild().getId());
        watchManager.removeWatchesInGuild(event.getGuild().getId());
    }
}
