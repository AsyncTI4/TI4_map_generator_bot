package ti4.model;

import java.util.regex.Pattern;
import javax.annotation.Nullable;

public final class SegmentNames {

    public static final String MAIN = "main";
    public static final String FRACTURE = "fracture";
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9-]{1,20}");
    private static final Pattern BOARD_NAME_PATTERN = Pattern.compile("board-[a-g]");

    private SegmentNames() {}

    public static boolean isValid(@Nullable String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    public static boolean isReserved(String name) {
        return MAIN.equals(name)
                || FRACTURE.equals(name)
                || BOARD_NAME_PATTERN.matcher(name).matches();
    }
}
