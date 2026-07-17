package ti4.spring.service.deploy;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class LeaseProperties {

    @Value("${ti4.deploy.lease-key:discord-bot}")
    private String leaseKey;

    @Value("${ti4.deploy.lease-duration-seconds:3600}")
    private long leaseDurationSeconds;

    @Value("${ti4.deploy.heartbeat-interval-seconds:5}")
    private long heartbeatIntervalSeconds;

    public long getHeartbeatIntervalMillis() {
        return heartbeatIntervalSeconds * 1000;
    }
}
