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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.game.persistence.GameManager;

class ActiveLeaseServiceTest {

    @Test
    void beginLeaseParticipationRunsCallableAfter() throws Exception {
        ActiveLeaseRepository repository = mock(ActiveLeaseRepository.class);
        LeaseProperties leaseProperties = leaseProperties();
        when(repository.findById("discord-bot")).thenReturn(Optional.empty());

        ActiveLeaseTransactionService activeLeaseTransactionService = mock(ActiveLeaseTransactionService.class);
        when(activeLeaseTransactionService.tryAcquireLease(any())).thenReturn(true);

        ActiveLeaseService service = new ActiveLeaseService(
                repository, leaseProperties, activeLeaseTransactionService, mock(LeaseReleaseListener.class));
        try (MockedStatic<GameManager> gameManager = Mockito.mockStatic(GameManager.class)) {
            service.beginLeaseParticipation(() -> {
                service.setReady(true);
                GameManager.warmup();
            });

            assertThat(service.isReady()).isTrue();
            gameManager.verify(GameManager::warmup, times(1));
        }
    }

    @Test
    void releaseNotificationLetsTheStandbyTakeOverImmediatelyAndOnlyOnce() throws Exception {
        ActiveLeaseTransactionService activeLeaseTransactionService = mock(ActiveLeaseTransactionService.class);
        when(activeLeaseTransactionService.tryAcquireLease(any())).thenReturn(false, true);
        LeaseReleaseListener listener = mock(LeaseReleaseListener.class);
        ActiveLeaseService service = new ActiveLeaseService(
                mock(ActiveLeaseRepository.class), leaseProperties(), activeLeaseTransactionService, listener);
        AtomicInteger acquisitions = new AtomicInteger();

        service.beginLeaseParticipation(acquisitions::incrementAndGet);
        ArgumentCaptor<Consumer<String>> onRelease = ArgumentCaptor.captor();
        verify(listener).start(onRelease.capture());
        assertThat(service.isActive()).isFalse();

        onRelease.getValue().accept("release notification");
        onRelease.getValue().accept("release notification");

        assertThat(service.isActive()).isTrue();
        assertThat(service.isReady()).isTrue();
        assertThat(acquisitions).hasValue(1);
        verify(activeLeaseTransactionService, times(2)).tryAcquireLease(any());
    }

    @Test
    void beginLeaseParticipationDoesNotRunCallableWhenAnotherInstanceStillOwnsLease() throws Exception {
        ActiveLeaseRepository repository = mock(ActiveLeaseRepository.class);
        LeaseProperties leaseProperties = leaseProperties();
        ActiveLeaseEntity existingLease = new ActiveLeaseEntity();
        existingLease.setLeaseKey("discord-bot");
        existingLease.setInstanceId("other-instance");
        existingLease.setLeaseExpiresAt(Instant.now().plusSeconds(60));
        when(repository.findById("discord-bot")).thenReturn(Optional.of(existingLease));

        ActiveLeaseTransactionService activeLeaseTransactionService = mock(ActiveLeaseTransactionService.class);
        when(activeLeaseTransactionService.tryAcquireLease(any())).thenReturn(false);

        ActiveLeaseService service = new ActiveLeaseService(
                repository, leaseProperties, activeLeaseTransactionService, mock(LeaseReleaseListener.class));
        try (MockedStatic<GameManager> gameManager = Mockito.mockStatic(GameManager.class)) {
            service.beginLeaseParticipation(() -> {
                service.setReady(true);
                GameManager.warmup();
            });

            assertThat(service.isReady()).isFalse();
            gameManager.verifyNoInteractions();
        }
    }

    @Test
    void anInstanceThatReleasedItsLeaseNeverTakesItBackFromItsOwnNotification() throws Exception {
        ActiveLeaseTransactionService activeLeaseTransactionService = mock(ActiveLeaseTransactionService.class);
        when(activeLeaseTransactionService.tryAcquireLease(any())).thenReturn(true);
        LeaseReleaseListener listener = mock(LeaseReleaseListener.class);
        ActiveLeaseService service = new ActiveLeaseService(
                mock(ActiveLeaseRepository.class), leaseProperties(), activeLeaseTransactionService, listener);
        AtomicInteger acquisitions = new AtomicInteger();
        service.beginLeaseParticipation(acquisitions::incrementAndGet);
        ArgumentCaptor<Consumer<String>> onRelease = ArgumentCaptor.captor();
        verify(listener).start(onRelease.capture());

        service.releaseLease();
        onRelease.getValue().accept("release notification");

        assertThat(service.isActive()).isFalse();
        assertThat(service.isReady()).isFalse();
        // The pipelines still drain after the release, and their logs must read as draining, not standby.
        assertThat(service.isDraining()).isTrue();
        assertThat(acquisitions).hasValue(1);
        verify(listener).stop();
        verify(activeLeaseTransactionService, times(1)).tryAcquireLease(any());
    }

    private LeaseProperties leaseProperties() throws Exception {
        LeaseProperties leaseProperties = new LeaseProperties();
        setField(leaseProperties, "leaseKey", "discord-bot");
        setField(leaseProperties, "leaseDurationSeconds", 300L);
        setField(leaseProperties, "heartbeatIntervalSeconds", 5L);
        return leaseProperties;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
