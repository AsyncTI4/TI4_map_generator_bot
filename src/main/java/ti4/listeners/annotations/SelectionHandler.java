package ti4.listeners.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Repeatable(ManySelectionHandlers.class)
@Target({ ElementType.METHOD })
public @interface SelectionHandler {
    String value();
}
