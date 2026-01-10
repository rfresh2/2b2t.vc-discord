package vc.api;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import tools.jackson.databind.ObjectMapper;
import vc.live.dto.ChatsRecord;
import vc.live.dto.ConnectionsRecord;
import vc.live.dto.DeathsRecord;

import java.time.Duration;
import java.util.Arrays;

@Component
public class FeedRestClient {
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(FeedRestClient.class);
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public FeedRestClient(
        final ObjectMapper objectMapper,
        @Value("${API_KEY}") final String apiKey
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    public Flux<ChatsRecord> getChats() {
        return buildFlux("/chats", ChatsRecord.class);
    }

    public Flux<DeathsRecord> getDeaths() {
        return buildFlux("/deaths", DeathsRecord.class);
    }

    public Flux<ConnectionsRecord> getConnections() {
        return buildFlux("/connections", ConnectionsRecord.class);
    }

    private HttpClient httpClient() {
        return HttpClient.create()
            .baseUrl("https://api.2b2t.vc/feed")
            .headers(h -> h
                .add(HttpHeaderNames.USER_AGENT, "2b2t.vc-discord")
                .add("X-API-Key", apiKey)
                .add(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM));
    }

    private <T> Flux<T> buildFlux(String uri, Class<T> clazz) {
        return httpClient()
            .get()
            .uri(uri)
            .responseContent()
            .asString()
            .timeout(Duration.ofMinutes(2))
            .flatMapIterable(s -> Arrays.asList(s.split("\n")))
            .filter(s -> s.startsWith("data:"))
            .map(s -> s.substring("data:".length()).trim())
            .filter(s -> !s.isBlank())
            .map(s -> objectMapper.readValue(s, clazz))
            .retryWhen(Retry.fixedDelay(Long.MAX_VALUE, Duration.ofSeconds(2))
                .doBeforeRetry(retrySignal ->
                    LOGGER.warn("Feed {} reconnecting (reason: {})", uri,
                        retrySignal.failure() != null ? retrySignal.failure().getMessage() : "Connection closed")))
            .doOnError(e -> LOGGER.error("Feed {} error", uri, e))
            .onErrorComplete()
            .repeat();
    }
}
