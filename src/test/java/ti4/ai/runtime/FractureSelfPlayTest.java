package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.GameManager;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.service.map.FractureService;
import ti4.testUtils.BaseTi4Test;

// Six AI seats play in-process on the fake Discord. At the start of the first action phase the Fracture enters play and
// the active seat finds a fleet beside one of its planets; the seat then plans the attack, moves into the Fracture and
// plays the combat against the neutral units to its end with the bot's real buttons: the neutral dice are rolled and
// the neutral hits assigned by another AI seat, and the planet is taken with its relic (and, for Styx, its point).
class FractureSelfPlayTest extends BaseTi4Test {

    private static final List<String> FACTIONS = List.of("hacan", "sol", "jolnar", "xxcha", "letnev", "yin");
    private static final long VIRTUAL_DAY = 24 * 60 * 60 * 1000L;
    private static final int MAX_TICKS = 1500;
    private static final String ACTION = "action";
    private static final int RELIC_PATIENCE_TICKS = 25;
    private static final String STYX = "styx";
    private static final String COCYTUS = "cocytus";

    @Test
    void aSeatTakesStyxFromTheNeutralUnitsAndScoresItsPoint() throws Exception {
        Attack scenario = new Attack("frac3", STYX, 8, 4, 16);
        SelfPlayGame.Outcome outcome = play(scenario);

        assertThat(outcome.ending()).as(outcome.report()).isEqualTo(SelfPlayGame.Ending.STOP_CONDITION);
        assertThat(scenario.taken).as(outcome.report()).isTrue();
        assertThat(scenario.pointsGained).as(outcome.report()).isEqualTo(1);
        assertThat(scenario.relicsGained).as(outcome.report()).isEqualTo(1);
    }

    // A fleet that could beat the guard of Styx marches on toward its point instead, so this one is only big enough for
    // Cocytus.
    @Test
    void aSeatTakesCocytusAndDrawsItsRelic() throws Exception {
        Attack scenario = new Attack("frac2", COCYTUS, 2, 1, 4);
        SelfPlayGame.Outcome outcome = play(scenario);

        assertThat(outcome.ending()).as(outcome.report()).isEqualTo(SelfPlayGame.Ending.STOP_CONDITION);
        assertThat(scenario.taken).as(outcome.report()).isTrue();
        assertThat(scenario.relicsGained).as(outcome.report()).isEqualTo(1);
    }

    private static SelfPlayGame.Outcome play(Attack scenario) throws Exception {
        try (SelfPlayEnvironment env = new SelfPlayEnvironment(System.currentTimeMillis())) {
            SelfPlayGame selfPlay =
                    new SelfPlayGame(env, "aifrac" + ThreadLocalRandom.current().nextInt(100_000, 1_000_000));
            scenario.attach(selfPlay);
            try {
                selfPlay.setUp(FACTIONS);
                SelfPlayGame.Outcome outcome = selfPlay.run(scenario, VIRTUAL_DAY, MAX_TICKS);
                System.out.println(outcome.report());
                System.out.println("Fracture scenario: " + scenario.lastSeen);
                return outcome;
            } finally {
                AiRuntime.forget(selfPlay.name);
                GameManager.delete(selfPlay.name);
            }
        }
    }

    private static final class Attack implements Predicate<Game> {

        private final String staging;
        private final String planet;
        private final int dreadnoughts;
        private final int carriers;
        private final int infantry;
        private SelfPlayGame selfPlay;
        private String attacker;
        private int pointsBefore;
        private int relicsBefore;
        boolean taken;
        private int waitingForRelic;
        String lastSeen = "not started";
        int pointsGained;
        int relicsGained;

        Attack(String staging, String planet, int dreadnoughts, int carriers, int infantry) {
            this.staging = staging;
            this.planet = planet;
            this.dreadnoughts = dreadnoughts;
            this.carriers = carriers;
            this.infantry = infantry;
        }

        void attach(SelfPlayGame selfPlay) {
            this.selfPlay = selfPlay;
        }

        @Override
        public boolean test(Game game) {
            if (attacker == null) {
                if (!ACTION.equalsIgnoreCase(game.getPhaseOfGame()) || game.getActivePlayer() == null) return false;
                attacker = game.getActivePlayer().getFaction();
                selfPlay.force(game, this::arrange);
                return false;
            }
            Player seat = game.getPlayerFromColorOrFaction(attacker);
            Player holder = game.getPlayerThatControlsPlanet(planet);
            lastSeen = "attacker " + attacker + ", holder " + (holder == null ? "nobody" : holder.getFaction())
                    + ", neutral units left " + neutralUnitsRemain(game) + ", relics "
                    + (seat.getRelics().size() - relicsBefore) + ", round " + game.getRound();
            if (holder != seat || neutralUnitsRemain(game)) return false;
            taken = true;
            pointsGained = seat.getTotalVictoryPoints() - pointsBefore;
            relicsGained = seat.getRelics().size() - relicsBefore;
            return relicsGained > 0 || ++waitingForRelic > RELIC_PATIENCE_TICKS;
        }

        private boolean neutralUnitsRemain(Game game) {
            Player neutral = game.getPlayerFromColorOrFaction("neutral");
            Tile tile = game.getTileFromPlanet(planet);
            return tile.getUnitHolders().values().stream()
                    .anyMatch(holder -> !holder.getUnitKeysForPlayer(neutral).isEmpty());
        }

        private void arrange(Game game) {
            FractureService.spawnFracture(null, game);
            Player seat = game.getPlayerFromColorOrFaction(attacker);
            Tile beside = game.getTileByPosition(staging);
            seat.setFleetCC(dreadnoughts + carriers + 2);
            beside.addUnit("space", Units.getUnitKey(UnitType.Dreadnought, seat.getColor()), dreadnoughts);
            beside.addUnit("space", Units.getUnitKey(UnitType.Carrier, seat.getColor()), carriers);
            beside.addUnit("space", Units.getUnitKey(UnitType.Infantry, seat.getColor()), infantry);
            pointsBefore = seat.getTotalVictoryPoints();
            relicsBefore = seat.getRelics().size();
        }
    }
}
