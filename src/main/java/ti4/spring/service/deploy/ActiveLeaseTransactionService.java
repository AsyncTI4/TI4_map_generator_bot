package ti4.spring.service.deploy;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActiveLeaseTransactionService {

    private final ActiveLeaseRepository activeLeaseRepository;
    private final LeaseProperties leaseProperties;

    @Transactional
    public boolean tryAcquireLease(String instanceId) {
        Instant now = Instant.now();
        String leaseKey = leaseProperties.getLeaseKey();
        insertLeaseRowIfAbsent(leaseKey);
        return activeLeaseRepository.claimLease(
                        leaseKey, instanceId, now.plusSeconds(leaseProperties.getLeaseDurationSeconds()), now, now)
                == 1;
    }

    private void insertLeaseRowIfAbsent(String leaseKey) {
        if (StringUtils.isBlank(System.getenv("SPRING_DATASOURCE_URL"))) {
            if (activeLeaseRepository.existsById(leaseKey)) {
                return;
            }
            var lease = new ActiveLeaseEntity();
            lease.setLeaseKey(leaseKey);
            lease.setInstanceId("");
            lease.setLeaseExpiresAt(Instant.EPOCH);
            lease.setHeartbeatAt(Instant.EPOCH);
            activeLeaseRepository.save(lease);
            return;
        }

        activeLeaseRepository.insertLeaseRowIfAbsent(leaseKey, "", Instant.EPOCH, Instant.EPOCH);
    }

    @Transactional
    public boolean renewLease(String instanceId) {
        Instant now = Instant.now();
        return activeLeaseRepository.updateLeaseIfOwned(
                        leaseProperties.getLeaseKey(),
                        instanceId,
                        now.plusSeconds(leaseProperties.getLeaseDurationSeconds()),
                        now)
                == 1;
    }

    @Transactional
    public void releaseLease(String instanceId) {
        Instant now = Instant.now();
        activeLeaseRepository.updateLeaseIfOwned(leaseProperties.getLeaseKey(), instanceId, now.minusSeconds(1), now);
    }
}
