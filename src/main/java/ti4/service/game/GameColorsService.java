package ti4.service.game;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.model.ColorModel;

@UtilityClass
public class GameColorsService {

    private static final List<String> BASE_COLORS =
            List.of("red", "blue", "yellow", "purple", "green", "orange", "pink", "black");

    // Narrow heuristic for the most common form of color vision deficiency (red-green);
    // not a full CVD simulation across all hue pairs.
    private static final Map<String, Set<String>> RED_GREEN_CONFUSION_HUES =
            Map.of("RED", Set.of("GREEN"), "GREEN", Set.of("RED", "ORANGE"), "ORANGE", Set.of("GREEN"));

    public static boolean isCommonRedGreenConfusionPair(String hue1, String hue2) {
        return RED_GREEN_CONFUSION_HUES.getOrDefault(hue1, Set.of()).contains(hue2);
    }

    /**
     * Same heuristic as {@link #isCommonRedGreenConfusionPair(String, String)}, plus an RGB-based
     * fallback for gradient colors bucketed under a generic MULTI1/MULTI2/MULTI3 hue rather than a
     * specific named one (e.g. watermelon, rainbow), which the hue-only check can't see.
     */
    public static boolean isCommonRedGreenConfusionPair(ColorModel c1, ColorModel c2) {
        if (isCommonRedGreenConfusionPair(c1.getHue(), c2.getHue())) return true;
        return (c1.hasRedComponent() && c2.hasGreenComponent()) || (c1.hasGreenComponent() && c2.hasRedComponent());
    }

    // Uses the raw player map rather than getRealPlayers(), since this needs to be checkable at
    // game creation time, before anyone has picked a faction/color (isRealPlayer() requires both).
    public static boolean hasColorAccessibilityPlayer(Game game) {
        return game.getPlayers().values().stream()
                .anyMatch(p -> p.getUserSettings().isPrefersColorAccessibilityCues());
    }

    /**
     * True if `candidate` shares a hue with an already-used color in this game, or - when the game
     * has a player who has opted into color-accessibility cues - commonly confuses with one (see
     * {@link #isCommonRedGreenConfusionPair(ColorModel, ColorModel)}, which also covers gradient
     * colors via its RGB fallback, unlike a plain hue-string comparison). Used to filter fallback
     * color candidates during auto-assignment.
     */
    public static boolean conflictsWithUsedColors(Game game, ColorModel candidate) {
        boolean checkConfusionPairs = hasColorAccessibilityPlayer(game);
        return getUsedColors(game).stream()
                .anyMatch(used -> used.getHue().equals(candidate.getHue())
                        || (checkConfusionPairs && isCommonRedGreenConfusionPair(candidate, used)));
    }

    public static List<ColorModel> getUnusedColorsWithBaseColorsFirst(Game game) {
        List<ColorModel> unusedColors = getUnusedColors(game);
        return unusedColors.stream()
                .sorted(Comparator.comparing(colorModel -> BASE_COLORS.contains(colorModel.getName()) ? 0 : 1))
                .toList();
    }

    public static List<ColorModel> getUnusedColors(Game game) {
        List<ColorModel> usedColors = getUsedColors(game);
        return Mapper.getColors().stream()
                .filter(color -> !usedColors.contains(color))
                .toList();
    }

    public static List<ColorModel> getUsedColors(Game game) {
        return game.getPlayers().values().stream()
                .map(Player::getColor)
                .map(Mapper::getColor)
                .filter(Objects::nonNull)
                .toList();
    }

    public static List<String> getUsedHues(Game game) {
        return getUsedColors(game).stream().map(ColorModel::getHue).toList();
    }
}
