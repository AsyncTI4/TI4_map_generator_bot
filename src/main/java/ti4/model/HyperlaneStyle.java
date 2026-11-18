package ti4.model;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;

@Getter
public enum HyperlaneStyle {
    DASHED("Dashed lanes"),
    ARROWS("Arrows"),
    FADING("Fading lanes"),
    GATES("Entry/exit gates");

    private final String label;

    HyperlaneStyle(String label) {
        this.label = label;
    }

    public static Optional<HyperlaneStyle> fromName(String name) {
        return Arrays.stream(values())
                .filter(style -> style.name().equalsIgnoreCase(name))
                .findFirst();
    }
}
