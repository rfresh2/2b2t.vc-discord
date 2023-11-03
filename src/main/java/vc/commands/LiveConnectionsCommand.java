package vc.commands;

import org.springframework.stereotype.Component;
import vc.live.LiveConnections;

@Component
public class LiveConnectionsCommand extends LiveFeedCommand {
    public LiveConnectionsCommand(final LiveConnections liveConnections) {
        super(liveConnections);
    }

    @Override
    public String getName() {
        return "liveconnections";
    }

    @Override
    public String feedName() {
        return "Live Connections";
    }
}
