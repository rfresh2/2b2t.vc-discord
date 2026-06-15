package vc.api.laby;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vc.api.laby.model.LabyProfileSearch;
import vc.api.laby.model.LabyProfileSearchResponse;

@Component
public class LabyRestClient {
    private final RestClient restClient;

    public LabyRestClient(ClientHttpRequestFactory requestFactory) {
        this.restClient = RestClient.builder()
            .baseUrl("https://laby.net/api/v3")
            .requestFactory(requestFactory)
            .defaultHeader("User-Agent", "2b2t.vc-discord")
            .build();
    }

    public LabyProfileSearch searchProfiles(String username) {
        var response = restClient.get()
            .uri("/search/profiles/{username}", username)
            .retrieve()
            .body(LabyProfileSearchResponse.class);
        return new LabyProfileSearch(username, response);
    }
}
