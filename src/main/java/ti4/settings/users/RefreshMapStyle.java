package ti4.settings.users;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;

@Getter
public enum RefreshMapStyle {
    COMBINED("combined", "Single combined image"),
    SPLIT("split", "Separate map & stats, posted in channel"),
    SPLIT_PRIVATE("split_private", "Separate map & stats, only visible to me");

    private final String value;
    private final String label;

    RefreshMapStyle(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public boolean isSplit() {
        return this != COMBINED;
    }

    public boolean postsInChannel() {
        return this == SPLIT;
    }

    public static Optional<RefreshMapStyle> fromValue(String value) {
        return Arrays.stream(values())
                .filter(style -> style.value.equals(value))
                .findFirst();
    }
}
