package ti4.spring.context;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public final class SpringContext implements ApplicationContextAware {

    private static volatile ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
        if (SpringContext.applicationContext != null) {
            throw new IllegalStateException("ApplicationContext already initialized");
        }
        SpringContext.applicationContext = applicationContext;
    }

    public static <T> T getBean(Class<T> type) {
        ApplicationContext context = applicationContext;
        if (context == null) {
            throw new IllegalStateException("ApplicationContext not initialized");
        }
        return context.getBean(type);
    }
}
