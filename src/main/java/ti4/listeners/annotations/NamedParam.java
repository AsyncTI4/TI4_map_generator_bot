package ti4.listeners.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Use this annotation for potentially duplicated parameter types, because annotation handler cannot read the parameter names
 * <p>
 * It is technically possible to get the parameter names by updating the compiler settings {@link https://stackoverflow.com/questions/2237803/can-i-obtain-method-parameter-name-using-java-reflection}
 */
@Documented
@Retention(RUNTIME)
@Target({ ElementType.PARAMETER })
public @interface NamedParam {
    String value();
}
