package ti4.discord.interactions.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The handler scan filters class names before loading them, so it loads only the bot's own classes instead of every
 * library class on the classpath (which cost about 15 s per JVM).
 */
class AnnotationHandlerScanTest {

    @Test
    void theScanFindsOnlyTheBotsOwnClasses() {
        List<Class<?>> classes = AnnotationHandler.getAllClasses();

        assertThat(classes).isNotEmpty().contains(AnnotationHandler.class);
        assertThat(classes).allSatisfy(c -> assertThat(c.getName()).startsWith("ti4."));
    }

    @Test
    void theScanStillFindsTheButtonHandlers() {
        long handlerMethods = AnnotationHandler.getAllClasses().stream()
                .flatMap(c -> Arrays.stream(c.getDeclaredMethods()))
                .filter(AnnotationHandlerScanTest::isButtonHandler)
                .count();

        assertThat(handlerMethods).isGreaterThan(100);
    }

    private static boolean isButtonHandler(Method method) {
        return method.getAnnotationsByType(ButtonHandler.class).length > 0;
    }
}
