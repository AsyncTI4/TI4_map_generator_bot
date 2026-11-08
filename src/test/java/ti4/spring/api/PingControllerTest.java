package ti4.spring.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ti4.spring.service.deploy.ActiveLeaseService;

class PingControllerTest {

    @Test
    void reportsUnhealthyUntilTheProcessJoinsTheLease() {
        ActiveLeaseService activeLeaseService = mock(ActiveLeaseService.class);
        when(activeLeaseService.isLeaseParticipationEnabled()).thenReturn(false);

        var response = new PingController(activeLeaseService).ping();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo("not takeover capable");
    }

    @Test
    void reportsHealthyOnceTheProcessJoinsTheLeaseAndNoPreloadIsPending() {
        // No preload was started in this JVM, so there is nothing to wait for.
        ActiveLeaseService activeLeaseService = mock(ActiveLeaseService.class);
        when(activeLeaseService.isLeaseParticipationEnabled()).thenReturn(true);

        var response = new PingController(activeLeaseService).ping();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("pong");
    }
}
