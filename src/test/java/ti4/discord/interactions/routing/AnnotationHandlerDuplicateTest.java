package ti4.discord.interactions.routing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Guards against the same interaction-handler value being registered by more than one method.
 *
 * <p>{@link HandlerRegistry#register} does a plain {@code handlers.put(key, ...)} into a {@code HashMap}, so a second
 * method declaring the same {@code value()} silently overwrites the first with no warning. When that happens, edits to
 * one copy have no effect and there is no way to tell which method the bot actually runs. This test fails the build if
 * any such duplicate exists.
 *
 * <p>It mirrors {@link AnnotationHandler#registerHandlers} exactly (same class list, same {@code getAnnotationsByType},
 * same non-static skip) so it sees precisely the set of registrations the bot performs at startup. Detection is scoped
 * per annotation type because Button/Modal/Selection each own a separate registry (separate key space).
 */
class AnnotationHandlerDuplicateTest {

    @Test
    void noDuplicateButtonHandlers() {
        assertNoDuplicates(ButtonHandler.class, a -> ((ButtonHandler) a).value());
    }

    @Test
    void noDuplicateModalHandlers() {
        assertNoDuplicates(ModalHandler.class, a -> ((ModalHandler) a).value());
    }

    @Test
    void noDuplicateSelectionHandlers() {
        assertNoDuplicates(SelectionHandler.class, a -> ((SelectionHandler) a).value());
    }

    private static <H extends Annotation> void assertNoDuplicates(
            Class<H> handlerClass, Function<Annotation, String> valueOf) {
        // value -> the distinct methods that register it. Keyed on Method (not name) so overloads stay distinct and any
        // duplication in the getAllClasses() list collapses via Method.equals.
        Map<String, Set<Method>> byValue = new LinkedHashMap<>();

        for (Class<?> klass : AnnotationHandler.getAllClasses()) {
            for (Method method : klass.getDeclaredMethods()) {
                method.setAccessible(true);
                H[] annotations = method.getAnnotationsByType(handlerClass);
                if (annotations.length == 0) continue;
                // Non-static methods are skipped by registerHandlers and never register, so they can't overwrite.
                if (!Modifier.isStatic(method.getModifiers())) continue;
                for (H annotation : annotations) {
                    String value = valueOf.apply(annotation);
                    byValue.computeIfAbsent(value, v -> new LinkedHashSet<>()).add(method);
                }
            }
        }

        Map<String, Set<Method>> duplicates = byValue.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        assertTrue(
                duplicates.isEmpty(),
                () -> "Duplicate @" + handlerClass.getSimpleName()
                        + " value(s) detected — each must be handled by exactly one method. "
                        + "The registry keeps only one of them and silently drops the rest:\n"
                        + duplicates.entrySet().stream()
                                .map(e -> "  \"" + e.getKey() + "\" -> ["
                                        + e.getValue().stream()
                                                .map(AnnotationHandlerDuplicateTest::describe)
                                                .collect(Collectors.joining(", "))
                                        + "]")
                                .collect(Collectors.joining("\n")));
    }

    private static String describe(Method method) {
        String params = Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
        return method.getDeclaringClass().getName() + "#" + method.getName() + "(" + params + ")";
    }
}
