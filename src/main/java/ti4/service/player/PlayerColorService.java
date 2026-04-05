package ti4.service.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.helpers.ColorChangeHelper;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.model.ColorModel;
import ti4.model.FactionModel;
import ti4.service.game.GameColorsService;

@UtilityClass
public class PlayerColorService {

    public static String getPreferredColor(Player player) {
        List<ColorModel> unusedColors = GameColorsService.getUnusedColorsWithBaseColorsFirst(player.getGame()).stream()
                .filter(colorModel -> canUseColor(player, colorModel.getAlias()))
                .toList();
        String faction = player.getFaction();
        if (faction.contains("tf")) return getFactionsPreferredColor(faction, unusedColors, Collections.emptyList());

        String color = getUsersPreferredColor(player, unusedColors);
        if (color != null) return color;

        List<String> usedHues = GameColorsService.getUsedHues(player.getGame());
        color = getFactionsPreferredColor(faction, unusedColors, usedHues);
        if (color != null) return color;

        return getPreferredColor(unusedColors, usedHues);
    }

    private static String getUsersPreferredColor(Player player, Collection<ColorModel> unusedColors) {
        return player.getUserSettings().getPreferredColors().stream()
                .filter(c -> unusedColors.contains(Mapper.getColor(c)))
                .findFirst()
                .map(Mapper::getColorName)
                .orElse(null);
    }

    private static String getFactionsPreferredColor(
            String faction, Collection<ColorModel> unusedColors, Collection<String> usedHues) {
        FactionModel factionModel = Mapper.getFaction(faction);
        if (factionModel == null) return null;
        List<String> preferredColors = new ArrayList<>(factionModel.getPreferredColours());
        Collections.shuffle(preferredColors);
        return preferredColors.stream()
                .filter(color -> unusedColors.contains(Mapper.getColor(color)))
                .filter(color -> !usedHues.contains(Mapper.getColor(color).getHue()))
                .findFirst()
                .map(Mapper::getColorName)
                .orElse(null);
    }

    private static String getPreferredColor(Collection<ColorModel> unusedColors, Collection<String> usedHues) {
        return unusedColors.stream()
                .filter(c -> !usedHues.contains(c.getHue()))
                .findFirst()
                .map(ColorModel::getName)
                .map(Mapper::getColorName)
                .orElse(unusedColors.stream()
                        .findFirst()
                        .map(ColorModel::getName)
                        .map(Mapper::getColorName)
                        .orElse(null));
    }

    private static boolean canUseColor(Player player, String color) {
        return ColorChangeHelper.isColorAllowedForPlayer(color, player);
    }
}
