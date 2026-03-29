package ti4.service.player;

import java.util.Collection;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.helpers.ColorChangeHelper;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.model.ColorModel;
import ti4.service.game.GameColorsService;

@UtilityClass
public class PlayerColorService {

    public static String getNewColor(Player player) {
        List<ColorModel> unusedColors = GameColorsService.getUnusedColors(player.getGame()).stream()
                .filter(colorModel -> canUseColor(player, colorModel.getAlias()))
                .toList();
        String color = getPreferredColor(player, unusedColors);
        if (color == null) {
            color = GameColorsService.getColorsPreferringBase(unusedColors)
                    .getFirst()
                    .getName();
        }
        return Mapper.getColorName(color);
    }

    private static String getPreferredColor(Player player, Collection<ColorModel> unusedColors) {
        return player.getUserSettings().getPreferredColors().stream()
                .filter(c -> unusedColors.contains(Mapper.getColor(c)))
                .findFirst()
                .orElse(getPreferredColor(player.getFaction(), unusedColors));
    }

    private static String getPreferredColor(String faction, Collection<ColorModel> unusedColors) {
        return Mapper.getFaction(faction).getPreferredColours().stream()
                .filter(color -> !unusedColors.contains(Mapper.getColor(color)))
                .findFirst()
                .orElse(null);
    }

    private static boolean canUseColor(Player player, String color) {
        return ColorChangeHelper.isColorNotAllowedForPlayer(color, player);
    }
}
