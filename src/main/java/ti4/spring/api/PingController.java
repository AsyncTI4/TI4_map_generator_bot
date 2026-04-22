package ti4.spring.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.spring.service.deploy.ActiveLeaseService;

@RestController
@RequestMapping("/api/public/ping")
public class PingController {

    private final ActiveLeaseService activeLeaseService;

    public PingController(ActiveLeaseService activeLeaseService) {
        this.activeLeaseService = activeLeaseService;
    }

    @GetMapping
    public ResponseEntity<String> ping() {
        return ResponseEntity.status(
                        activeLeaseService.isLeaseParticipationEnabled()
                                ? HttpStatus.OK
                                : HttpStatus.SERVICE_UNAVAILABLE)
                .body(activeLeaseService.isLeaseParticipationEnabled() ? "pong" : "not takeover capable");
    }
}
