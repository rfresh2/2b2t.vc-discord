package vc.commands;

import org.springframework.stereotype.Component;
import vc.live.LiveChat;

@Component
public class LiveChatCommand extends LiveFeedCommand {
    public LiveChatCommand(final LiveChat liveChat) {
        super(liveChat);
    }

    @Override
    public String getName() {
        return "livechat";
    }

    @Override
    public String feedName() {
        return "Live Chat";
    }
}
