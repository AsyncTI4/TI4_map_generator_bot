package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.GameManager;
import ti4.service.planet.AddPlanetService;
import ti4.testUtils.BaseTi4Test;

// Six AI seats play in-process on the fake Discord. When the first action phase begins each seat is handed a legendary
// planet, and the test follows the games until the seats have exhausted the abilities through the bot's real buttons.
class LegendarySelfPlayTest extends BaseTi4Test {

    private static final List<String> FACTIONS = List.of("hacan", "sol", "jolnar", "xxcha", "letnev", "yin");
    private static final Map<String, String> TILES =
            Map.of("mallice", "82b", "hopesend", "66", "primor", "65", "ordinian", "92", "emelpar", "99");
    private static final List<String> PLANETS = List.of("mallice", "hopesend", "primor", "ordinian", "emelpar");
    private static final Set<String> EXPECTED = Set.of("mallice", "hopesend", "primor", "ordinian");
    private static final Set<String> ANSWERS = Set.of("mallice", "placeOneNDone");
    private static final long VIRTUAL_DAY = 24 * 60 * 60 * 1000L;
    private static final int MAX_TICKS = 4000;
    private static final String ACTION = "action";

    @Test
    void seatsUseTheirLegendaryPlanetsThroughTheBotsButtons() throws Exception {
        Handout scenario = new Handout();
        SelfPlayGame.Outcome outcome = play(scenario);

        assertThat(outcome.ending()).as(outcome.report()).isEqualTo(SelfPlayGame.Ending.STOP_CONDITION);
        assertThat(scenario.used).as(outcome.report()).containsAll(EXPECTED);
        assertThat(scenario.handlers.keySet()).as(outcome.report()).containsAll(ANSWERS);
    }

    private static SelfPlayGame.Outcome play(Handout scenario) throws Exception {
        try (SelfPlayEnvironment env = new SelfPlayEnvironment(System.currentTimeMillis())) {
            SelfPlayGame selfPlay = new SelfPlayGame(
                    env, "ailegend" + ThreadLocalRandom.current().nextInt(100_000, 1_000_000));
            scenario.attach(env, selfPlay);
            try {
                selfPlay.setUp(FACTIONS);
                SelfPlayGame.Outcome outcome = selfPlay.run(scenario, VIRTUAL_DAY, MAX_TICKS);
                System.out.println(outcome.report());
                return outcome;
            } finally {
                AiRuntime.forget(selfPlay.name);
                GameManager.delete(selfPlay.name);
            }
        }
    }

    private static final class Handout implements Predicate<Game> {

        private SelfPlayEnvironment env;
        private SelfPlayGame selfPlay;
        private boolean handedOut;
        final Set<String> used = new HashSet<>();
        Map<String, Integer> handlers = Map.of();

        void attach(SelfPlayEnvironment env, SelfPlayGame selfPlay) {
            this.env = env;
            this.selfPlay = selfPlay;
        }

        @Override
        public boolean test(Game game) {
            if (!handedOut) {
                if (!ACTION.equalsIgnoreCase(game.getPhaseOfGame()) || game.getActivePlayer() == null) return false;
                handedOut = true;
                selfPlay.force(game, this::hand);
                return false;
            }
            for (Player seat : game.getRealPlayers()) used.addAll(seat.getExhaustedPlanetsAbilities());
            handlers = Map.copyOf(env.pressedHandlers);
            return done();
        }

        private boolean done() {
            return used.containsAll(EXPECTED) && handlers.keySet().containsAll(ANSWERS);
        }

        private void hand(Game game) {
            List<Player> seats = game.getRealPlayers();
            int free = 0;
            for (int i = 0; i < PLANETS.size() && i < seats.size(); i++) {
                String planet = PLANETS.get(i);
                if (game.getTileFromPlanet(planet) == null) {
                    Tile tile = new Tile(TILES.get(planet), freePosition(game, ++free));
                    game.setTile(tile);
                }
                AddPlanetService.addPlanet(seats.get(i), planet, game);
            }
        }

        private static String freePosition(Game game, int from) {
            int index = from;
            while (game.getTileByPosition("4" + String.format("%02d", index)) != null) index++;
            return "4" + String.format("%02d", index);
        }
    }
}
