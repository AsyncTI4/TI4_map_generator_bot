package ti4.spring.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorLoggingFilterTest {

    @Test
    void skipsStandbyReadyProbe503DuringLeaseHandoff() {
        String body =
                "{\"ready\":false,\"startupComplete\":true,\"active\":false,\"draining\":false,\"leaseOwned\":false}";

        assertThat(ErrorLoggingFilter.shouldSkipReadyProbeHandoffReport("/api/public/ready", 503, body))
                .isTrue();
    }

    @Test
    void skipsDrainingReadyProbe503DuringLeaseHandoff() {
        String body =
                "{\"ready\":false,\"startupComplete\":false,\"active\":true,\"draining\":true,\"leaseOwned\":true}";

        assertThat(ErrorLoggingFilter.shouldSkipReadyProbeHandoffReport("/api/public/ready", 503, body))
                .isTrue();
    }

    @Test
    void keepsUnexpectedReadyProbeStatusesReportable() {
        String body =
                "{\"ready\":false,\"startupComplete\":true,\"active\":true,\"draining\":false,\"leaseOwned\":true}";

        assertThat(ErrorLoggingFilter.shouldSkipReadyProbeHandoffReport("/api/public/ready", 503, body))
                .isFalse();
    }

    @Test
    void keepsNonReadyEndpointsReportable() {
        String body =
                "{\"ready\":false,\"startupComplete\":true,\"active\":false,\"draining\":false,\"leaseOwned\":false}";

        assertThat(ErrorLoggingFilter.shouldSkipReadyProbeHandoffReport("/api/public/ping", 503, body))
                .isFalse();
    }
}
