package ti4.spring.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import ti4.spring.service.auth.GameLockAndRequestContextInterceptor;

@RequiredArgsConstructor
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    private final GameLockAndRequestContextInterceptor gameLockAndRequestContextInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(gameLockAndRequestContextInterceptor).addPathPatterns("/api/game/{gameName}/**");
    }
}