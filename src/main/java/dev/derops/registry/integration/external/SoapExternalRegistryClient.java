package dev.derops.registry.integration.external;

import java.util.Optional;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.core.WebServiceTemplate;

/** SOAP implementation of {@link ExternalRegistryClient} built on Spring-WS. */
public class SoapExternalRegistryClient implements ExternalRegistryClient {

    private final WebServiceTemplate template;

    public SoapExternalRegistryClient(WebServiceTemplate template) {
        this.template = template;
    }

    @Override
    public Optional<ExternalRecord> lookup(String registrationNumber) {
        try {
            Object reply = template.marshalSendAndReceive(new LookupTreatyRequest(registrationNumber));
            if (reply instanceof LookupTreatyResponse response && response.isFound()) {
                return Optional.of(new ExternalRecord(response.getExternalRef(), response.getStatus(), response.getRegisteredOn()));
            }
            return Optional.empty();
        } catch (WebServiceClientException e) {
            throw new ExternalRegistryException("External registry call failed: " + e.getMessage(), e);
        }
    }
}
