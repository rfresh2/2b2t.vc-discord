package vc.live;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import vc.api.vc.FeedRestClient;
import vc.live.dto.ChatsRecord;
import vc.live.dto.ConnectionsRecord;
import vc.live.dto.DeathsRecord;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class FeedApiManager implements DisposableBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeedApiManager.class);

    private final FeedRestClient feedRestClient;
    private final List<Consumer<ChatsRecord>> chatListeners;
    private final List<Consumer<DeathsRecord>> deathsListeners;
    private final List<Consumer<ConnectionsRecord>> connectionsListeners;
    private Disposable connectionsDisposable;
    private Disposable chatsDisposable;
    private Disposable deathsDisposable;

    public FeedApiManager(
        final FeedRestClient feedRestClient,
        @Value("${FEED_LISTENERS}") final String feedListeners
    ) {
        this.feedRestClient = feedRestClient;
        this.chatListeners = new CopyOnWriteArrayList<>();
        this.deathsListeners = new CopyOnWriteArrayList<>();
        this.connectionsListeners = new CopyOnWriteArrayList<>();
        if (Boolean.parseBoolean(feedListeners)) {
            var connectionsFlux = this.feedRestClient.getConnections();
            connectionsDisposable = connectionsFlux.subscribe(this::onConnection);
            var chatsFlux = this.feedRestClient.getChats();
            chatsDisposable = chatsFlux.subscribe(this::onChat);
            var deathsFlux = this.feedRestClient.getDeaths();
            deathsDisposable = deathsFlux.subscribe(this::onDeath);
            LOGGER.info("Feed API's listeners subscribed");
        } else {
            LOGGER.info("Feed API listeners disabled");
        }
    }

    public void addConnectionListener(Consumer<ConnectionsRecord> consumer) {
        connectionsListeners.add(consumer);
    }

    public void addChatListener(Consumer<ChatsRecord> consumer) {
        chatListeners.add(consumer);
    }

    public void addDeathsListener(Consumer<DeathsRecord> consumer) {
        deathsListeners.add(consumer);
    }

    private void onConnection(ConnectionsRecord connectionsRecord) {
        for (var consumer : connectionsListeners) {
            consumer.accept(connectionsRecord);
        }
    }

    private void onChat(ChatsRecord chatsRecord) {
        for (var consumer : chatListeners) {
            consumer.accept(chatsRecord);
        }
    }

    private void onDeath(DeathsRecord deathsRecord) {
        for (var consumer : deathsListeners) {
            consumer.accept(deathsRecord);
        }
    }

    @Override
    public void destroy() throws Exception {
        LOGGER.info("Shutting down feed listeners");
        if (connectionsDisposable != null) connectionsDisposable.dispose();
        if (chatsDisposable != null) chatsDisposable.dispose();
        if (deathsDisposable != null) deathsDisposable.dispose();
    }
}
