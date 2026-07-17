package ti4.spring.api.publicstatus;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.spring.service.deploy.ActiveLeaseService;

@RestController
@RequestMapping("/api/public/ready")
public class ReadyController {

    private final ActiveLeaseService activeLeaseService;

    public ReadyController(ActiveLeaseService activeLeaseService) {
        this.activeLeaseService = activeLeaseService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> ready() {
        boolean startupComplete = activeLeaseService.isReady();
        boolean active = activeLeaseService.isActive();
        boolean draining = activeLeaseService.isDraining();
        boolean leaseOwned = activeLeaseService.stillOwnsLease();
        boolean ready = startupComplete && active && !draining && leaseOwned;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ready", ready);
        payload.put("startupComplete", startupComplete);
        payload.put("active", active);
        payload.put("draining", draining);
        payload.put("leaseOwned", leaseOwned);

        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(payload);
    }
}
