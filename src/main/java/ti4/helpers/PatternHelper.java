package ti4.helpers;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PatternHelper {

    public static final Pattern SPACE_PATTERN = Pattern.compile(" ");
    public static final Pattern UNDERSCORE_PATTERN = Pattern.compile("_");
    public static final Pattern DOUBLE_UNDERSCORE_PATTERN = Pattern.compile("__");
    public static final Pattern FIN_SEPERATOR_PATTERN = Pattern.compile("finSep");
    public static final Pattern BLANK_WORD_PATTERN = Pattern.compile("blank");
    public static final Pattern NEWLINE_OPTIONAL_GT_PATTERN = Pattern.compile("\n(> )?");
    public static final Pattern NON_R_PATTERN = Pattern.compile("[^R]");
    public static final Pattern NON_G_PATTERN = Pattern.compile("[^G]");
    public static final Pattern NON_Y_PATTERN = Pattern.compile("[^Y]");
    public static final Pattern NON_B_PATTERN = Pattern.compile("[^B]");
    public static final Pattern LOWERCASE_LETTER_PATTERN = Pattern.compile("[a-z]");
}
