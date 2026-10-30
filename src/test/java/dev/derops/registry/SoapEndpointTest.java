package dev.derops.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.clientOrSenderFault;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;

/** SOAP service for external government systems, over real HTTP for the security checks. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SoapEndpointTest {

    private static final Map<String, String> NS = Map.of("a", "urn:cooperation-registry:agreements:v1");

    @Autowired
    ApplicationContext context;

    @Autowired
    TestRestTemplate http;

    private MockWebServiceClient client;

    @BeforeEach
    void setUp() {
        client = MockWebServiceClient.createClient(context);
    }

    private static StringSource request(String number) {
        return new StringSource("<a:GetAgreementStatusRequest xmlns:a=\"urn:cooperation-registry:agreements:v1\">"
                + "<a:registrationNumber>" + number + "</a:registrationNumber></a:GetAgreementStatusRequest>");
    }

    @Test
    void returnsStatusAndImplementationCounts() {
        client.sendRequest(withPayload(request("INT-2026-001")))
                .andExpect(noFault())
                .andExpect(xpath("//a:status", NS).evaluatesTo("IN_FORCE"))
                .andExpect(xpath("//a:openItems", NS).evaluatesTo(2))
                .andExpect(xpath("//a:overdueItems", NS).evaluatesTo(1));
    }

    @Test
    void unknownAgreementIsAClientFault() {
        client.sendRequest(withPayload(request("NOPE-1"))).andExpect(clientOrSenderFault());
    }

    @Test
    void wsdlIsServedOnlyToTheIntegrationAccount() {
        ResponseEntity<String> anonymous = http.getForEntity("/ws/agreements.wsdl", String.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> wrong = http.withBasicAuth("integration", "wrong").getForEntity("/ws/agreements.wsdl", String.class);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> ok = http.withBasicAuth("integration", "integration-test")
                .getForEntity("/ws/agreements.wsdl", String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).contains("GetAgreementStatusRequest").contains("AgreementsPort");
    }

    @Test
    void restApiTokensDoNotOpenTheSoapEndpoint() {
        ResponseEntity<String> response = http.getForEntity("/api/agreements", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
