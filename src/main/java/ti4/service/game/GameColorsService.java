package ti4.service.game;

import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.ColorModel;

@UtilityClass
public class GameColorsService {

    private static final List<String> BASE_COLORS =
            List.of("red", "blue", "yellow", "purple", "green", "orange", "pink", "black");

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
                .toList();
    }

    public static List<String> getUsedHues(Game game) {
        return getUsedColors(game).stream().map(ColorModel::getHue).toList();
    }
}
