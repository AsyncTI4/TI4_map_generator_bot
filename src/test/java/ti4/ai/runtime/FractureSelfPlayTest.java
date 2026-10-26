package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
    private static final int ATTEMPTS = 3;
    private static final List<String> FRACTURE_POSITIONS =
            List.of("frac1", "frac2", "frac3", "frac4", "frac5", "frac6", "frac7");
    private static final String SHARD_OF_THE_THRONE = "shard";

    @Test
    void aSeatTakesStyxFromTheNeutralUnitsAndScoresItsPoint() throws Exception {
        Played played = play(() -> new Attack("frac3", STYX, 8, 4, 16));
        Attack scenario = played.scenario();
        SelfPlayGame.Outcome outcome = played.outcome();

        assertThat(outcome.ending()).as(outcome.report()).isEqualTo(SelfPlayGame.Ending.STOP_CONDITION);
        assertThat(scenario.taken).as(outcome.report()).isTrue();
        assertThat(scenario.pointsGained).as(outcome.report()).isEqualTo(1);
        assertThat(scenario.relicsGained).as(outcome.report()).isEqualTo(1);
    }

    // From the egress Styx is two moves away, so a dreadnought fleet needs Flank Speed to strike this action.
    @Test
    void aSeatPlaysFlankSpeedToStrikeStyxFromTheEgress() throws Exception {
        Played played = play(() -> new Attack("frac2", STYX, 8, 4, 16).withFlankSpeed());
        Attack scenario = played.scenario();
        SelfPlayGame.Outcome outcome = played.outcome();

        assertThat(outcome.ending()).as(outcome.report()).isEqualTo(SelfPlayGame.Ending.STOP_CONDITION);
        assertThat(scenario.taken).as(outcome.report()).isTrue();
        assertThat(scenario.round).as(outcome.report()).isEqualTo(scenario.arrangedRound);
    }

    // A fleet that could beat the guard of Styx marches on toward its point instead, so this one is only big enough for
    // Cocytus.
    @Test
    void aSeatTakesCocytusAndDrawsItsRelic() throws Exception {
        Played played = play(() -> new Attack("frac2", COCYTUS, 2, 1, 4));
        Attack scenario = played.scenario();
        SelfPlayGame.Outcome outcome = played.outcome();

        assertThat(outcome.ending()).as(outcome.report()).isEqualTo(SelfPlayGame.Ending.STOP_CONDITION);
        assertThat(scenario.taken).as(outcome.report()).isTrue();
        assertThat(scenario.relicsGained).as(outcome.report()).isEqualTo(1);
    }

    private record Played(Attack scenario, SelfPlayGame.Outcome outcome) {}

    // The neutral dice are real, and a fleet that lands a handful of infantry can lose the battle on them (about one
    // game in ten for the Cocytus fleet). A scenario whose fleet died in the Fracture says nothing about the planning,
    // so it is played again on a fresh game; only a fleet that survives, or never attacks, is judged.
    private static final long CLOCK_START = 1_790_000_000_000L;

    private static Played play(Supplier<Attack> scenarios) throws Exception {
        Played played = playOnce(scenarios.get(), 0);
        for (int attempt = 1; attempt < ATTEMPTS && played.scenario().fleetLost; attempt++) {
            played = playOnce(scenarios.get(), attempt);
        }
        return played;
    }

    // The game name and clock start come from the seed as well (message ids and names feed the AI), so a failure
    // replays with -Dai.selfplay.seed=<printed seed minus the attempt>. Each retry gets the next seed.
    private static Played playOnce(Attack scenario, int attempt) throws Exception {
        long seed = SelfPlayGame.BASE_SEED + attempt;
        try (SelfPlayEnvironment env = new SelfPlayEnvironment(CLOCK_START)) {
            SelfPlayGame selfPlay = new SelfPlayGame(env, "aifrac" + Math.floorMod(seed, 1_000_000L), attempt, seed);
            scenario.attach(selfPlay);
            try {
                selfPlay.setUp(FACTIONS);
                SelfPlayGame.Outcome outcome = selfPlay.run(scenario, VIRTUAL_DAY, MAX_TICKS);
                System.out.println(outcome.report());
                System.out.println("Fracture scenario: " + scenario.lastSeen);
                return new Played(scenario, outcome);
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
        private boolean flankSpeed;
        int arrangedRound;
        int round;
        private SelfPlayGame selfPlay;
        private String attacker;
        private int pointsBefore;
        private int relicsBefore;
        boolean taken;
        boolean fleetLost;
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

        Attack withFlankSpeed() {
            flankSpeed = true;
            return this;
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
            if (holder != seat || neutralUnitsRemain(game)) {
                fleetLost = arrangedRound > 0 && !hasUnitsInTheFracture(game, seat) && isNoUnitInTransit(game);
                return fleetLost;
            }
            taken = true;
            round = game.getRound();
            pointsGained = seat.getTotalVictoryPoints() - pointsBefore - relicPoints(seat);
            relicsGained = seat.getRelics().size() - relicsBefore;
            return relicsGained > 0 || ++waitingForRelic > RELIC_PATIENCE_TICKS;
        }

        // Shard of the Throne gives its holder a point on top of the one the planet scores.
        private int relicPoints(Player seat) {
            return seat.getRelics().contains(SHARD_OF_THE_THRONE) ? 1 : 0;
        }

        private boolean hasUnitsInTheFracture(Game game, Player seat) {
            return FRACTURE_POSITIONS.stream()
                    .map(game::getTileByPosition)
                    .anyMatch(tile -> tile.getUnitHolders().values().stream()
                            .anyMatch(
                                    holder -> !holder.getUnitKeysForPlayer(seat).isEmpty()));
        }

        private boolean isNoUnitInTransit(Game game) {
            return game.getTacticalActionDisplacement().isEmpty();
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
            arrangedRound = game.getRound();
            if (flankSpeed) seat.setActionCard("fs1", 401);
        }
    }
}
