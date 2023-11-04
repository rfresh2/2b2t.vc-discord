package vc.live;

import org.springframework.stereotype.Component;

@Component
public class LiveFeedManager {
    private final LiveChat liveChatFeed;
    private final LiveConnections liveConnections;

    public LiveFeedManager(final LiveChat liveChatFeed, final LiveConnections liveConnections) {
        this.liveChatFeed = liveChatFeed;
        this.liveConnections = liveConnections;
    }

    public void onAllGuildsLoaded() {
        liveChatFeed.onAllGuildsLoaded();
        liveConnections.onAllGuildsLoaded();
    }
}
