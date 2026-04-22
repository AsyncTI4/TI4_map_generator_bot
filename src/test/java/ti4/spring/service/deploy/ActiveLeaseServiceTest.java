package ti4.spring.service.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.game.persistence.GameManager;

class ActiveLeaseServiceTest {

    @Test
    void tryAcquireLeaseStartsManagedGameWarmupAfterAcquiringLease() throws Exception {
        ActiveLeaseRepository repository = mock(ActiveLeaseRepository.class);
        LeaseProperties leaseProperties = leaseProperties();
        when(repository.findById("discord-bot")).thenReturn(Optional.empty());

        ActiveLeaseService service = new ActiveLeaseService(repository, leaseProperties);
        try (MockedStatic<GameManager> gameManager = Mockito.mockStatic(GameManager.class)) {
            boolean acquired = service.tryAcquireLease();

            assertThat(acquired).isTrue();
            verify(repository).save(any(ActiveLeaseEntity.class));
            gameManager.verify(GameManager::warmup, times(1));
        }
    }

    @Test
    void tryAcquireLeaseDoesNotStartWarmupWhenAnotherInstanceStillOwnsLease() throws Exception {
        ActiveLeaseRepository repository = mock(ActiveLeaseRepository.class);
        LeaseProperties leaseProperties = leaseProperties();
        ActiveLeaseEntity existingLease = new ActiveLeaseEntity();
        existingLease.setLeaseKey("discord-bot");
        existingLease.setInstanceId("other-instance");
        existingLease.setLeaseExpiresAt(Instant.now().plusSeconds(60));
        when(repository.findById("discord-bot")).thenReturn(Optional.of(existingLease));

        ActiveLeaseService service = new ActiveLeaseService(repository, leaseProperties);
        try (MockedStatic<GameManager> gameManager = Mockito.mockStatic(GameManager.class)) {
            boolean acquired = service.tryAcquireLease();

            assertThat(acquired).isFalse();
            verify(repository, times(0)).save(any(ActiveLeaseEntity.class));
            gameManager.verifyNoInteractions();
        }
    }

    private LeaseProperties leaseProperties() throws Exception {
        LeaseProperties leaseProperties = new LeaseProperties();
        setField(leaseProperties, "leaseKey", "discord-bot");
        setField(leaseProperties, "leaseDurationSeconds", 300L);
        setField(leaseProperties, "heartbeatIntervalSeconds", 5L);
        return leaseProperties;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
