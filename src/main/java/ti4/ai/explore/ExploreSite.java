package ti4.ai.explore;

import ti4.ai.eval.BoardView;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;

public record ExploreSite(
        Game game, Player seat, Tile tile, String planet, int infantry, int mechs, ExploreOutlook outlook) {

    public static ExploreSite onBoard(Game game, Player seat, String planetName, ExploreOutlook outlook) {
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        return new ExploreSite(
                game,
                seat,
                tile,
                planetName,
                BoardView.count(planet, seat, UnitType.Infantry),
                BoardView.count(planet, seat, UnitType.Mech),
                outlook);
    }

    public static ExploreSite landing(
            Game game, Player seat, Tile tile, Planet planet, int infantry, int mechs, ExploreOutlook outlook) {
        return new ExploreSite(game, seat, tile, planet.getName(), infantry, mechs, outlook);
    }

    public Planet holder() {
        return game.getUnitHolderFromPlanet(planet);
    }

    public boolean lastGroundForce() {
        return infantry <= 1 && mechs == 0;
    }

    public boolean hasDemilitarizedZone() {
        return isDemilitarized(holder());
    }

    public static boolean isDemilitarized(Planet planet) {
        return planet.getTokenList().stream().anyMatch(token -> token.contains(Constants.DMZ));
    }

    public double infantryCost() {
        double cost = ExploreValues.INFANTRY_UNIT + outlook.expansionNeed(holder());
        if (lastGroundForce() && outlook.enemyCanReach(tile)) {
            cost += ExploreValues.LAST_FORCE_RISK_SHARE * outlook.planetStake(holder());
        }
        return cost;
    }

    public double viaMechOrInfantry(double benefit) {
        if (benefit <= 0) return 0;
        if (mechs > 0) return benefit;
        if (infantry > 0) return Math.max(0, benefit - infantryCost());
        return 0;
    }

    public boolean mechIsWorthIt(double benefit) {
        return mechs > 0 && benefit > 0;
    }

    public boolean infantryIsWorthIt(double benefit) {
        return mechs == 0 && infantry > 0 && benefit > infantryCost();
    }
}
