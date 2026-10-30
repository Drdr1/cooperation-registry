package dev.derops.registry.integration.soap;

import dev.derops.registry.agreement.Agreement;
import dev.derops.registry.agreement.AgreementRepository;
import dev.derops.registry.agreement.ImplementationItemRepository;
import dev.derops.registry.agreement.ItemStatus;
import dev.derops.registry.audit.AuditAction;
import dev.derops.registry.audit.AuditService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.springframework.ws.soap.server.endpoint.annotation.FaultCode;
import org.springframework.ws.soap.server.endpoint.annotation.SoapFault;

/** SOAP service for other government systems: status of an agreement by registration number. */
@Endpoint
public class AgreementStatusEndpoint {

    private final AgreementRepository agreements;
    private final ImplementationItemRepository items;
    private final AuditService audit;
    private final Clock clock;

    public AgreementStatusEndpoint(AgreementRepository agreements, ImplementationItemRepository items,
                                   AuditService audit, Clock clock) {
        this.agreements = agreements;
        this.items = items;
        this.audit = audit;
        this.clock = clock;
    }

    @PayloadRoot(namespace = SoapNs.AGREEMENTS, localPart = "GetAgreementStatusRequest")
    @ResponsePayload
    public GetAgreementStatusResponse getStatus(@RequestPayload GetAgreementStatusRequest request) {
        String number = request.getRegistrationNumber() == null ? "" : request.getRegistrationNumber().trim();
        Agreement a = agreements.findByRegistrationNumber(number)
                .orElseThrow(() -> new AgreementNotFoundFault("Agreement not found: " + number));

        LocalDate today = LocalDate.now(clock);
        GetAgreementStatusResponse response = new GetAgreementStatusResponse();
        response.setRegistrationNumber(a.getRegistrationNumber());
        response.setTitle(a.getTitle());
        response.setStatus(a.getStatus().name());
        response.setSignedOn(a.getSignedOn() == null ? null : a.getSignedOn().toString());
        response.setValidUntil(a.getValidUntil() == null ? null : a.getValidUntil().toString());
        response.setOpenItems((int) items.countByAgreementIdAndStatusNot(a.getId(), ItemStatus.DONE));
        response.setOverdueItems((int) items.countByAgreementIdAndStatusNotAndDueDateBefore(a.getId(), ItemStatus.DONE, today));
        audit.record(AuditAction.EXPORT, "AGREEMENT", a.getId(), Map.of("channel", "soap", "operation", "GetAgreementStatus"));
        return response;
    }

    @SoapFault(faultCode = FaultCode.CLIENT)
    public static class AgreementNotFoundFault extends RuntimeException {
        public AgreementNotFoundFault(String message) {
            super(message);
        }
    }
}
