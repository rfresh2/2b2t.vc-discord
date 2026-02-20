package vc.api;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;
import tools.jackson.databind.ObjectMapper;
import vc.live.dto.ChatsRecord;
import vc.live.dto.ConnectionsRecord;
import vc.live.dto.DeathsRecord;

import java.time.Duration;

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

    private WebClient webClient() {
        return WebClient.builder()
            .baseUrl("https://api.2b2t.vc/feed")
            .defaultHeader(HttpHeaders.USER_AGENT, "2b2t.vc-discord")
            .defaultHeader("X-API-Key", apiKey)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
            .build();
    }

    private <T> Flux<T> buildFlux(String uri, Class<T> clazz) {
        return webClient()
            .get()
            .uri(uri)
            .retrieve()
            .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
            .mapNotNull(ServerSentEvent::data)
            .timeout(Duration.ofMinutes(5))
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
