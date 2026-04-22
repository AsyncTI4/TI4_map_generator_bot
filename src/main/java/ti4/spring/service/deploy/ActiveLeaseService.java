package ti4.spring.service.deploy;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;

/**
 * Owns the shared active lease and the local process state that decides whether this instance may serve traffic,
 * handle Discord interactions, mutate game state, or run scheduled work.
 */
@Service
@RequiredArgsConstructor
public class ActiveLeaseService {

    private final ActiveLeaseRepository activeLeaseRepository;
    private final LeaseProperties leaseProperties;

    private final String instanceId = UUID.randomUUID().toString();
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean leaseParticipationEnabled = new AtomicBoolean(true);

    @PostConstruct
    void acquireOnStartup() {
        boolean acquired = tryAcquireLease();
        setActive(acquired);
        if (acquired) {
            BotLogger.info("Acquired active lease for instance " + instanceId);
        } else {
            BotLogger.warning("Did not acquire active lease on startup for instance " + instanceId);
        }
    }

    /** Attempts to acquire or steal an expired lease for the current process. */
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
        setActive(true);
        setDraining(false);
        GameManager.warmup();
        return true;
    }

    /** Extends the lease while this process remains active. */
    @Transactional
    public void renewLease() {
        if (!isActive()) {
            return;
        }
        activeLeaseRepository.save(buildLease(Instant.now()));
    }

    /** Releases the lease if it is currently owned by this process. */
    @Transactional
    public void releaseLease() {
        Optional<ActiveLeaseEntity> existing = activeLeaseRepository.findById(leaseProperties.getLeaseKey());
        if (existing.isPresent() && instanceId.equals(existing.get().getInstanceId())) {
            activeLeaseRepository.delete(existing.get());
        }
        setActive(false);
    }

    /** Returns whether the shared lease row is still owned by this process. */
    public boolean stillOwnsLease() {
        return activeLeaseRepository
                .findById(leaseProperties.getLeaseKey())
                .map(lease -> instanceId.equals(lease.getInstanceId()))
                .orElse(false);
    }

    /** Returns whether this process may perform persistent game mutations right now. */
    public boolean mayMutate() {
        return isActive() && stillOwnsLease();
    }

    /** Returns whether this process is the currently active instance. */
    public boolean isActive() {
        return active.get();
    }

    /** Marks this process active or inactive, clearing drain state when deactivating. */
    public void setActive(boolean active) {
        this.active.set(active);
        if (!active) {
            draining.set(false);
        }
    }

    /** Returns whether this process is draining and refusing new work. */
    public boolean isDraining() {
        return draining.get();
    }

    /** Marks this process as draining or not draining. */
    public void setDraining(boolean draining) {
        this.draining.set(draining);
    }

    /** Returns whether this process should handle Discord interaction ingress. */
    public boolean shouldHandleDiscordInteraction() {
        return isActive() && !isDraining();
    }

    /** Returns whether startup has completed for this process. */
    public boolean isReady() {
        return ready.get();
    }

    /** Marks this process ready or not ready for normal traffic. */
    public void setReady(boolean ready) {
        this.ready.set(ready);
    }

    /** Returns whether this process should currently serve normal HTTP traffic. */
    public boolean shouldServeTraffic() {
        return isReady() && mayMutate() && !isDraining();
    }

    /** Initiates drain and closes the Spring context on a background thread. */
    public boolean requestDrain() {
        if (!isActive() || isDraining()) {
            return false;
        }

        leaseParticipationEnabled.set(false);
        setDraining(true);
        setReady(false);
        BotLogger.info("Drain requested for active instance " + instanceId);
        Thread.ofPlatform().name("bot-drain-shutdown").start(SpringContext::closeApplicationContext);
        return true;
    }

    /** Returns a log prefix for non-serving process states, or an empty string for the active serving instance. */
    private String currentProcessLogPrefix() {
        if (shouldServeTraffic()) {
            return "";
        }
        if (isDraining()) {
            return "[DRAINING " + shortInstanceId() + "] ";
        }
        if (!isActive()) {
            return "[STANDBY " + shortInstanceId() + "] ";
        }
        return "[WARMING " + shortInstanceId() + "] ";
    }

    @Scheduled(fixedDelayString = "#{@leaseProperties.heartbeatIntervalMillis}")
    void maintainLease() {
        if (isDraining()) {
            return;
        }

        if (isActive()) {
            if (stillOwnsLease()) {
                renewLease();
            } else {
                BotLogger.warning("Active instance lost lease ownership: " + instanceId);
                setReady(false);
                setActive(false);
            }
            return;
        }

        if (!leaseParticipationEnabled.get()) {
            return;
        }

        if (tryAcquireLease()) {
            setReady(true);
            BotLogger.info("Inactive instance acquired active lease: " + instanceId);
        }
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

    /** Returns current-process readiness, defaulting to false before Spring has initialized. */
    public static boolean isCurrentProcessReady() {
        try {
            return SpringContext.getBean(ActiveLeaseService.class).isReady();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** Updates current-process readiness if Spring has initialized. */
    public static void setCurrentProcessReady(boolean ready) {
        try {
            SpringContext.getBean(ActiveLeaseService.class).setReady(ready);
        } catch (IllegalStateException e) {
            // Spring not initialized yet; ignore.
        }
    }

    /** Returns whether the current process should handle a Discord interaction. */
    public static boolean shouldHandleCurrentProcessInteraction() {
        try {
            return SpringContext.getBean(ActiveLeaseService.class).shouldHandleDiscordInteraction();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** Returns whether the current process should serve normal HTTP traffic. */
    public static boolean shouldCurrentProcessServeTraffic() {
        try {
            return SpringContext.getBean(ActiveLeaseService.class).shouldServeTraffic();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** Returns whether the current process should run active-instance scheduled work. */
    public static boolean shouldCurrentProcessRunScheduledWork() {
        try {
            ActiveLeaseService activeLeaseService = SpringContext.getBean(ActiveLeaseService.class);
            return activeLeaseService.mayMutate() && !activeLeaseService.isDraining();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** Returns a log prefix for non-serving process states, defaulting to startup when Spring is unavailable. */
    public static String getCurrentProcessLogPrefix() {
        try {
            return SpringContext.getBean(ActiveLeaseService.class).currentProcessLogPrefix();
        } catch (IllegalStateException e) {
            return "[STARTUP] ";
        }
    }

    private String shortInstanceId() {
        return instanceId.substring(0, 8);
    }
}
