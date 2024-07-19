package ti4.listeners.annotations;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Documented
@Retention(RUNTIME)
@Repeatable(ManyButtonHandlers.class)
@Target({ ElementType.METHOD })
public @interface ButtonHandler {
    String value();
}
