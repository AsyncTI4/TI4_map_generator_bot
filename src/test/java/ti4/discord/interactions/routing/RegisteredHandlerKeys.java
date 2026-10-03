package ti4.discord.interactions.routing;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/** The handler keys {@link AnnotationHandler} registers at startup, collected with the same class scan. */
final class RegisteredHandlerKeys {

    private RegisteredHandlerKeys() {}

    static Set<String> buttonKeys() {
        return of(ButtonHandler.class, ButtonHandler::value);
    }

    static Set<String> modalKeys() {
        return of(ModalHandler.class, ModalHandler::value);
    }

    static Set<String> selectionKeys() {
        return of(SelectionHandler.class, SelectionHandler::value);
    }

    private static <H extends Annotation> Set<String> of(Class<H> handlerClass, Function<H, String> valueOf) {
        Set<String> keys = new TreeSet<>();
        for (Class<?> klass : AnnotationHandler.getAllClasses()) {
            for (Method method : klass.getDeclaredMethods()) {
                // registerHandlers skips non-static methods, so they never become keys.
                if (!Modifier.isStatic(method.getModifiers())) continue;
                for (H annotation : method.getAnnotationsByType(handlerClass)) {
                    keys.add(valueOf.apply(annotation));
                }
            }
        }
        return keys;
    }
}
