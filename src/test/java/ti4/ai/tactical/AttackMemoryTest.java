package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.ValueScale;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.IncomingForces.Horizon;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.GameSnapshots;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// ATTACK_MEMORY: a public ledger of who attacked whom (ships destroyed in combat, LRR 18; planets taken by invasion,
// LRR 49), diffed from the board between AI ticks. The ledger is a game stored value saved only when it changes, so
// /game undo rolls it back; the board picture it diffs against lives in memory per game and is retaken after an undo.
// Each round halves what an attack weighs and the ledger forgets after three rounds. A seat hits back at a recent
// attacker more readily, prices the lasting anger of the players it conquers from, and expects the players it angered
// to come for it.
class AttackMemoryTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;

    private static void control(Player seat) {
        AiExperiments.disable(seat, EnumSet.of(Experiment.ATTACK_MEMORY));
    }

    private static double cost(Player player, UnitType type) {
        return player.getUnitByType(type).getCost();
    }

    private static void remove(Tile tile, String holder, Player player, UnitType type, int count) {
        tile.removeUnit(holder, Units.getUnitKey(type, player.getColor()), count);
    }

    private static void entry(Game game, int round, Player attacker, Player victim, double damage) {
        String previous = game.getStoredValue(AttackLedger.LEDGER_KEY);
        String added = round + "/" + attacker.getFaction() + "/" + victim.getFaction() + "=" + damage;
        game.setStoredValue(
                AttackLedger.LEDGER_KEY, previous.isEmpty() || "-".equals(previous) ? added : previous + ";" + added);
    }

    // Wellon (tile 19) beside the Sardakk home, held by Sardakk with one infantry.
    private static final class Invasion {
        final DefenseScenario board = new DefenseScenario();
        final Game game = board.test.game;
        final Player nekro = board.test.nekro;
        final Player sardakk = board.sardakk;
        final Tile wellon = board.test.place("19", AiTestGame.neighbourOf(DefenseScenario.SARDAKK_HOME_POSITION));

        Invasion() {
            game.newGameSetup();
            sardakk.addPlanet("wellon");
            board.test.units(wellon, "wellon", sardakk, UnitType.Infantry, 1);
        }

        void nekroTakesWellon() {
            takeWellon(game);
        }

        static void takeWellon(Game game) {
            Player nekro = game.getPlayerFromColorOrFaction("nekro");
            Player sardakk = game.getPlayerFromColorOrFaction("sardakk");
            Tile wellon = game.getTileFromPlanet("wellon");
            game.setPhaseOfGame("action");
            game.setActivePlayerID(nekro.getUserID());
            remove(wellon, "wellon", sardakk, UnitType.Infantry, 1);
            sardakk.removePlanet("wellon");
            nekro.addPlanet("wellon");
            wellon.addUnit("wellon", Units.getUnitKey(UnitType.Infantry, nekro.getColor()), 2);
        }

        double expectedDamage() {
            double location = Grievance.location(game, wellon, sardakk);
            Planet planet = (Planet) wellon.getUnitHolders().get("wellon");
            return (BoardView.planetValue(planet) + ValueScale.RESOURCE * cost(sardakk, UnitType.Infantry)) * location;
        }
    }

    @Test
    void theLedgerRecordsAPlanetTakenAndTheGroundForcesKilledThere() {
        Invasion invasion = new Invasion();
        AttackLedger.record(invasion.game);
        invasion.nekroTakesWellon();

        AttackLedger.record(invasion.game);

        assertThat(AttackLedger.grudge(invasion.game, invasion.sardakk, invasion.nekro))
                .isCloseTo(invasion.expectedDamage(), within(0.01));
        assertThat(AttackLedger.grudge(invasion.game, invasion.nekro, invasion.sardakk))
                .isZero();
    }

    // The first look at a game only takes a picture of the board: nothing before it is an attack.
    @Test
    void theFirstLookOnlyRemembersTheBoard() {
        Invasion invasion = new Invasion();
        invasion.nekroTakesWellon();

        AttackLedger.record(invasion.game);

        assertThat(AttackLedger.grudge(invasion.game, invasion.sardakk, invasion.nekro))
                .isZero();
    }

    // Ships destroyed in a space combat the active player started (LRR 18.1: combat happens in the active system).
    @Test
    void theLedgerRecordsShipsTheActivePlayerDestroyed() {
        DefenseScenario board = new DefenseScenario();
        board.raidingFleet(0, 2, 0);
        AttackLedger.record(board.test.game);
        board.test.isActive(board.test.nekro, "action");
        board.test.units(board.staging, "space", board.test.nekro, UnitType.Dreadnought, 2);
        remove(board.staging, "space", board.sardakk, UnitType.Cruiser, 2);

        AttackLedger.record(board.test.game);

        double location = Grievance.location(board.test.game, board.staging, board.sardakk);
        assertThat(AttackLedger.grudge(board.test.game, board.sardakk, board.test.nekro))
                .isCloseTo(2 * cost(board.sardakk, UnitType.Cruiser) * ValueScale.RESOURCE * location, within(0.01));
    }

    // A retreat (LRR 18.6) moves ships away rather than destroying them, and the active player's own losses are not an
    // attack on them.
    @Test
    void retreatsAndTheAttackersOwnLossesAreNotAttacks() {
        DefenseScenario board = new DefenseScenario();
        board.raidingFleet(0, 2, 0);
        board.test.units(board.staging, "space", board.test.nekro, UnitType.Destroyer, 1);
        Tile refuge = board.test.place("47", board.beyondStaging());
        AttackLedger.record(board.test.game);
        board.test.isActive(board.test.nekro, "action");
        board.test.game.setActiveSystem(board.stagingPosition);
        remove(board.staging, "space", board.sardakk, UnitType.Cruiser, 2);
        board.test.units(refuge, "space", board.sardakk, UnitType.Cruiser, 2);
        remove(board.staging, "space", board.test.nekro, UnitType.Destroyer, 1);

        AttackLedger.record(board.test.game);

        assertThat(AttackLedger.grudge(board.test.game, board.sardakk, board.test.nekro))
                .isZero();
        assertThat(AttackLedger.grudge(board.test.game, board.test.nekro, board.sardakk))
                .isZero();
    }

    // The bot reloads the game from its save file on every tick, so two ticks never share a Game instance: the second
    // tick must still diff against the picture the first one took.
    @Test
    void aFreshlyLoadedGameSeesTheLastTicksPicture() {
        Invasion invasion = new Invasion();
        AttackLedger.record(GameSnapshots.copy(invasion.game));
        invasion.nekroTakesWellon();
        Game nextTick = GameSnapshots.copy(invasion.game);

        AttackLedger.record(nextTick);

        assertThat(AttackLedger.grudge(nextTick, sardakkOf(nextTick), nekroOf(nextTick)))
                .isCloseTo(invasion.expectedDamage(), within(0.01));
        Game reloaded = GameSnapshots.copy(nextTick);
        assertThat(AttackLedger.grudge(reloaded, sardakkOf(reloaded), nekroOf(reloaded)))
                .isCloseTo(invasion.expectedDamage(), within(0.01));
    }

    // /game undo restores an older save file. Its ledger no longer matches the picture, so the AI retakes the picture
    // instead of reading the undone invasion backwards, and replaying the invasion records it once.
    @Test
    void theLedgerRollsBackWithUndo() {
        Invasion invasion = new Invasion();
        AttackLedger.record(invasion.game);
        Game undoFile = GameSnapshots.copy(invasion.game);
        invasion.nekroTakesWellon();
        Game attacked = GameSnapshots.copy(invasion.game);
        AttackLedger.record(attacked);
        double recorded = AttackLedger.grudge(attacked, sardakkOf(attacked), nekroOf(attacked));

        Game undone = GameSnapshots.copy(undoFile);
        AttackLedger.record(undone);
        double afterUndo = AttackLedger.grudge(undone, sardakkOf(undone), nekroOf(undone));
        double reverse = AttackLedger.grudge(undone, nekroOf(undone), sardakkOf(undone));
        Invasion.takeWellon(undone);
        AttackLedger.record(undone);
        AttackLedger.record(undone);

        assertThat(recorded).isPositive();
        assertThat(afterUndo).isZero();
        assertThat(reverse).isZero();
        assertThat(AttackLedger.grudge(undone, sardakkOf(undone), nekroOf(undone)))
                .isCloseTo(recorded, within(EXACT));
    }

    // A tick that sees nothing new writes nothing, so the bot does not save and add an undo step after every action.
    @Test
    void aQuietTickLeavesTheSaveAlone() {
        Invasion invasion = new Invasion();
        AttackLedger.record(invasion.game);
        AttackLedger.record(invasion.game);

        assertThat(invasion.game.getStoredValue(AttackLedger.LEDGER_KEY)).isEmpty();
    }

    // The AI may only tick once the victim's own turn has started (end of turn clears the active system), and the
    // victim's losses from the attack still count.
    @Test
    void theVictimsLossesCountAfterItsTurnStarts() {
        DefenseScenario board = new DefenseScenario();
        board.raidingFleet(0, 2, 0);
        AttackLedger.record(board.test.game);
        board.test.units(board.staging, "space", board.test.nekro, UnitType.Dreadnought, 2);
        remove(board.staging, "space", board.sardakk, UnitType.Cruiser, 2);
        board.test.isActive(board.sardakk, "action");
        board.test.game.setActiveSystem("");

        AttackLedger.record(board.test.game);

        assertThat(AttackLedger.grudge(board.test.game, board.sardakk, board.test.nekro))
                .isPositive();
    }

    private static Player nekroOf(Game game) {
        return game.getPlayerFromColorOrFaction("nekro");
    }

    private static Player sardakkOf(Game game) {
        return game.getPlayerFromColorOrFaction("sardakk");
    }

    @Test
    void anAttackHalvesEachRoundAndIsForgottenAfterThree() {
        AiTestGame test = new AiTestGame();
        test.game.setRound(4);
        entry(test.game, 4, test.sol, test.nekro, 8);

        double fresh = AttackLedger.grudge(test.game, test.nekro, test.sol);
        test.game.setRound(5);
        double lastRound = AttackLedger.grudge(test.game, test.nekro, test.sol);
        test.game.setRound(7);
        double old = AttackLedger.grudge(test.game, test.nekro, test.sol);
        AttackLedger.record(test.game);

        assertThat(fresh).isEqualTo(8);
        assertThat(lastRound).isEqualTo(4);
        assertThat(old).isZero();
        assertThat(AttackLedger.attacks(test.game)).isEmpty();
    }

    // A seat that lost a VP's worth of plastic to Sardakk weighs hurting Sardakk a quarter more, the same step a one
    // point lead adds; the weight stays inside its cap.
    @Test
    void aSeatWeighsHurtingARecentAttackerMore() {
        DefenseScenario board = new DefenseScenario();
        Standings calm = new Standings(board.test.game, board.test.nekro, null);
        double before = calm.hurtWeight(board.sardakk);
        entry(board.test.game, board.test.game.getRound(), board.sardakk, board.test.nekro, 8);

        double after = new Standings(board.test.game, board.test.nekro, null).hurtWeight(board.sardakk);
        entry(board.test.game, board.test.game.getRound(), board.sardakk, board.test.nekro, 80);
        double capped = new Standings(board.test.game, board.test.nekro, null).hurtWeight(board.sardakk);

        assertThat(after - before).isCloseTo(0.25, within(EXACT));
        assertThat(capped).isEqualTo(1.5);
    }

    // Nekro's dreadnoughts beside a Sardakk cruiser: the strike is worth more against a player who just attacked Nekro.
    private static double strikeAtStaging(boolean grudge, boolean control) {
        DefenseScenario board = new DefenseScenario();
        if (control) control(board.test.nekro);
        board.raidingFleet(0, 1, 0);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Dreadnought, 2);
        if (grudge) entry(board.test.game, board.test.game.getRound(), board.sardakk, board.test.nekro, 8);
        return TacticalPlanner.explainAttack(board.test.game, board.test.nekro, board.stagingPosition)
                .orElseThrow()
                .score();
    }

    @Test
    void aSeatStrikesBackMoreReadilyAtARecentAttacker() {
        assertThat(strikeAtStaging(true, false)).isGreaterThan(strikeAtStaging(false, false));
    }

    @Test
    void aControlSeatIgnoresTheLedgerWhenStriking() {
        assertThat(strikeAtStaging(true, true)).isEqualTo(strikeAtStaging(false, true));
    }

    // Nekro takes Mecatol Rex from Sardakk. Sardakk's free fleet either waits beside Nekro's home, or is one cruiser at
    // their own far home.
    private static final class RexRaid {
        final DefenseScenario board = new DefenseScenario();

        RexRaid() {
            board.sardakk.setTacticalCC(3);
            Tile rex = board.test.place("18", "000");
            board.sardakk.addPlanet("mr");
            board.test.units(rex, "mr", board.sardakk, UnitType.Infantry, 1);
            Tile launch = board.test.place("47", AiTestGame.neighbourOf("000"));
            board.test.units(launch, "space", board.test.nekro, UnitType.Carrier, 1);
            board.test.units(launch, "space", board.test.nekro, UnitType.Cruiser, 2);
            board.test.units(launch, "space", board.test.nekro, UnitType.Infantry, 2);
        }

        RexRaid strongNeighbour() {
            board.raidingFleet(1, 2, 4);
            board.test.units(board.staging, "space", board.sardakk, UnitType.Dreadnought, 2);
            return this;
        }

        RexRaid weakAndFar() {
            Tile sardakkHome = board.test.game.getTileByPosition(DefenseScenario.SARDAKK_HOME_POSITION);
            board.test.units(sardakkHome, "space", board.sardakk, UnitType.Cruiser, 1);
            return this;
        }

        RexRaid control() {
            AttackMemoryTest.control(board.test.nekro);
            return this;
        }

        RexRaid grudge(double damage) {
            entry(board.test.game, board.test.game.getRound(), board.sardakk, board.test.nekro, damage);
            entry(board.test.game, board.test.game.getRound(), board.test.nekro, board.sardakk, damage);
            return this;
        }

        double anger() {
            return TacticalPlanner.explainAttack(board.test.game, board.test.nekro, "000")
                    .orElseThrow()
                    .anger();
        }
    }

    @Test
    void takingAPlanetFromAStrongNeighbourCostsMoreThanFromAWeakFarOne() {
        double strong = new RexRaid().strongNeighbour().anger();
        double weak = new RexRaid().weakAndFar().anger();

        assertThat(strong).isPositive();
        assertThat(weak).isGreaterThanOrEqualTo(0).isLessThan(strong);
    }

    // The control seat keeps the old anger term, whatever the ledger says.
    @Test
    void aControlSeatKeepsTheOldAngerTerm() {
        double plain = new RexRaid().strongNeighbour().control().anger();
        double remembered = new RexRaid().strongNeighbour().control().grudge(8).anger();
        double treatment = new RexRaid().strongNeighbour().anger();

        assertThat(remembered).isEqualTo(plain);
        assertThat(treatment).isGreaterThanOrEqualTo(plain);
    }

    // Remembering attacks only ever adds the victim's lasting anger on top of the near-term term: conquest never gets
    // cheaper with the flag on.
    @Test
    void conquestAngerWithTheFlagOnIsAtLeastTheOldValue() {
        assertThat(new RexRaid().strongNeighbour().anger())
                .isGreaterThanOrEqualTo(
                        new RexRaid().strongNeighbour().control().anger());
        assertThat(new RexRaid().weakAndFar().anger())
                .isGreaterThanOrEqualTo(new RexRaid().weakAndFar().control().anger());
        assertThat(new RexRaid().strongNeighbour().grudge(8).anger())
                .isGreaterThanOrEqualTo(
                        new RexRaid().strongNeighbour().grudge(8).control().anger());
    }

    // Sardakk's fleet waits beside Nekro's home. After Nekro took a VP's worth from Sardakk, Nekro expects Sardakk to
    // come for it more; a control seat does not.
    private static double sardakkRaidFear(boolean attackedSardakk, boolean control) {
        DefenseScenario board = new DefenseScenario();
        if (control) control(board.test.nekro);
        board.raidingFleet(1, 1, 2);
        if (attackedSardakk) {
            entry(board.test.game, board.test.game.getRound(), board.test.nekro, board.sardakk, 8);
        }
        DefenseOutlook outlook = new DefenseOutlook(board.test.game, board.test.nekro);
        return outlook.fear(board.sardakk, board.home, Horizon.IMMEDIATE, board.defenders());
    }

    @Test
    void aRivalIsExpectedToStrikeAtWhoeverAttackedThem() {
        double calm = sardakkRaidFear(false, false);
        double angry = sardakkRaidFear(true, false);

        assertThat(calm).isPositive();
        assertThat(angry).isGreaterThan(calm);
        assertThat(sardakkRaidFear(true, true)).isEqualTo(sardakkRaidFear(false, true));
    }

    @Test
    void angerScalesWithTheHurtWeightItAdds() {
        AiTestGame test = new AiTestGame();

        assertThat(Standings.angerRatio(test.game, test.nekro, 0)).isEqualTo(1);
        assertThat(Standings.angerRatio(test.game, test.nekro, ObjectiveValue.VICTORY_POINT_VALUE))
                .isCloseTo(1.25, within(EXACT));
    }
}
