package ti4.helpers;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PatternHelper {

    public static final Pattern SPACE_PATTERN = Pattern.compile(" ");
    public static final Pattern UNDERSCORE_PATTERN = Pattern.compile("_");
    public static final Pattern DOUBLE_UNDERSCORE_PATTERN = Pattern.compile("__");
}
