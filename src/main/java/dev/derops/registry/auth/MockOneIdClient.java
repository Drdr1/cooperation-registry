package dev.derops.registry.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Development stand-in for OneID: any code of the form {@code mock:<subject>}
 * logs in as the user linked to that subject. Lets the frontend build the full
 * SSO flow before real OneID credentials exist.
 */
@Component
@ConditionalOnProperty(name = "app.oneid.mode", havingValue = "mock", matchIfMissing = true)
public class MockOneIdClient implements OneIdClient {

    static final String PREFIX = "mock:";

    @Override
    public String authorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString("mock://oneid/authorize")
                .queryParam("state", state)
                .queryParam("hint", "call /api/auth/oneid/callback with code=mock:<subject>")
                .encode()
                .build()
                .toUriString();
    }

    @Override
    public OneIdProfile exchange(String code) {
        if (code == null || !code.startsWith(PREFIX) || code.length() == PREFIX.length()) {
            throw new OneIdException("Invalid mock authorization code");
        }
        String subject = code.substring(PREFIX.length());
        return new OneIdProfile(subject, "Mock OneID user " + subject);
    }
}
