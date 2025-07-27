package ti4.spring.service.auth;

import java.lang.reflect.Field;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import ti4.executors.CircuitBreaker;
import ti4.spring.exception.ServiceUnavailableException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

class CircuitBreakerFilterTest {
    private final CircuitBreakerFilter filter = new CircuitBreakerFilter();

    @AfterEach
    void reset() throws Exception {
        setCircuitOpen(false);
    }

    @Test
    void throwsWhenOpen() throws Exception {
        setCircuitOpen(true);
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
        FilterChain chain = Mockito.mock(FilterChain.class);

        assertThatThrownBy(() -> filter.doFilterInternal(req, res, chain))
            .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void delegatesWhenClosed() throws Exception {
        setCircuitOpen(false);
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);
        verify(chain).doFilter(req, res);
    }

    private static void setCircuitOpen(boolean open) throws Exception {
        Field f = CircuitBreaker.class.getDeclaredField("open");
        f.setAccessible(true);
        f.set(null, open);
    }
}
