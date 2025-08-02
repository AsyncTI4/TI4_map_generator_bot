package ti4.spring.service.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class ErrorLoggingFilterTest {
    private final ErrorLoggingFilter filter = new ErrorLoggingFilter();

    @Test
    void propagatesException() throws Exception {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
        FilterChain chain = Mockito.mock(FilterChain.class);
        doThrow(new RuntimeException("boom")).when(chain).doFilter(req, res);

        assertThatThrownBy(() -> filter.doFilterInternal(req, res, chain))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void delegatesNormally() throws Exception {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);
        verify(chain).doFilter(req, res);
    }
}
