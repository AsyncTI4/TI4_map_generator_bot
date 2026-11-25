package ti4.spring.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.game.persistence.GameManager;
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
        if (!activeLeaseService.isLeaseParticipationEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("not takeover capable");
        }
        if (!GameManager.isPreloadFinished()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("still preloading managed games");
        }
        return ResponseEntity.ok("pong");
    }
}
