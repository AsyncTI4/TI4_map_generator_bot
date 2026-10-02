package ti4.spring.api.publicstatus;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.spring.service.deploy.ActiveLeaseService;

@RestController
@RequestMapping("/api/public/deploy")
public class DeployController {

    private final ActiveLeaseService activeLeaseService;

    public DeployController(ActiveLeaseService activeLeaseService) {
        this.activeLeaseService = activeLeaseService;
    }

    @PostMapping("/drain")
    public ResponseEntity<Map<String, Object>> drain(HttpServletRequest request) {
        if (!isLoopback(request.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("accepted", false, "reason", "drain endpoint is loopback-only"));
        }

        if (!activeLeaseService.requestDrain()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("accepted", false, "reason", "process is not active or is already draining"));
        }

        return ResponseEntity.accepted().body(Map.of("accepted", true));
    }

    private static boolean isLoopback(String remoteAddr) {
        return "127.0.0.1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr);
    }
}
