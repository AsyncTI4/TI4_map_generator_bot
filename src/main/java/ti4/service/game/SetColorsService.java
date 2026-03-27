package ti4.service.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.helpers.ColorChangeHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.ColorModel;
import ti4.model.FactionModel;
import ti4.service.strategycard.PickStrategyCardService;

@UtilityClass
public class SetColorsService {

    private static final int FALLBACK_PRIORITY = 1000;

    public static int setPreferredColors(Game game) {
        List<Player> scPickOrder = PickStrategyCardService.getSCPickOrder(game);
        Map<String, Integer> scOrder = new HashMap<>();
        for (int i = 0; i < scPickOrder.size(); i++) {
            Player player = scPickOrder.get(i);
            if (player != null) {
                scOrder.put(player.getUserID(), i);
            }
        }

        Set<String> usedColors = new HashSet<>();
        Set<String> usedHues = new HashSet<>();
        for (Player player : game.getRealPlayers()) {
            if (!player.isColorManuallySet()) {
                continue;
            }
            ColorModel color = Mapper.getColor(player.getColor());
            if (color == null) {
                continue;
            }
            usedColors.add(color.getName());
            usedHues.add(color.getHue());
        }

        List<PlayerColorPreferences> playersToAssign = game.getRealPlayers().stream()
                .filter(player -> !player.isColorManuallySet())
                .map(player -> buildPreferences(player, game, usedColors))
                .toList();

        Map<Player, String> assigned = new LinkedHashMap<>();
        Set<Player> unassigned = new HashSet<>();
        unassigned.addAll(playersToAssign.stream().map(PlayerColorPreferences::player).toList());

        while (!unassigned.isEmpty()) {
            AssignmentChoice winningChoice = unassigned.stream()
                    .map(player -> pickBestChoice(player, playersToAssign, usedColors, usedHues, scOrder))
                    .filter(Objects::nonNull)
                    .min(Comparator.comparingInt((AssignmentChoice choice) -> choice.option.priority())
                            .thenComparingInt(choice -> choice.scPickOrder)
                            .thenComparingInt(choice -> choice.option.rank()))
                    .orElse(null);

            if (winningChoice == null) {
                break;
            }

            assigned.put(winningChoice.player, winningChoice.option.colorName());
            usedColors.add(winningChoice.option.colorName());
            ColorModel chosenColor = Mapper.getColor(winningChoice.option.colorName());
            if (chosenColor != null) {
                usedHues.add(chosenColor.getHue());
            }
            unassigned.remove(winningChoice.player);
        }

        int changed = 0;
        for (Map.Entry<Player, String> entry : assigned.entrySet()) {
            Player player = entry.getKey();
            String targetColor = entry.getValue();
            if (targetColor == null || Objects.equals(player.getColor(), targetColor)) {
                continue;
            }

            if (Mapper.isValidColor(player.getColor())) {
                ColorChangeHelper.changePlayerColor(game, player, player.getColor(), targetColor);
            } else {
                player.setColor(targetColor);
            }
            changed++;
        }

        return changed;
    }

    private static AssignmentChoice pickBestChoice(
            Player player,
            List<PlayerColorPreferences> playersToAssign,
            Set<String> usedColors,
            Set<String> usedHues,
            Map<String, Integer> scOrder) {
        PlayerColorPreferences preferences = playersToAssign.stream()
                .filter(pref -> pref.player().equals(player))
                .findFirst()
                .orElse(null);

        if (preferences == null) {
            return null;
        }

        ColorOption bestUniqueHue = preferences.options().stream()
                .filter(option -> !usedColors.contains(option.colorName()))
                .filter(option -> !usedHues.contains(option.hue()))
                .findFirst()
                .orElse(null);

        ColorOption bestAnyHue = preferences.options().stream()
                .filter(option -> !usedColors.contains(option.colorName()))
                .findFirst()
                .orElse(null);

        ColorOption chosen = bestUniqueHue != null ? bestUniqueHue : bestAnyHue;
        if (chosen == null) {
            return null;
        }

        int pickOrder = scOrder.getOrDefault(player.getUserID(), Integer.MAX_VALUE);
        return new AssignmentChoice(player, chosen, pickOrder);
    }

    private static PlayerColorPreferences buildPreferences(Player player, Game game, Set<String> unavailableColors) {
        Map<String, ColorOption> orderedOptions = new LinkedHashMap<>();

        List<String> userPreferences = player.getUserSettings().getPreferredColors();
        for (int i = 0; i < userPreferences.size(); i++) {
            String colorName = Mapper.getColorName(userPreferences.get(i));
            if (!addColorOption(orderedOptions, player, colorName, 0, i, unavailableColors)) {
                continue;
            }
        }

        FactionModel faction = Mapper.getFaction(player.getFaction());
        List<String> factionPreferences = faction == null ? List.of() : faction.getPreferredColours();
        for (int i = 0; i < factionPreferences.size(); i++) {
            String colorName = Mapper.getColorName(factionPreferences.get(i));
            if (!addColorOption(orderedOptions, player, colorName, i + 1, i, unavailableColors)) {
                continue;
            }
        }

        int rank = 0;
        for (ColorModel color : Mapper.getColors()) {
            String colorName = color.getName();
            if (orderedOptions.containsKey(colorName)) {
                continue;
            }
            if (unavailableColors.contains(colorName)) {
                continue;
            }
            if (ColorChangeHelper.colorIsExclusive(colorName, player)) {
                continue;
            }
            orderedOptions.put(colorName, new ColorOption(colorName, color.getHue(), FALLBACK_PRIORITY, rank++));
        }

        return new PlayerColorPreferences(player, new ArrayList<>(orderedOptions.values()));
    }

    private static boolean addColorOption(
            Map<String, ColorOption> options,
            Player player,
            String colorName,
            int priority,
            int rank,
            Set<String> unavailableColors) {
        ColorModel color = Mapper.getColor(colorName);
        if (color == null) {
            return false;
        }
        if (unavailableColors.contains(color.getName())) {
            return false;
        }
        if (ColorChangeHelper.colorIsExclusive(color.getName(), player)) {
            return false;
        }
        options.putIfAbsent(color.getName(), new ColorOption(color.getName(), color.getHue(), priority, rank));
        return true;
    }

    private record ColorOption(String colorName, String hue, int priority, int rank) {}

    private record PlayerColorPreferences(Player player, List<ColorOption> options) {}

    private record AssignmentChoice(Player player, ColorOption option, int scPickOrder) {}
}
