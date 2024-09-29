package vc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import vc.openapi.handler.ApiClient;
import vc.openapi.handler.ApiException;
import vc.openapi.handler.ApiResponse;
import vc.openapi.handler.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Copying this out from openapi generator
 *
 * The problem is the default generated code always tries to parse the body as JSON
 * even if the schema is not JSON - like it is in this case
 *
 * The body for this API is raw CSV text
 */
public class VcDataDumpApi {
    private final HttpClient memberVarHttpClient;
    private final ObjectMapper memberVarObjectMapper;
    private final String memberVarBaseUri;
    private final Consumer<HttpRequest.Builder> memberVarInterceptor;
    private final Duration memberVarReadTimeout;
    private final Consumer<HttpResponse<InputStream>> memberVarResponseInterceptor;
    private final Consumer<HttpResponse<String>> memberVarAsyncResponseInterceptor;

    public VcDataDumpApi() {
        this(new ApiClient());
    }

    public VcDataDumpApi(ApiClient apiClient) {
        memberVarHttpClient = apiClient.getHttpClient();
        memberVarObjectMapper = apiClient.getObjectMapper();
        memberVarBaseUri = apiClient.getBaseUri();
        memberVarInterceptor = apiClient.getRequestInterceptor();
        memberVarReadTimeout = apiClient.getReadTimeout();
        memberVarResponseInterceptor = apiClient.getResponseInterceptor();
        memberVarAsyncResponseInterceptor = apiClient.getAsyncResponseInterceptor();
    }

    protected ApiException getApiException(String operationId, HttpResponse<InputStream> response) throws IOException {
        String body = response.body() == null ? null : new String(response.body().readAllBytes());
        String message = formatExceptionMessage(operationId, response.statusCode(), body);
        return new ApiException(response.statusCode(), message, response.headers(), body);
    }

    private String formatExceptionMessage(String operationId, int statusCode, String body) {
        if (body == null || body.isEmpty()) {
            body = "[no body]";
        }
        return operationId + " call failed with: " + statusCode + " - " + body;
    }

    /**
     *
     *
     * @param uuid  (optional)
     * @param playerName  (optional)
     * @return String
     * @throws ApiException if fails to make API call
     */
    public String getPlayerDataDump(UUID uuid, String playerName) throws ApiException {
        ApiResponse<String> localVarResponse = getPlayerDataDumpWithHttpInfo(uuid, playerName);
        return localVarResponse.getData();
    }

    /**
     *
     *
     * @param uuid  (optional)
     * @param playerName  (optional)
     * @return ApiResponse&lt;String&gt;
     * @throws ApiException if fails to make API call
     */
    public ApiResponse<String> getPlayerDataDumpWithHttpInfo(UUID uuid, String playerName) throws ApiException {
        HttpRequest.Builder localVarRequestBuilder = getPlayerDataDumpRequestBuilder(uuid, playerName);
        try {
            HttpResponse<InputStream> localVarResponse = memberVarHttpClient.send(
                localVarRequestBuilder.build(),
                HttpResponse.BodyHandlers.ofInputStream());
            if (memberVarResponseInterceptor != null) {
                memberVarResponseInterceptor.accept(localVarResponse);
            }
            if (localVarResponse.statusCode()/ 100 != 2) {
                throw getApiException("getPlayerDataDump", localVarResponse);
            }
            try (InputStream body = localVarResponse.body()) {
                return new ApiResponse<>(
                    localVarResponse.statusCode(),
                    localVarResponse.headers().map(),
                    new String(body.readAllBytes())
                );
            }
        } catch (IOException e) {
            throw new ApiException(e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(e);
        }
    }

    private HttpRequest.Builder getPlayerDataDumpRequestBuilder(UUID uuid, String playerName) throws ApiException {

        HttpRequest.Builder localVarRequestBuilder = HttpRequest.newBuilder();

        String localVarPath = "/dump/player";

        List<Pair> localVarQueryParams = new ArrayList<>();
        StringJoiner localVarQueryStringJoiner = new StringJoiner("&");
        String localVarQueryParameterBaseName;
        localVarQueryParameterBaseName = "uuid";
        localVarQueryParams.addAll(ApiClient.parameterToPairs("uuid", uuid));
        localVarQueryParameterBaseName = "playerName";
        localVarQueryParams.addAll(ApiClient.parameterToPairs("playerName", playerName));

        if (!localVarQueryParams.isEmpty() || localVarQueryStringJoiner.length() != 0) {
            StringJoiner queryJoiner = new StringJoiner("&");
            localVarQueryParams.forEach(p -> queryJoiner.add(p.getName() + '=' + p.getValue()));
            if (localVarQueryStringJoiner.length() != 0) {
                queryJoiner.add(localVarQueryStringJoiner.toString());
            }
            localVarRequestBuilder.uri(URI.create(memberVarBaseUri + localVarPath + '?' + queryJoiner.toString()));
        } else {
            localVarRequestBuilder.uri(URI.create(memberVarBaseUri + localVarPath));
        }

        localVarRequestBuilder.header("Accept", "*/*");

        localVarRequestBuilder.method("GET", HttpRequest.BodyPublishers.noBody());
        if (memberVarReadTimeout != null) {
            localVarRequestBuilder.timeout(memberVarReadTimeout);
        }
        if (memberVarInterceptor != null) {
            memberVarInterceptor.accept(localVarRequestBuilder);
        }
        return localVarRequestBuilder;
    }
}
