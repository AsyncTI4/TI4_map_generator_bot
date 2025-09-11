package ti4.spring.context;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to indicate whether a PUT/POST/DELETE/PATCH controller method should trigger a game lock and save.
 * If you set the value to false, no request context values will be set (and thus it will not save)
 * If you set save to false, it will skip saving the game for PUT/POST/DELETE/PATCH.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SetupRequestContext {
    boolean value() default true;

    boolean save() default true;
}
