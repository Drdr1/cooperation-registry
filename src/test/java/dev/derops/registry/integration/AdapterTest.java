package dev.derops.registry.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.derops.registry.auth.HttpOneIdClient;
import dev.derops.registry.auth.OneIdClient;
import dev.derops.registry.config.AppProperties;
import dev.derops.registry.integration.external.ExternalRegistryClient;
import dev.derops.registry.integration.external.ExternalRegistryConfig;
import dev.derops.registry.integration.external.SoapExternalRegistryClient;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.ws.test.client.RequestMatchers;
import org.springframework.ws.test.client.ResponseCreators;
import org.springframework.xml.transform.StringSource;

/** The real (non-mock) adapters, against stubbed remote services. */
class AdapterTest {

    private static AppProperties props() {
        AppProperties.OneId oneId = new AppProperties.OneId("http",
                "https://id.test/authorize", "https://id.test/token", "https://id.test/userinfo",
                "registry-client", "s3cret", "https://registry.test/callback", "openid", "pin", "full_name");
        return new AppProperties("uz", null, null, null, oneId, null, null, null);
    }

    @Test
    void oneIdAuthorizationUrlCarriesClientAndState() {
        HttpOneIdClient client = new HttpOneIdClient(RestClient.builder(), props());
        String url = client.authorizationUrl("abc123");
        assertThat(url).startsWith("https://id.test/authorize?")
                .contains("response_type=code")
                .contains("client_id=registry-client")
                .contains("state=abc123");
    }

    @Test
    void oneIdCodeExchangeThenUserinfo() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://id.test/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of("grant_type", "authorization_code", "code", "the-code",
                        "client_id", "registry-client")))
                .andRespond(withSuccess("{\"access_token\":\"at-1\",\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://id.test/userinfo"))
                .andExpect(header("Authorization", "Bearer at-1"))
                .andRespond(withSuccess("{\"pin\":\"30101850000001\",\"full_name\":\"Test Person\"}", MediaType.APPLICATION_JSON));

        OneIdClient.OneIdProfile profile = new HttpOneIdClient(builder, props()).exchange("the-code");

        assertThat(profile.subject()).isEqualTo("30101850000001");
        assertThat(profile.fullName()).isEqualTo("Test Person");
        server.verify();
    }

    @Test
    void oneIdFailureBecomesOneIdException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://id.test/token")).andRespond(withServerError());
        HttpOneIdClient client = new HttpOneIdClient(builder, props());
        assertThatThrownBy(() -> client.exchange("x")).isInstanceOf(OneIdClient.OneIdException.class);
    }

    private static WebServiceTemplate template() {
        WebServiceTemplate template = new WebServiceTemplate(ExternalRegistryConfig.marshaller());
        template.setDefaultUri("http://registry.test/ws");
        return template;
    }

    @Test
    void externalRegistryLookupOverSoap() {
        WebServiceTemplate template = template();
        MockWebServiceServer server = MockWebServiceServer.createServer(template);
        server.expect(RequestMatchers.payload(new StringSource(
                        "<LookupTreatyRequest xmlns=\"urn:example:treaty-registry:v1\">"
                                + "<registrationNumber>INT-2026-001</registrationNumber></LookupTreatyRequest>")))
                .andRespond(ResponseCreators.withPayload(new StringSource(
                        "<LookupTreatyResponse xmlns=\"urn:example:treaty-registry:v1\">"
                                + "<found>true</found><externalRef>TR-77</externalRef><status>REGISTERED</status>"
                                + "</LookupTreatyResponse>")));

        var record = new SoapExternalRegistryClient(template).lookup("INT-2026-001");

        assertThat(record).isPresent();
        assertThat(record.get().externalRef()).isEqualTo("TR-77");
        assertThat(record.get().status()).isEqualTo("REGISTERED");
        server.verify();
    }

    @Test
    void externalRegistryNotFoundAndFaults() {
        WebServiceTemplate template = template();
        MockWebServiceServer server = MockWebServiceServer.createServer(template);
        server.expect(RequestMatchers.anything())
                .andRespond(ResponseCreators.withPayload(new StringSource(
                        "<LookupTreatyResponse xmlns=\"urn:example:treaty-registry:v1\"><found>false</found></LookupTreatyResponse>")));
        server.expect(RequestMatchers.anything())
                .andRespond(ResponseCreators.withServerOrReceiverFault("registry down", Locale.ENGLISH));

        SoapExternalRegistryClient client = new SoapExternalRegistryClient(template);
        assertThat(client.lookup("X-1")).isEmpty();
        assertThatThrownBy(() -> client.lookup("X-2")).isInstanceOf(ExternalRegistryClient.ExternalRegistryException.class);
    }
}
