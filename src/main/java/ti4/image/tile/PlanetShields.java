package ti4.image.tile;

import java.util.HashMap;
import java.util.Map;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.aeterna.AeternaBreakthroughHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.bluereverie.BlueReverieTechHelper;
import ti4.model.UnitModel;
import ti4.service.actioncard.DisableService;

public final class PlanetShields {

    private PlanetShields() {}

    public static boolean shouldHaveShield(UnitHolder unitHolder, Game game) {
        if (unitHolder.getTokenList().stream().anyMatch(token -> token.contains(Constants.WORLD_DESTROYED))) {
            return false;
        }
        if (unitHolder.getTokenList().stream().anyMatch(token -> token.contains("superweapon_mors"))) {
            return true;
        }
        if (unitHolder instanceof Planet planet && BlueReverieTechHelper.hasEcologicalEngineeringShield(game, planet)) {
            return true;
        }
        if (unitHolder instanceof Planet planet
                && game.getRealPlayers().stream()
                        .anyMatch(player -> AeternaBreakthroughHandler.hasTwilightDefenseSystem(player, planet))) {
            return true;
        }
        for (Player player : game.getRealPlayers()) {
            if (player.hasAbility("synthesis") && player.getReadiedPlanets().contains(unitHolder.getName())) {
                return true;
            }
        }
        if (ButtonHelper.isLawInPlay(game, "conventions")
                && ButtonHelper.getTypeOfPlanet(game, unitHolder.getName()).contains("cultural")) {
            return true;
        }
        return hasShieldingUnit(unitHolder, game);
    }

    private static boolean hasShieldingUnit(UnitHolder unitHolder, Game game) {
        Map<Units.UnitKey, Integer> planetUnits = new HashMap<>(unitHolder.getUnits());
        Tile tile = unitHolder instanceof Planet planet ? game.getTileFromPlanet(planet.getName()) : null;
        for (Player player : game.getRealPlayers()) {
            for (Units.UnitKey unitKey : planetUnits.keySet()) {
                if (!player.unitBelongsToPlayer(unitKey)) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                if (unitModel == null) {
                    continue;
                }
                if (DisableService.losesPdsAbilities(game, tile, player, unitModel)) {
                    continue;
                }
                if (unitModel.getPlanetaryShield()) {
                    return !"mech".equalsIgnoreCase(unitModel.getBaseType())
                            || !ButtonHelper.isLawInPlay(game, "articles_war");
                }
            }
        }
        return false;
    }
}
