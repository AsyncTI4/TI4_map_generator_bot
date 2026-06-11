package ti4.service.webhook;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebhookDispatchServiceTest {

    @Test
    void shouldRetryTransientStatuses() {
        assertTrue(WebhookDispatchService.shouldRetry(408));
        assertTrue(WebhookDispatchService.shouldRetry(429));
        assertTrue(WebhookDispatchService.shouldRetry(500));
        assertTrue(WebhookDispatchService.shouldRetry(502));
        assertTrue(WebhookDispatchService.shouldRetry(503));
    }

    @Test
    void shouldNotRetrySuccessOrNonTransientClientErrors() {
        assertFalse(WebhookDispatchService.shouldRetry(200));
        assertFalse(WebhookDispatchService.shouldRetry(201));
        assertFalse(WebhookDispatchService.shouldRetry(400));
        assertFalse(WebhookDispatchService.shouldRetry(401));
        assertFalse(WebhookDispatchService.shouldRetry(404));
    }
}
