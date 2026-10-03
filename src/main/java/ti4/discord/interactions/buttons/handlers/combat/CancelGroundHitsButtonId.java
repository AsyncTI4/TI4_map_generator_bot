package ti4.discord.interactions.buttons.handlers.combat;

import java.util.List;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;

public record CancelGroundHitsButtonId(String tilePosition, int hits, String planet, boolean interlocking) {

    public static final String PREFIX = "cancelGroundHits_";
    private static final String INTERLOCKING_SUFFIX = "_interlocking";

    public static String of(String tilePosition, int hits, String planet) {
        return new CancelGroundHitsButtonId(tilePosition, hits, planet, false).toButtonId();
    }

    public static String interlockingOf(String tilePosition, int hits, String planet) {
        return new CancelGroundHitsButtonId(tilePosition, hits, planet, true).toButtonId();
    }

    public String toButtonId() {
        StringBuilder id =
                new StringBuilder(PREFIX).append(tilePosition).append('_').append(hits);
        if (hasPlanet()) {
            id.append('_').append(planet);
        }
        if (interlocking) {
            id.append(INTERLOCKING_SUFFIX);
        }
        return id.toString();
    }

    public static CancelGroundHitsButtonId parse(String buttonID) {
        String payload = buttonID.substring(buttonID.indexOf(PREFIX) + PREFIX.length());
        boolean interlocking = payload.endsWith(INTERLOCKING_SUFFIX);
        if (interlocking) {
            payload = payload.substring(0, payload.length() - INTERLOCKING_SUFFIX.length());
        }
        String[] segments = payload.split("_", 3);
        String planet = segments.length > 2 && !segments[2].isBlank() ? segments[2] : null;
        return new CancelGroundHitsButtonId(segments[0], Integer.parseInt(segments[1]), planet, interlocking);
    }

    public boolean hasPlanet() {
        return planet != null && !planet.isBlank();
    }

    public static String soleGroundForcePlanet(Tile tile, Player player) {
        if (tile == null || player == null) {
            return null;
        }
        List<Planet> planetsWithGroundForces = tile.getPlanetUnitHolders().stream()
                .filter(planet -> planet.hasGroundForces(player))
                .toList();
        return planetsWithGroundForces.size() == 1
                ? planetsWithGroundForces.getFirst().getName()
                : null;
    }
}
