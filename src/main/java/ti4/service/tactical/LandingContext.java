package ti4.service.tactical;

import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.utils.StringUtils;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;

public final class LandingContext {
    public final Game game;
    public final Player mainPlayer;
    public final Tile tile;
    public final UnitHolder space;
    public final Planet planet;
    public final String planetName;
    public final String planetRep;
    public final String landPrefix;
    public final String unlandPrefix;
    public final List<Player> alliedPlayers;
    public final List<UnitType> committable;

    private LandingContext(
            Game game,
            Player mainPlayer,
            Tile tile,
            UnitHolder space,
            Planet planet,
            String landPrefix,
            String unlandPrefix,
            List<UnitType> committable) {
        this.game = game;
        this.mainPlayer = mainPlayer;
        this.tile = tile;
        this.space = space;
        this.planet = planet;
        this.planetName = planet.getName();
        this.planetRep = Helper.getPlanetRepresentation(planet.getName(), game);
        this.landPrefix = landPrefix;
        this.unlandPrefix = unlandPrefix;
        this.alliedPlayers = computeAllies(mainPlayer, game);
        this.committable = committable;
    }

    public static LandingContext of(
            Game game,
            Player mainPlayer,
            Tile tile,
            UnitHolder space,
            Planet planet,
            String landPrefix,
            String unlandPrefix,
            List<UnitType> committable) {
        return new LandingContext(game, mainPlayer, tile, space, planet, landPrefix, unlandPrefix, committable);
    }

    private static List<Player> computeAllies(Player player, Game game) {
        List<Player> allies = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 != player && player.getAllianceMembers().contains(p2.getFaction())) {
                allies.add(p2);
            }
        }
        return allies;
    }

    public boolean hasSpaceUnits(Player owner, UnitType unitType, UnitState state, int count) {
        return space.getUnitCountForState(unitType, owner, state) >= count;
    }

    public boolean hasPlanetUnits(Player owner, UnitType unitType, UnitState state, int count) {
        return planet.getUnitCountForState(unitType, owner, state) >= count;
    }

    public String buildLandButtonId(int count, UnitType unitType, Player owner) {
        return landPrefix + count + unitType.getValue() + "_" + planetName + "_" + owner.getColor();
    }

    public String buildUnlandButtonId(int count, UnitType unitType, Player owner) {
        return unlandPrefix + count + unitType.getValue() + "_" + planetName + "_" + owner.getColor();
    }

    public String buildLandLabel(int count, Player owner, UnitState state, UnitType unitType) {
        return "Land " + count + colorTextFor(owner) + stateDescriptor(state) + " " + unitType.humanReadableName()
                + " on " + planetRep;
    }

    public String buildUnlandLabel(int count, Player owner, UnitState state, UnitType unitType) {
        return "Un-land " + count + colorTextFor(owner) + stateDescriptor(state) + " " + unitType.humanReadableName()
                + " from " + planetRep;
    }

    public String colorTextFor(Player owner) {
        return owner == mainPlayer ? "" : (" " + StringUtils.capitalize(owner.getColor()) + " ");
    }

    public String stateDescriptor(UnitState state) {
        return state != UnitState.none ? " " + state.humanDescr() : "";
    }
}
