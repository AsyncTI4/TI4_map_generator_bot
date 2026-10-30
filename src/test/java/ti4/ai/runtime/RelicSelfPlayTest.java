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
import ti4.helpers.Constants;
import ti4.helpers.RelicHelper;
import ti4.model.Mapper;
import ti4.service.planet.AddPlanetService;
import ti4.testUtils.BaseTi4Test;

// Six AI seats play in-process on the fake Discord. When the first action phase begins each seat is handed relics (and
// what they need: a planet for the Nano-Forge, a frontier token for the Circlet, discarded action cards for the Codex,
// three fragments of one kind), and the test follows the game until the seats have used them through the bot's
// real buttons.
class RelicSelfPlayTest extends BaseTi4Test {

    private static final List<String> FACTIONS = List.of("hacan", "sol", "jolnar", "xxcha", "letnev", "yin");
    private static final long VIRTUAL_DAY = 24 * 60 * 60 * 1000L;
    private static final int MAX_TICKS = 6000;
    private static final String ACTION = "action";
    private static final Set<String> EXPECTED_HANDLERS =
            Set.of("componentActionRes", "drawRelicFromFrag", "purge", "nanoforgePlanet", "codexCardPick");

    @Test
    void seatsUseTheirRelicsThroughTheBotsButtons() throws Exception {
        Handout scenario = new Handout();
        SelfPlayGame.Outcome outcome = play(scenario);

        assertThat(outcome.ending()).as(outcome.report()).isEqualTo(SelfPlayGame.Ending.STOP_CONDITION);
        assertThat(scenario.report()).as(outcome.report()).isEmpty();
        assertThat(scenario.handlers.keySet()).as(outcome.report()).containsAll(EXPECTED_HANDLERS);
    }

    private static SelfPlayGame.Outcome play(Handout scenario) throws Exception {
        try (SelfPlayEnvironment env = new SelfPlayEnvironment(System.currentTimeMillis())) {
            SelfPlayGame selfPlay = new SelfPlayGame(
                    env, "airelic" + ThreadLocalRandom.current().nextInt(100_000, 1_000_000));
            scenario.attach(env, selfPlay);
            try {
                selfPlay.setUp(FACTIONS);
                SelfPlayGame.Outcome outcome = selfPlay.run(scenario, VIRTUAL_DAY, MAX_TICKS);
                System.out.println(outcome.report());
                System.out.println("RELICS " + scenario.summary());
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
        private int techsBefore;
        private Player xxcha;
        private Player yin;
        private Player hacan;
        private Player sol;
        private Player jolnar;
        private final Set<String> gone = new HashSet<>();
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
            handlers = Map.copyOf(env.pressedHandlers);
            observe(game);
            return report().isEmpty();
        }

        private void observe(Game game) {
            Player hacan = seat(game.getRealPlayers(), "hacan");
            Player xxcha = seat(game.getRealPlayers(), "xxcha");
            if (!hacan.hasRelic("dynamiscore")) gone.add("dynamiscore");
            if (handlers.containsKey("jrStructure")) gone.add("titanprototype");
            if (handlers.containsKey("codexCardPick")) gone.add("codex");
            if (handlers.containsKey("nanoforgePlanet")) gone.add("nanoforge");
            if (handlers.containsKey("probeStep2")) gone.add("circletofthevoid");
            if (xxcha.getTechs().size() >= techsBefore + 2) gone.add("bookoflatvinia");
            if (handlers.containsKey("drawRelicFromFrag")) gone.add("fragments");
            if (handlers.containsKey("resolveCrownOfE")) gone.add("emphidia");
        }

        String report() {
            Set<String> missing = new HashSet<>(Set.of(
                    "dynamiscore",
                    "titanprototype",
                    "codex",
                    "nanoforge",
                    "circletofthevoid",
                    "bookoflatvinia",
                    "fragments",
                    "emphidia"));
            missing.removeAll(gone);
            return missing.isEmpty() ? "" : "not yet used: " + missing;
        }

        String summary() {
            return "used " + gone + " ; " + report();
        }

        private void hand(Game game) {
            List<Player> seats = game.getRealPlayers();
            hacan = seat(seats, "hacan");
            sol = seat(seats, "sol");
            jolnar = seat(seats, "jolnar");
            xxcha = seat(seats, "xxcha");
            yin = seat(seats, "yin");
            Player letnev = seat(seats, "letnev");

            hacan.addRelic("dynamiscore");
            hacan.addRelic("titanprototype");

            game.getDiscardActionCards().put("sabo1", 901);
            game.getDiscardActionCards().put("sabo2", 902);
            game.getDiscardActionCards().put("dh1", 903);
            sol.addRelic("codex");
            sol.addRelic("nanoforge");
            placePlanet(game, sol, "22", "tarmann");

            Tile frontier = new Tile("41", freePosition(game, 20));
            game.setTile(frontier);
            frontier.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);
            jolnar.addRelic("circletofthevoid");

            techsBefore = xxcha.getTechs().size();
            RelicHelper.gainRelicAndNotify(xxcha, selfPlay.ownerEvent(), game, "bookoflatvinia", "drew");

            letnev.addRelic("mawofworlds");
            letnev.addRelic("prophetstears");
            letnev.addRelic("emelpar");
            letnev.addRelic("thesilverflame");
            letnev.addRelic("heartofixth");
            hacan.addRelic("emphidia");
            String tomb = hacan.getPlanets().getFirst();
            game.getTileFromPlanet(tomb).getUnitHolders().get(tomb).addToken("attachment_tombofemphidia.png");
            yin.addFragment("crf1");
            yin.addFragment("crf2");
            yin.addFragment("crf3");
        }

        private static Player seat(List<Player> seats, String faction) {
            return seats.stream()
                    .filter(player -> faction.equals(player.getFaction()))
                    .findFirst()
                    .orElseThrow();
        }

        private static void placePlanet(Game game, Player seat, String tileId, String planet) {
            if (game.getTileFromPlanet(planet) == null) {
                game.setTile(new Tile(tileId, freePosition(game, 10)));
            }
            AddPlanetService.addPlanet(seat, planet, game);
        }

        private static String freePosition(Game game, int from) {
            int index = from;
            while (game.getTileByPosition("4" + String.format("%02d", index)) != null) index++;
            return "4" + String.format("%02d", index);
        }
    }
}
