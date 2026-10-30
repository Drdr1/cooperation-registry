package dev.derops.registry.auth;

/**
 * Adapter for the national single sign-on (OneID). The HTTP implementation follows
 * the OAuth2 authorization-code flow; endpoint URLs and attribute names are
 * configuration, to be filled in from the official integration specification.
 */
public interface OneIdClient {

    String authorizationUrl(String state);

    /** Exchanges an authorization code for the identity of the person who logged in. */
    OneIdProfile exchange(String code);

    record OneIdProfile(String subject, String fullName) {}

    class OneIdException extends RuntimeException {
        public OneIdException(String message) {
            super(message);
        }

        public OneIdException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
