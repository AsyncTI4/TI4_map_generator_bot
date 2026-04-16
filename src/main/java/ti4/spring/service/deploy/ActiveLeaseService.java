package ti4.spring.service.deploy;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;

@Service
@RequiredArgsConstructor
public class ActiveLeaseService {

    private final ActiveLeaseRepository activeLeaseRepository;
    private final LeaseProperties leaseProperties;
    private final InstanceActivityService instanceActivityService;

    private final String instanceId = UUID.randomUUID().toString();
    private final AtomicBoolean ready = new AtomicBoolean(false);

    @PostConstruct
    void acquireOnStartup() {
        boolean acquired = tryAcquireLease();
        instanceActivityService.setActive(acquired);
        if (acquired) {
            BotLogger.info("Acquired active lease for instance " + instanceId);
        } else {
            BotLogger.warning("Did not acquire active lease on startup for instance " + instanceId);
        }
    }

    @PreDestroy
    void releaseOnShutdown() {
        releaseLease();
    }

    @Transactional
    public boolean tryAcquireLease() {
        Instant now = Instant.now();
        Optional<ActiveLeaseEntity> existing = activeLeaseRepository.findById(leaseProperties.getLeaseKey());
        if (existing.isPresent()) {
            ActiveLeaseEntity lease = existing.get();
            if (!instanceId.equals(lease.getInstanceId()) && !isExpired(lease, now)) {
                return false;
            }
        }

        activeLeaseRepository.save(buildLease(now));
        instanceActivityService.setActive(true);
        return true;
    }

    @Transactional
    public void renewLease() {
        if (!instanceActivityService.isActive()) {
            return;
        }
        activeLeaseRepository.save(buildLease(Instant.now()));
    }

    @Transactional
    public void releaseLease() {
        Optional<ActiveLeaseEntity> existing = activeLeaseRepository.findById(leaseProperties.getLeaseKey());
        if (existing.isPresent() && instanceId.equals(existing.get().getInstanceId())) {
            activeLeaseRepository.delete(existing.get());
        }
        instanceActivityService.setActive(false);
    }

    public boolean stillOwnsLease() {
        return activeLeaseRepository
                .findById(leaseProperties.getLeaseKey())
                .map(lease -> instanceId.equals(lease.getInstanceId()))
                .orElse(false);
    }

    public boolean mayMutate() {
        return instanceActivityService.isActive() && stillOwnsLease();
    }

    public boolean isReady() {
        return ready.get();
    }

    public void setReady(boolean ready) {
        this.ready.set(ready);
    }

    public String currentInstanceId() {
        return instanceId;
    }

    private ActiveLeaseEntity buildLease(Instant now) {
        ActiveLeaseEntity lease = new ActiveLeaseEntity();
        lease.setLeaseKey(leaseProperties.getLeaseKey());
        lease.setInstanceId(instanceId);
        lease.setHeartbeatAt(now);
        lease.setLeaseExpiresAt(now.plusSeconds(leaseProperties.getLeaseDurationSeconds()));
        return lease;
    }

    private boolean isExpired(ActiveLeaseEntity lease, Instant now) {
        return lease.getLeaseExpiresAt() == null || lease.getLeaseExpiresAt().isBefore(now);
    }

    public static boolean isCurrentProcessReady() {
        try {
            return SpringContext.getBean(ActiveLeaseService.class).isReady();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static void setCurrentProcessReady(boolean ready) {
        try {
            SpringContext.getBean(ActiveLeaseService.class).setReady(ready);
        } catch (IllegalStateException e) {
            // Spring not initialized yet; ignore.
        }
    }
}
