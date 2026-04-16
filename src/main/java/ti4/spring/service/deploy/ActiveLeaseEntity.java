package ti4.spring.service.deploy;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "active_lease")
@Getter
@Setter
public class ActiveLeaseEntity {

    @Id
    private String leaseKey;

    private String instanceId;
    private Instant leaseExpiresAt;
    private Instant heartbeatAt;
}
