package ti4.spring.service.auth;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ti4.cache.CacheManager;
import ti4.spring.exception.TooManyRequestsException;

@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int CAPACITY = 20;
    private static final Duration REFILL_DURATION = Duration.ofMinutes(1);

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
        .maximumSize(10000)
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build();

    public RateLimitFilter() {
        CacheManager.registerCache("rateLimitBuckets", buckets);
    }

    private Bucket resolveBucket(String userId) {
        Bucket bucket = buckets.getIfPresent(userId);
        if (bucket == null) {
            Bandwidth limit = Bandwidth.classic(CAPACITY, Refill.greedy(CAPACITY, REFILL_DURATION));
            bucket = Bucket.builder().addLimit(limit).build();
            buckets.put(userId, bucket);
        }
        return bucket;
    }

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain) throws ServletException, IOException {
        String userId = null;
        try {
            userId = RequestContext.getUserId();
        } catch (Exception ignored) {
        }

        if (userId != null) {
            Bucket bucket = resolveBucket(userId);
            if (!bucket.tryConsume(1)) {
                throw new TooManyRequestsException();
            }
        }

        filterChain.doFilter(request, response);
    }
}
