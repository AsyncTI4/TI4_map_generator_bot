package ti4.ai.scoring;

import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;

public enum Pooling {
    SEPARATE,
    EITHER_AT_BEST,
    COMBINED;

    private static final String XXCHA_HERO = "xxchahero";
    private static final String XXCHA_BREAKTHROUGH = "xxchabt";

    public static Pooling of(Player seat) {
        if (seat.hasLeaderUnlocked(XXCHA_HERO)) return COMBINED;
        if (seat.hasUnlockedBreakthrough(XXCHA_BREAKTHROUGH)) return EITHER_AT_BEST;
        return SEPARATE;
    }

    public int resourcesOf(Planet planet) {
        return value(Math.max(0, planet.getResources()), Math.max(0, planet.getInfluence()), true);
    }

    public int influenceOf(Planet planet) {
        return value(Math.max(0, planet.getResources()), Math.max(0, planet.getInfluence()), false);
    }

    public int resourcesOf(Game game, String planetName) {
        Planet planet = game.getPlanetsInfo().get(planetName);
        return planet == null ? 0 : resourcesOf(planet);
    }

    public int influenceOf(Game game, String planetName) {
        Planet planet = game.getPlanetsInfo().get(planetName);
        return planet == null ? 0 : influenceOf(planet);
    }

    public boolean paysBothKinds() {
        return this == COMBINED;
    }

    private int value(int resources, int influence, boolean asResources) {
        return switch (this) {
            case COMBINED -> resources + influence;
            case EITHER_AT_BEST -> Math.max(resources, influence);
            case SEPARATE -> asResources ? resources : influence;
        };
    }
}
