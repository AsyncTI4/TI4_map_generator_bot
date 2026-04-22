package ti4.spring.service.deploy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
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
    private final ActiveLeaseTransactionService activeLeaseTransactionService;

    private final String instanceId = UUID.randomUUID().toString();
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean leaseParticipationEnabled = new AtomicBoolean(false);
    private volatile Runnable onLeaseAcquired = () -> {};

    public boolean beginLeaseParticipation(Runnable onLeaseAcquired) {
        this.onLeaseAcquired = Objects.requireNonNull(onLeaseAcquired);
        leaseParticipationEnabled.set(true);

        boolean acquired = tryAcquireLease(onLeaseAcquired);
        if (acquired) {
            BotLogger.info("Acquired active lease for instance " + instanceId);
        } else {
            BotLogger.warning("Did not acquire active lease on startup for instance " + instanceId);
        }
        return acquired;
    }

    public boolean tryAcquireLease() {
        if (!activeLeaseTransactionService.tryAcquireLease(instanceId)) {
            return false;
        }

        setActive(true);
        setDraining(false);
        onLeaseAcquired.run();
        return true;
    }

    public boolean tryAcquireLease(Runnable onLeaseAcquired) {
        if (!activeLeaseTransactionService.tryAcquireLease(instanceId)) {
            return false;
        }

        setActive(true);
        setDraining(false);
        onLeaseAcquired.run();
        return true;
    }

    public void renewLease() {
        if (!isActive()) {
            return;
        }

        if (!activeLeaseTransactionService.renewLease(instanceId)) {
            BotLogger.warning("Active instance lost lease ownership: " + instanceId);
            setReady(false);
            setActive(false);
        }
    }

    public void releaseLease() {
        activeLeaseTransactionService.releaseLease(instanceId);
        setActive(false);
    }

    public boolean stillOwnsLease() {
        Instant now = Instant.now();
        return activeLeaseRepository
                .findById(leaseProperties.getLeaseKey())
                .map(lease -> instanceId.equals(lease.getInstanceId())
                        && lease.getLeaseExpiresAt() != null
                        && lease.getLeaseExpiresAt().isAfter(now))
                .orElse(false);
    }

    public boolean mayMutate() {
        return isActive() && stillOwnsLease();
    }

    public boolean isActive() {
        return active.get();
    }

    public void setActive(boolean active) {
        this.active.set(active);
        if (!active) {
            draining.set(false);
        }
    }

    public boolean isDraining() {
        return draining.get();
    }

    public void setDraining(boolean draining) {
        this.draining.set(draining);
    }

    public boolean shouldHandleDiscordInteraction() {
        return isActive() && !isDraining();
    }

    public boolean isReady() {
        return ready.get();
    }

    public void setReady(boolean ready) {
        this.ready.set(ready);
    }

    public boolean shouldServeTraffic() {
        return isReady() && mayMutate() && !isDraining();
    }

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

    public String currentInstanceId() {
        return instanceId;
    }

    public String currentProcessLogPrefix() {
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
            renewLease();
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

    public static boolean shouldHandleCurrentProcessInteraction() {
        try {
            return SpringContext.getBean(ActiveLeaseService.class).shouldHandleDiscordInteraction();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static boolean shouldCurrentProcessServeTraffic() {
        try {
            return SpringContext.getBean(ActiveLeaseService.class).shouldServeTraffic();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static boolean shouldCurrentProcessRunScheduledWork() {
        try {
            ActiveLeaseService activeLeaseService = SpringContext.getBean(ActiveLeaseService.class);
            return activeLeaseService.mayMutate() && !activeLeaseService.isDraining();
        } catch (IllegalStateException e) {
            return false;
        }
    }

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
