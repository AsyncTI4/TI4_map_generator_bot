package ti4.ai.eval;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
public class TraitNeeds {

    public record Need(String secret, String trait, int owned, int required) {
        public int missing() {
            return required - owned;
        }

        public boolean wants(Planet planet) {
            return planet.getPlanetTypes().contains(trait);
        }
    }

    public static List<Need> of(Game game, Player seat) {
        List<Need> needs = new ArrayList<>();
        for (String secret : seat.getSecretsUnscored().keySet()) {
            String trait = ObjectiveValue.TRAIT_SECRETS.get(secret);
            if (trait == null) continue;
            int required = ListPlayerInfoService.getObjectiveThreshold(secret, game);
            int owned = owned(game, seat, trait);
            if (owned < required) needs.add(new Need(secret, trait, owned, required));
        }
        return needs;
    }

    public static boolean wanted(List<Need> needs, Planet planet) {
        return needs.stream().anyMatch(need -> need.wants(planet));
    }

    private static int owned(Game game, Player seat, String trait) {
        int count = 0;
        for (String name : seat.getPlanets()) {
            Planet planet = game.getPlanetsInfo().get(name);
            if (planet != null
                    && ObjectiveValue.countsForScoring(game, planet)
                    && planet.getPlanetTypes().contains(trait)) {
                count++;
            }
        }
        return count;
    }
}
