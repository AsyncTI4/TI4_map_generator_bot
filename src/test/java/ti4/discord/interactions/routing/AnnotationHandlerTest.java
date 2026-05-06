package ti4.discord.interactions.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.discord.interactions.listeners.context.ListenerContext;
import ti4.executors.CircuitBreaker;
import ti4.logging.BotLogger;

class AnnotationHandlerTest {

    @AfterEach
    void resetCircuitBreaker() throws Exception {
        invokeCircuitBreakerMethod("reset");
    }

    @Test
    void buildConsumerInvocationTargetExceptionIncrementsCircuitBreaker() throws Exception {
        Consumer<ListenerContext> consumer = buildConsumer("failingHandler", context -> List.of());
        ListenerContext context = mock(ListenerContext.class);

        try (MockedStatic<BotLogger> botLogger = Mockito.mockStatic(BotLogger.class)) {
            consumer.accept(context);

            verify(context).setShouldSave(true);
            botLogger.verify(() -> BotLogger.error(
                    isNull(), contains("AnnotationHandlerTest#failingHandler"), any(RuntimeException.class)));
        }

        assertThat(getThresholdCount()).isEqualTo(1);
        assertThat(CircuitBreaker.isOpen()).isFalse();
    }

    @Test
    void circuitBreakerThresholdIsFifteen() throws Exception {
        assertThat(getCircuitBreakThreshold()).isEqualTo(15);
    }

    private Consumer<ListenerContext> buildConsumer(String methodName, Function<ListenerContext, List<Object>> getArgs)
            throws Exception {
        Method handlerMethod = AnnotationHandlerTest.class.getDeclaredMethod(methodName);
        Method buildConsumer =
                AnnotationHandler.class.getDeclaredMethod("buildConsumer", Method.class, Function.class, boolean.class);
        buildConsumer.setAccessible(true);
        @SuppressWarnings("unchecked")
        Consumer<ListenerContext> consumer =
                (Consumer<ListenerContext>) buildConsumer.invoke(null, handlerMethod, getArgs, true);
        return consumer;
    }

    private static int getThresholdCount() throws Exception {
        Field thresholdCount = CircuitBreaker.class.getDeclaredField("thresholdCount");
        thresholdCount.setAccessible(true);
        return thresholdCount.getInt(null);
    }

    private static int getCircuitBreakThreshold() throws Exception {
        Field circuitBreakThreshold = CircuitBreaker.class.getDeclaredField("CIRCUIT_BREAK_THRESHOLD");
        circuitBreakThreshold.setAccessible(true);
        return circuitBreakThreshold.getInt(null);
    }

    private static void invokeCircuitBreakerMethod(String methodName) throws Exception {
        Method method = CircuitBreaker.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(null);
    }

    private static void failingHandler() {
        throw new RuntimeException("boom");
    }
}
