package ti4.service.tech;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
public class EntropicScarService {

    public static List<String> getAvailableTechnologies(Game game, Player player) {
        List<String> technologies = new ArrayList<>(player.getFactionTechs());
        if (game.isTwilightsFallMode()) {
            technologies.add("antimatter");
            technologies.add("wavelength");
        }
        technologies.remove("vax");
        technologies.remove("vay");
        player.getTechs().forEach(technologies::remove);
        return technologies;
    }

    public static boolean couldScorePublicObjectiveAfterScar(Game game, Player player) {
        List<String> available = getAvailableTechnologies(game, player);
        int gainable = Math.min(
                available.size(),
                Math.min(countScarSystemsWithShips(game, player), countTechnologiesPlayerCanPayFor(player)));
        if (gainable == 0) return false;

        List<String> unscoredObjectives = getUnscoredPublicObjectives(game, player);
        if (unscoredObjectives.isEmpty()) return false;

        for (List<String> choice : combinations(available, gainable)) {
            boolean scorable = whileOwning(
                    player,
                    choice,
                    () -> unscoredObjectives.stream().anyMatch(objective -> meetsThreshold(game, player, objective)));
            if (scorable) return true;
        }
        return false;
    }

    private static int countTechnologiesPlayerCanPayFor(Player player) {
        boolean scepterReady = player.hasRelicReady("emelpar") || player.hasRelicReady("absol_emelpar");
        return Math.max(0, player.getStrategicCC()) + (scepterReady ? 1 : 0);
    }

    private static int countScarSystemsWithShips(Game game, Player player) {
        return (int) game.getTileMap().values().stream()
                .filter(tile ->
                        isScarSystem(tile) && Tile.tileHasPlayerShips(player).test(tile))
                .count();
    }

    private static boolean isScarSystem(Tile tile) {
        boolean isAurelionSystem = tile.getSpaceUnitHolder().getUnitKeys().stream()
                .anyMatch(unitKey -> unitKey.unitType() == UnitType.Aurelion);
        return tile.isScar() || isAurelionSystem;
    }

    private static List<String> getUnscoredPublicObjectives(Game game, Player player) {
        return game.getRevealedPublicObjectives().keySet().stream()
                .filter(objective -> Mapper.getPublicObjective(objective) != null)
                .filter(objective -> !game.getScoredPublicObjectives()
                        .getOrDefault(objective, List.of())
                        .contains(player.getUserID()))
                .toList();
    }

    private static boolean meetsThreshold(Game game, Player player, String objective) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(objective, game);
        return threshold > 0
                && ListPlayerInfoService.getPlayerProgressOnObjective(objective, game, player) >= threshold;
    }

    private static boolean whileOwning(Player player, List<String> technologies, BooleanSupplier check) {
        List<String> owned = player.getTechs();
        owned.addAll(technologies);
        try {
            return check.getAsBoolean();
        } finally {
            technologies.forEach(owned::remove);
        }
    }

    private static List<List<String>> combinations(List<String> items, int size) {
        List<List<String>> result = new ArrayList<>();
        addCombinations(items, size, 0, new ArrayList<>(), result);
        return result;
    }

    private static void addCombinations(
            List<String> items, int size, int start, List<String> current, List<List<String>> result) {
        if (current.size() == size) {
            result.add(List.copyOf(current));
            return;
        }
        for (int i = start; i < items.size(); i++) {
            current.add(items.get(i));
            addCombinations(items, size, i + 1, current, result);
            current.removeLast();
        }
    }
}
