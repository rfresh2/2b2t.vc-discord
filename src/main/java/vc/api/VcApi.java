package vc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vc.openapi.vc.handler.*;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class VcApi {

    @Bean
    public HttpClient.Builder httpClientBuilder() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5));
    }

    @Bean
    public ApiClient apiClient(
        final HttpClient.Builder httpClientBuilder,
        final ObjectMapper objectMapper,
        @Value("${API_KEY}") final String apiKey
    ) {
        return new ApiClient(httpClientBuilder, objectMapper, "https://api.2b2t.vc")
            .setReadTimeout(Duration.ofSeconds(30))
            .setRequestInterceptor((builder) -> builder.headers(
                "X-API-Key", apiKey,
                "User-Agent", "2b2t.vc-discord"
            ));
    }

    @Bean
    public ChatsApi chatsApi(final ApiClient apiClient) {
        return new ChatsApi(apiClient);
    }

    @Bean
    public ConnectionsApi connectionsApi(final ApiClient apiClient) {
        return new ConnectionsApi(apiClient);
    }

    @Bean
    public VcDataDumpApi dataDumpApi(final ApiClient apiClient) {
        return new VcDataDumpApi(apiClient);
    }

    @Bean
    public DeathsApi deathsApi(final ApiClient apiClient) {
        return new DeathsApi(apiClient);
    }

    @Bean
    public NamesApi namesApi(final ApiClient apiClient) {
        return new NamesApi(apiClient);
    }

    @Bean
    public StatsApi statsApi(final ApiClient apiClient) {
        return new StatsApi(apiClient);
    }

    @Bean
    public PlaytimeApi playtimeApi(final ApiClient apiClient) {
        return new PlaytimeApi(apiClient);
    }

    @Bean
    public QueueApi queueApi(final ApiClient apiClient) {
        return new QueueApi(apiClient);
    }

    @Bean
    public SeenApi seenApi(final ApiClient apiClient) {
        return new SeenApi(apiClient);
    }

    @Bean
    public TabListApi tabListApi(final ApiClient apiClient) {
        return new TabListApi(apiClient);
    }
}
