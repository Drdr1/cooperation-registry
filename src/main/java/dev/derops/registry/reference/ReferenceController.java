package dev.derops.registry.reference;

import dev.derops.registry.reference.ReferenceService.ReferenceDto;
import dev.derops.registry.reference.ReferenceService.ReferenceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/references/{kind}")
@Tag(name = "Reference books")
public class ReferenceController {

    private final ReferenceService service;

    public ReferenceController(ReferenceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List a reference book (countries, organizations, agreement types, meeting formats)")
    public List<ReferenceDto> list(@PathVariable ReferenceKind kind,
                                   @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(kind, includeInactive);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('REFERENCE_ADMIN')")
    public ReferenceDto create(@PathVariable ReferenceKind kind, @Valid @RequestBody ReferenceRequest request) {
        return service.create(kind, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('REFERENCE_ADMIN')")
    @Operation(summary = "Update an entry; set active=false to retire it without breaking existing records")
    public ReferenceDto update(@PathVariable ReferenceKind kind, @PathVariable Long id,
                               @Valid @RequestBody ReferenceRequest request) {
        return service.update(kind, id, request);
    }
}
