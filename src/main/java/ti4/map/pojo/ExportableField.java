package ti4.map.pojo;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ ElementType.FIELD })
@Retention(RUNTIME)
@Documented
public @interface ExportableField {}
