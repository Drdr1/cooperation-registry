package dev.derops.registry.meeting;

import dev.derops.registry.common.PageResponse;
import dev.derops.registry.meeting.MeetingDtos.MeetingDto;
import dev.derops.registry.meeting.MeetingDtos.MeetingFilter;
import dev.derops.registry.meeting.MeetingDtos.MeetingRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meetings")
@Tag(name = "Meetings")
public class MeetingController {

    private final MeetingService service;

    public MeetingController(MeetingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MEETING_READ')")
    @Operation(summary = "Search meetings", description = "Filters combine with AND. Sort with e.g. sort=heldOn,desc.")
    public PageResponse<MeetingDto> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) MeetingStatus status,
            @RequestParam(required = false) Long countryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "heldOn", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(new MeetingFilter(q, status, countryId, from, to), pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MEETING_READ')")
    public MeetingDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MEETING_WRITE')")
    public MeetingDto create(@Valid @RequestBody MeetingRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MEETING_WRITE')")
    @Operation(summary = "Update a meeting", description = "Send the version you loaded; a stale version returns 409.")
    public MeetingDto update(@PathVariable Long id, @Valid @RequestBody MeetingRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('MEETING_WRITE')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
