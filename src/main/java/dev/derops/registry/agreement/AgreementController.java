package dev.derops.registry.agreement;

import dev.derops.registry.agreement.AgreementDtos.AgreementDetail;
import dev.derops.registry.agreement.AgreementDtos.AgreementDto;
import dev.derops.registry.agreement.AgreementDtos.AgreementFilter;
import dev.derops.registry.agreement.AgreementDtos.AgreementRequest;
import dev.derops.registry.agreement.AgreementDtos.ExternalCheckResult;
import dev.derops.registry.agreement.AgreementDtos.ItemDto;
import dev.derops.registry.agreement.AgreementDtos.ItemRequest;
import dev.derops.registry.agreement.AgreementDtos.ProgressRequest;
import dev.derops.registry.agreement.AgreementDtos.StatusChangeRequest;
import dev.derops.registry.auth.CurrentUser;
import dev.derops.registry.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Agreements and implementation")
public class AgreementController {

    private final AgreementService agreements;
    private final ImplementationService items;

    public AgreementController(AgreementService agreements, ImplementationService items) {
        this.agreements = agreements;
        this.items = items;
    }

    @GetMapping("/api/agreements")
    @PreAuthorize("hasAuthority('AGREEMENT_READ')")
    @Operation(summary = "Search agreements", description = "q matches title or registration number.")
    public PageResponse<AgreementDto> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AgreementStatus status,
            @RequestParam(required = false) Long countryId,
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) Long responsibleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate signedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate signedTo,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return agreements.list(new AgreementFilter(q, status, countryId, typeId, responsibleId, signedFrom, signedTo), pageable);
    }

    @GetMapping("/api/agreements/{id}")
    @PreAuthorize("hasAuthority('AGREEMENT_READ')")
    @Operation(summary = "Agreement with its implementation items and status history")
    public AgreementDetail get(@PathVariable Long id) {
        return agreements.get(id);
    }

    @PostMapping("/api/agreements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('AGREEMENT_WRITE')")
    public AgreementDto create(@Valid @RequestBody AgreementRequest request) {
        return agreements.create(request);
    }

    @PutMapping("/api/agreements/{id}")
    @PreAuthorize("hasAuthority('AGREEMENT_WRITE')")
    public AgreementDto update(@PathVariable Long id, @Valid @RequestBody AgreementRequest request) {
        return agreements.update(id, request);
    }

    @PostMapping("/api/agreements/{id}/status")
    @PreAuthorize("hasAuthority('AGREEMENT_WRITE')")
    @Operation(summary = "Change status",
            description = "Allowed moves are listed in nextStatuses. Moving to SIGNED needs AGREEMENT_APPROVE; TERMINATED needs a comment.")
    public AgreementDto changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request) {
        return agreements.changeStatus(id, request);
    }

    @PostMapping("/api/agreements/{id}/external-check")
    @PreAuthorize("hasAuthority('AGREEMENT_WRITE')")
    @Operation(summary = "Verify the agreement against the external government registry")
    public ExternalCheckResult externalCheck(@PathVariable Long id) {
        return agreements.verifyExternal(id);
    }

    @PostMapping("/api/agreements/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('AGREEMENT_WRITE')")
    @Operation(summary = "Add an implementation item; the responsible person is notified")
    public ItemDto addItem(@PathVariable Long id, @Valid @RequestBody ItemRequest request) {
        return items.add(id, request);
    }

    @PutMapping("/api/items/{itemId}")
    @PreAuthorize("hasAuthority('AGREEMENT_WRITE')")
    public ItemDto updateItem(@PathVariable Long itemId, @Valid @RequestBody ItemRequest request) {
        return items.update(itemId, request);
    }

    @PostMapping("/api/items/{itemId}/progress")
    @PreAuthorize("hasAuthority('ITEM_UPDATE')")
    @Operation(summary = "Report progress", description = "Executors may only update items they are responsible for.")
    public ItemDto progress(@PathVariable Long itemId, @Valid @RequestBody ProgressRequest request) {
        return items.updateProgress(itemId, request);
    }

    @DeleteMapping("/api/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('AGREEMENT_WRITE')")
    public void deleteItem(@PathVariable Long itemId) {
        items.delete(itemId);
    }

    @GetMapping("/api/items/mine")
    @Operation(summary = "Items the current user is responsible for")
    public List<ItemDto> mine(@RequestParam(defaultValue = "true") boolean openOnly) {
        return items.listMine(CurrentUser.id(), openOnly);
    }
}
