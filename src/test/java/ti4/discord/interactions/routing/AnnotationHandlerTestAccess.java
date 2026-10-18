package ti4.discord.interactions.routing;

import java.util.List;

/** Exposes the package-private handler class scan to tests in other packages. */
public final class AnnotationHandlerTestAccess {

    private AnnotationHandlerTestAccess() {}

    public static List<Class<?>> allHandlerClasses() {
        return AnnotationHandler.getAllClasses();
    }
}
