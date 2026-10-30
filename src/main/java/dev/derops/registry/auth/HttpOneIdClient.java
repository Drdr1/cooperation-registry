package dev.derops.registry.auth;

import dev.derops.registry.config.AppProperties;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/** OAuth2 authorization-code flow against OneID: authorize redirect, token exchange, userinfo. */
@Component
@ConditionalOnProperty(name = "app.oneid.mode", havingValue = "http")
public class HttpOneIdClient implements OneIdClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient rest;
    private final AppProperties.OneId config;

    public HttpOneIdClient(RestClient.Builder builder, AppProperties props) {
        this.rest = builder.build();
        this.config = props.oneid();
    }

    @Override
    public String authorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString(config.authorizeUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", config.clientId())
                .queryParam("redirect_uri", config.redirectUri())
                .queryParam("scope", config.scope())
                .queryParam("state", state)
                .encode()
                .build()
                .toUriString();
    }

    @Override
    public OneIdProfile exchange(String code) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("code", code);
            form.add("client_id", config.clientId());
            form.add("client_secret", config.clientSecret());
            form.add("redirect_uri", config.redirectUri());

            Map<String, Object> token = rest.post()
                    .uri(config.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MAP);
            Object accessToken = token == null ? null : token.get("access_token");
            if (!(accessToken instanceof String bearer) || bearer.isBlank()) {
                throw new OneIdException("OneID token response has no access_token");
            }

            Map<String, Object> info = rest.get()
                    .uri(config.userinfoUrl())
                    .headers(h -> h.setBearerAuth(bearer))
                    .retrieve()
                    .body(MAP);
            Object subject = info == null ? null : info.get(config.subjectAttribute());
            if (subject == null || subject.toString().isBlank()) {
                throw new OneIdException("OneID userinfo has no '" + config.subjectAttribute() + "' attribute");
            }
            Object name = info.get(config.nameAttribute());
            return new OneIdProfile(subject.toString(), name == null ? null : name.toString());
        } catch (RestClientException e) {
            throw new OneIdException("OneID request failed: " + e.getMessage(), e);
        }
    }
}
