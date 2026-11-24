package com.dcpost.customer.integration.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.*;
import static com.dcpost.customer.integration.common.IntegrationException.Kind.*;

/** Transport for the provisional development protocol; replace wire mapping when contracts are agreed. */
public final class DevelopmentHttpTransport {
    private final RestClient client;

    public DevelopmentHttpTransport(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        var httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        var mapper = Jackson2ObjectMapperBuilder.json()
                .featuresToEnable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .messageConverters(converters -> converters.forEach(converter -> {
                    if (converter instanceof MappingJackson2HttpMessageConverter json) json.setObjectMapper(mapper);
                })).build();
    }

    public JsonNode exchange(HttpMethod method, String path, Object body, String requestKey, boolean mutation) {
        try {
            var request = client.method(method).uri(path).accept(MediaType.APPLICATION_JSON);
            if (requestKey != null) request.header("Idempotency-Key", requestKey);
            if (body != null) request.contentType(MediaType.APPLICATION_JSON).body(body);
            JsonNode result = request.retrieve().body(JsonNode.class);
            if (result == null || !result.isObject()) throw new IntegrationException(INVALID_RESPONSE, mutation);
            return result;
        } catch (RestClientResponseException error) {
            int status = error.getStatusCode().value();
            throw new IntegrationException(status == 401 || status == 403 ? UNAUTHORIZED : UNAVAILABLE, mutation);
        } catch (ResourceAccessException error) {
            Throwable cause = error;
            while (cause != null) {
                if (cause instanceof HttpConnectTimeoutException) throw new IntegrationException(CONNECT_TIMEOUT, mutation);
                if (cause instanceof HttpTimeoutException || cause instanceof java.net.SocketTimeoutException)
                    throw new IntegrationException(READ_TIMEOUT, mutation);
                cause = cause.getCause();
            }
            throw new IntegrationException(UNAVAILABLE, mutation);
        } catch (RestClientException error) {
            throw new IntegrationException(INVALID_RESPONSE, mutation);
        }
    }

    public static String requiredText(JsonNode node, String field, boolean mutation) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank())
            throw new IntegrationException(INVALID_RESPONSE, mutation);
        return value.textValue();
    }
}
