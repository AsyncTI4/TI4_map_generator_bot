package ti4.spring.api.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.spring.context.RequestContext;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/dashboard/settings")
public class DashboardSettingsController {

    private final DashboardSettingsService dashboardSettingsService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public DashboardSettingsResponse get() {
        return dashboardSettingsService.getSettings(RequestContext.getUserId());
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DashboardSettingsResponse> update(@RequestBody DashboardSettingsUpdateRequest request) {
        try {
            return ResponseEntity.ok(dashboardSettingsService.updateSettings(RequestContext.getUserId(), request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
