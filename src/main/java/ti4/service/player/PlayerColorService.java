package ti4.service.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ColorChangeHelper;
import ti4.image.Mapper;
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
        // null game means "don't filter by conflicts" - used for "tf" factions, same as before.
        if (faction.contains("tf")) return getFactionsPreferredColor(null, faction, unusedColors);

        String color = getUsersPreferredColor(player, unusedColors);
        if (color != null) return color;

        Game game = player.getGame();
        color = getFactionsPreferredColor(game, faction, unusedColors);
        if (color != null) return color;

        return getPreferredColor(game, unusedColors);
    }

    private static String getUsersPreferredColor(Player player, Collection<ColorModel> unusedColors) {
        return player.getUserSettings().getPreferredColors().stream()
                .filter(c -> unusedColors.contains(Mapper.getColor(c)))
                .findFirst()
                .map(Mapper::getColorName)
                .orElse(null);
    }

    private static String getFactionsPreferredColor(Game game, String faction, Collection<ColorModel> unusedColors) {
        FactionModel factionModel = Mapper.getFaction(faction);
        if (factionModel == null) return null;
        List<String> preferredColors = new ArrayList<>(factionModel.getPreferredColours());
        Collections.shuffle(preferredColors);
        return preferredColors.stream()
                .filter(color -> unusedColors.contains(Mapper.getColor(color)))
                .filter(color ->
                        game == null || !GameColorsService.conflictsWithUsedColors(game, Mapper.getColor(color)))
                .findFirst()
                .map(Mapper::getColorName)
                .orElse(null);
    }

    private static String getPreferredColor(Game game, Collection<ColorModel> unusedColors) {
        return unusedColors.stream()
                .filter(c -> !GameColorsService.conflictsWithUsedColors(game, c))
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
