package dev.derops.registry.integration.external;

import dev.derops.registry.config.AppProperties;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;

@Configuration
public class ExternalRegistryConfig {

    public static Jaxb2Marshaller marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(LookupTreatyRequest.class, LookupTreatyResponse.class);
        try {
            marshaller.afterPropertiesSet();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialise JAXB marshaller", e);
        }
        return marshaller;
    }

    @Bean
    @ConditionalOnProperty(name = "app.external-registry.mode", havingValue = "soap")
    ExternalRegistryClient soapExternalRegistryClient(AppProperties props) {
        Jaxb2Marshaller marshaller = marshaller();
        WebServiceTemplate template = new WebServiceTemplate(marshaller);
        template.setDefaultUri(props.externalRegistry().url());
        return new SoapExternalRegistryClient(template);
    }

    /** Stand-in until the real service is reachable: numbers starting with "INT-" are "registered". */
    @Bean
    @ConditionalOnProperty(name = "app.external-registry.mode", havingValue = "mock", matchIfMissing = true)
    ExternalRegistryClient mockExternalRegistryClient() {
        return registrationNumber -> registrationNumber != null && registrationNumber.startsWith("INT-")
                ? Optional.of(new ExternalRegistryClient.ExternalRecord("EXT-" + registrationNumber, "REGISTERED", null))
                : Optional.empty();
    }
}
