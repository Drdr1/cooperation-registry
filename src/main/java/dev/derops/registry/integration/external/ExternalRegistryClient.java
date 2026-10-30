package dev.derops.registry.integration.external;

import java.util.Optional;

/**
 * Adapter for the external government registry that agreements are checked
 * against. The contract here is deliberately small; the SOAP implementation maps
 * it onto the real service once its WSDL is available.
 */
public interface ExternalRegistryClient {

    Optional<ExternalRecord> lookup(String registrationNumber);

    record ExternalRecord(String externalRef, String status, String registeredOn) {}

    class ExternalRegistryException extends RuntimeException {
        public ExternalRegistryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
