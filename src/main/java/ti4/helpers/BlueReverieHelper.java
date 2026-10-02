package ti4.helpers;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;

@UtilityClass
public class BlueReverieHelper {
    public static void checkXinHarmony(Game game) {
        for (Player player : game.getRealPlayers()) {
            if (!player.hasAbility("harmony")) {
                continue;
            }

            String suffix = player.getStarbalanceCounter() > player.getSteelbalanceCounter()
                    ? "star"
                    : player.getSteelbalanceCounter() > player.getStarbalanceCounter() ? "steel" : "";

            replaceHarmonyUnit(player, suffix);
            replaceHarmonyTech(player, "dsxing", suffix);
            replaceHarmonyTech(player, "dsxiny", suffix);
        }
    }

    private static void replaceHarmonyUnit(Player player, String suffix) {
        String desired = "xin_mech" + suffix;
        List<String> variants = List.of("xin_mech", "xin_mechsteel", "xin_mechstar");

        if (player.ownsUnit(desired)) {
            return;
        }

        variants.stream().filter(player::ownsUnit).forEach(player::removeOwnedUnitByID);
        player.addOwnedUnitByID(desired);
    }

    private static void replaceHarmonyTech(Player player, String baseTech, String suffix) {
        List<String> variants = List.of(baseTech, baseTech + "steel", baseTech + "star");
        String currentTech = variants.stream()
                .filter(player.getTechs()::contains)
                .findFirst()
                .orElse(null);

        if (currentTech == null) {
            return;
        }

        String desired = baseTech + suffix;
        if (desired.equals(currentTech)) {
            return;
        }

        boolean exhausted = player.getExhaustedTechs().contains(currentTech);
        player.getTechs().removeAll(variants);
        player.getExhaustedTechs().removeAll(variants);
        player.addTech(desired);

        if (exhausted) {
            player.getExhaustedTechs().add(desired);
        }
    }

    public static boolean hasXinCommanderUnlock(Player player, Game game) {
        List<Planet> occupiedPlanets = game.getTileMap().values().stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .filter(planet -> FoWHelper.playerHasUnitsOnPlanet(player, planet))
                .toList();

        boolean hasLegendaryPlanet = occupiedPlanets.stream().anyMatch(Planet::isLegendary);

        boolean hasThreeDifferentTraits = occupiedPlanets.stream()
                .filter(planet -> planet.getPlanetTypes().contains("cultural"))
                .anyMatch(cultural -> occupiedPlanets.stream()
                        .filter(planet ->
                                planet != cultural && planet.getPlanetTypes().contains("industrial"))
                        .anyMatch(industrial -> occupiedPlanets.stream()
                                .anyMatch(planet -> planet != cultural
                                        && planet != industrial
                                        && planet.getPlanetTypes().contains("hazardous"))));

        return hasLegendaryPlanet && hasThreeDifferentTraits;
    }
}
