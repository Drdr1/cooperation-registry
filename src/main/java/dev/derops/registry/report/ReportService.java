package dev.derops.registry.report;

import dev.derops.registry.agreement.Agreement;
import dev.derops.registry.agreement.AgreementDtos.AgreementFilter;
import dev.derops.registry.agreement.AgreementRepository;
import dev.derops.registry.agreement.AgreementService;
import dev.derops.registry.agreement.ImplementationItem;
import dev.derops.registry.agreement.ImplementationItemRepository;
import dev.derops.registry.common.Messages;
import dev.derops.registry.reference.ReferenceItem;
import dev.derops.registry.user.AppUser;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds report tables in the request language; rendering is format-specific. */
@Service
public class ReportService {

    private final AgreementRepository agreements;
    private final ImplementationItemRepository items;
    private final Messages messages;
    private final Clock clock;

    public ReportService(AgreementRepository agreements, ImplementationItemRepository items,
                         Messages messages, Clock clock) {
        this.agreements = agreements;
        this.items = items;
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReportTable agreements(AgreementFilter filter) {
        Locale locale = LocaleContextHolder.getLocale();
        List<Agreement> rows = agreements.findAll(AgreementService.spec(filter), Sort.by("registrationNumber"));
        List<List<Object>> data = new ArrayList<>();
        int n = 1;
        for (Agreement a : rows) {
            data.add(Arrays.<Object>asList(
                    n++,
                    a.getRegistrationNumber(),
                    a.getTitle(),
                    name(a.getType(), locale),
                    name(a.getCountry(), locale),
                    name(a.getOrganization(), locale),
                    a.getSignedOn(),
                    a.getValidUntil(),
                    messages.enumLabel(a.getStatus()),
                    person(a.getResponsible())));
        }
        return new ReportTable(
                messages.get("report.agreements.title"),
                subtitle(data.size()),
                headers("no", "registrationNumber", "title", "type", "country", "organization",
                        "signedOn", "validUntil", "status", "responsible"),
                data);
    }

    @Transactional(readOnly = true)
    public ReportTable implementation() {
        LocalDate today = LocalDate.now(clock);
        String yes = messages.get("common.yes");
        String no = messages.get("common.no");
        List<List<Object>> data = new ArrayList<>();
        for (ImplementationItem i : items.findAllForReport()) {
            data.add(Arrays.<Object>asList(
                    i.getAgreement().getRegistrationNumber(),
                    i.getDescription(),
                    person(i.getResponsible()),
                    i.getDueDate(),
                    messages.enumLabel(i.getStatus()),
                    i.isOverdue(today) ? yes : no,
                    i.getCompletedOn(),
                    i.getProgressNote()));
        }
        return new ReportTable(
                messages.get("report.implementation.title"),
                subtitle(data.size()),
                headers("agreement", "item", "responsible", "dueDate", "status", "overdue", "completedOn", "progressNote"),
                data);
    }

    private String subtitle(int rows) {
        return messages.get("report.generated", LocalDate.now(clock).toString(), rows);
    }

    private List<String> headers(String... keys) {
        return Arrays.stream(keys).map(k -> messages.get("report.col." + k)).toList();
    }

    private static String name(ReferenceItem item, Locale locale) {
        return item == null ? "" : item.name(locale);
    }

    private static String person(AppUser user) {
        return user == null ? "" : user.getFullName();
    }
}
