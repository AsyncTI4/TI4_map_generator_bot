package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.eval.DecidingSeat;
import ti4.ai.eval.ImaginedTurn;
import ti4.ai.eval.MoveBoost;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

/**
 * A rival's imagined turn of a seat (ImaginedTurn.of) must not see that seat's secrets, and a seat-private valuation
 * (seatPrivate) must not use them either. Long-lived caches must keep the imagined, deciding-seat and movement "what
 * if" answers apart from the plain one.
 */
class TradeImaginedTurnTest extends BaseTi4Test {

    private static final String SOL_THRONE = "blue_sftt";
    private static final String SOL_ALLIANCE = "blue_an";
    private static final double EXACT = 1e-9;

    // Every official action-phase secret. Nekro on 9 is the sole leader and has a stack of three destroyers, so the
    // per-round odds add up to about 1.14: one action point while Nekro sees its own hand, none in an imagined turn.
    private static final List<String> ACTION_SECRETS =
            List.of("dyp", "btv", "uf", "sar", "pe", "mew", "fwp", "baf", "dts", "dtgs", "ttfd", "bam");

    // The NoteSecretsTest table: Nekro and Sol are neighbouring AI seats that each hold their own Throne and Alliance.
    private static TradeTable noteTable() {
        TradeTable table = TradeTable.withAiSol();
        table.game.setRound(3);
        table.nekroAndSolNeighbour();
        for (String suffix : List.of("_sftt", "_an")) {
            table.nekro.addOwnedPromissoryNoteByID("black" + suffix);
            table.nekro.setPromissoryNote("black" + suffix);
            table.sol.addOwnedPromissoryNoteByID("blue" + suffix);
            table.sol.setPromissoryNote("blue" + suffix);
        }
        table.stock(table.nekro, 3, 8);
        table.stock(table.sol, 4, 0);
        table.points(table.nekro, 3);
        table.points(table.sol, 3);
        return table;
    }

    // Finding 6: Strengthen Bonds is Nekro's own secret, so an imagined turn of Nekro must not chase it.
    @Test
    void anImaginedTurnDoesNotChaseStrengthenBonds() {
        TradeTable table = noteTable();
        Player nekro = table.nekro;
        nekro.setSecret(NoteSecrets.STRENGTHEN_BONDS);

        assertThat(ImaginedTurn.of(nekro, () -> NoteSecrets.value(table.game, nekro, SOL_ALLIANCE)))
                .isZero();
        assertThat(ImaginedTurn.of(nekro, () -> NoteSecrets.chases(table.game, nekro, SOL_ALLIANCE)))
                .isFalse();
        assertThat(NoteSecrets.value(table.game, nekro, SOL_ALLIANCE)).isPositive();
    }

    // Finding 6: the same for Betray a Friend.
    @Test
    void anImaginedTurnDoesNotChaseBetrayAFriend() {
        TradeTable table = noteTable();
        Player nekro = table.nekro;
        nekro.setSecret(NoteSecrets.BETRAY_A_FRIEND);

        assertThat(ImaginedTurn.of(nekro, () -> NoteSecrets.value(table.game, nekro, SOL_THRONE)))
                .isZero();
        assertThat(NoteSecrets.value(table.game, nekro, SOL_THRONE)).isPositive();
    }

    // Finding 6: a seat-private valuation of a received note leaves the receiver's secrets out, while the ordinary
    // valuation still adds most of a point for Strengthen Bonds.
    @Test
    void aSeatPrivateReceiveValueIgnoresTheReceiversNoteSecrets() {
        TradeTable table = noteTable();
        Player nekro = table.nekro;
        double privateBefore = NotesForTrade.receiveValue(table.game, nekro, SOL_ALLIANCE, true);
        double openBefore = NotesForTrade.receiveValue(table.game, nekro, SOL_ALLIANCE, false);

        nekro.setSecret(NoteSecrets.STRENGTHEN_BONDS);

        assertThat(NotesForTrade.receiveValue(table.game, nekro, SOL_ALLIANCE, true))
                .isCloseTo(privateBefore, within(EXACT));
        assertThat(NotesForTrade.receiveValue(table.game, nekro, SOL_ALLIANCE, false))
                .isGreaterThan(openBefore + 3);
    }

    // Nekro (Galactic Threat, a weak voter) values its own Political Secret back more while it holds a Rider. The
    // Rider is in Nekro's hidden hand, so an imagined turn of Nekro must value the note as if no Rider were held.
    @Test
    void anImaginedTurnDoesNotSeeAHeldRiderWhenValuingOwnPoliticalSecret() {
        TradeTable table = TradeTable.withAiSol();
        Player nekro = table.nekro;
        nekro.addOwnedPromissoryNoteByID("black_ps");
        nekro.setActionCard("lead_rider");

        double plain = NotesForTrade.receiveValue(table.game, nekro, "black_ps", false);
        double imagined =
                ImaginedTurn.of(nekro, () -> NotesForTrade.receiveValue(table.game, nekro, "black_ps", false));

        assertThat(imagined).isLessThan(plain);
    }

    // Finding 5: Nekro on 9 (goal 10) wins first only through an action secret in its own hand. Sol on 8 with
    // Nekro's Throne reaches 9 and has no way to its tenth point. An imagined turn of Nekro must not count the hand.
    @Test
    void anImaginedTurnDoesNotCountOwnActionSecretsInTheThroneRace() {
        TradeTable table = TradeTable.withAiSol();
        Player nekro = table.nekro;
        Player sol = table.sol;
        AiExperiments.disable(nekro, Set.of(Experiment.WIN_THREAT_FEASIBLE));
        table.points(nekro, 9);
        table.points(sol, 8);
        for (int ship = 0; ship < 3; ship++) table.presence(nekro, TradeTable.NEKRO_SPOT);
        ACTION_SECRETS.forEach(nekro::setSecret);

        assertThat(ImaginedTurn.of(nekro, () -> ThroneSale.winsFirst(table.game, nekro, sol, false, 0)))
                .isFalse();
        assertThat(ThroneSale.winsFirst(table.game, nekro, sol, false, 0)).isTrue();
    }

    // Board for the Ceasefire stakes: Nekro's empty home, a plain system beside it and a second plain system beyond
    // that, so the far system is exactly two moves from home.
    private record Corridor(AiTestGame test, Tile near, Tile far) {}

    private static Corridor corridor() {
        AiTestGame test = new AiTestGame();
        test.game.setVp(10);
        test.nekroHome();
        String near = AiTestGame.neighbourOf(AiTestGame.HOME);
        List<String> besideHome = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        String far = MapPositionMapper.getAdjacentTilePositions(near).stream()
                .filter(candidate ->
                        !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate) && !besideHome.contains(candidate))
                .findFirst()
                .orElseThrow();
        return new Corridor(test, test.place("19", near), test.place("20", far));
    }

    // A board position that touches none of the given positions.
    private static String islandAwayFrom(List<String> positions) {
        for (int ring = 1; ring <= 3; ring++) {
            for (int index = 1; index <= ring * 6; index++) {
                String candidate = ring + String.format("%02d", index);
                boolean touches = positions.stream()
                        .anyMatch(step -> step.equals(candidate)
                                || MapPositionMapper.getAdjacentTilePositions(step)
                                        .contains(candidate));
                if (!touches) return candidate;
            }
        }
        throw new IllegalStateException("no free position");
    }

    // Saar with Captain Mendosa: one dreadnought beside Nekro's home (4) and one two moves out (4). Mendosa lifts the
    // far one to the fastest ship on the board (a Nekro cruiser far away, move 2), but enemy shortcuts like Mendosa
    // are only assumed when the Saar move is imagined. Imagined, 8 resources reach the empty home (duress); plain,
    // only 4 do (no duress).
    private static Player saarWithMendosa(Corridor board) {
        AiTestGame test = board.test();
        Player saar = test.addSeat("300000000000000006", "saar", "orange");
        saar.setTacticalCC(3);
        saar.addLeader("saaragent");
        test.units(board.near(), "space", saar, UnitType.Dreadnought, 1);
        test.units(board.far(), "space", saar, UnitType.Dreadnought, 1);
        Tile island = test.place(
                "27",
                islandAwayFrom(List.of(
                        AiTestGame.HOME, board.near().getPosition(), board.far().getPosition())));
        test.units(island, "space", test.nekro, UnitType.Cruiser, 1);
        test.game.setLastModifiedDate(5);
        return saar;
    }

    // Finding 7: the plain stake is cached first; the imagined Saar move must not reuse it.
    @Test
    void anImaginedAttackerDoesNotReuseThePlainCeasefireStake() {
        Corridor board = corridor();
        Player saar = saarWithMendosa(board);
        AiTestGame test = board.test();

        assertThat(CeasefireValue.stakeAgainst(test.game, test.nekro, saar)).isZero();
        assertThat(ImaginedTurn.of(saar, () -> CeasefireValue.stakeAgainst(test.game, test.nekro, saar)))
                .isPositive();
    }

    // Finding 7, the reverse order: the imagined stake is cached first; the plain view must not reuse it.
    @Test
    void thePlainViewDoesNotReuseAnImaginedCeasefireStake() {
        Corridor board = corridor();
        Player saar = saarWithMendosa(board);
        AiTestGame test = board.test();

        assertThat(ImaginedTurn.of(saar, () -> CeasefireValue.stakeAgainst(test.game, test.nekro, saar)))
                .isPositive();
        assertThat(CeasefireValue.stakeAgainst(test.game, test.nekro, saar)).isZero();
    }

    // Finding 7: Nekro deciding imagines the Saar move (Mendosa assumed); Saar deciding for itself does not. Both
    // views ask the same defender and attacker, so the cache must keep them apart.
    @Test
    void theDefendersViewAndTheAttackersOwnViewKeepTheirOwnCeasefireStakes() {
        Corridor board = corridor();
        Player saar = saarWithMendosa(board);
        AiTestGame test = board.test();

        assertThat(DecidingSeat.of(saar, () -> CeasefireValue.stakeAgainst(test.game, test.nekro, saar)))
                .isZero();
        assertThat(DecidingSeat.of(test.nekro, () -> CeasefireValue.stakeAgainst(test.game, test.nekro, saar)))
                .isPositive();
        assertThat(DecidingSeat.of(saar, () -> CeasefireValue.stakeAgainst(test.game, test.nekro, saar)))
                .isZero();
    }

    // Finding 7, movement assumptions: two Sol dreadnoughts two moves out cannot reach Nekro's home, but with Flank
    // Speed assumed (MoveBoost) they can. The boosted stake must not be served to the plain question.
    @Test
    void anAssumedFlankSpeedDoesNotLeakIntoThePlainCeasefireStake() {
        Corridor board = corridor();
        AiTestGame test = board.test();
        test.units(board.far(), "space", test.sol, UnitType.Dreadnought, 2);
        test.game.setLastModifiedDate(6);

        assertThat(MoveBoost.withOneMore(test.sol, () -> CeasefireValue.stakeAgainst(test.game, test.nekro, test.sol)))
                .isPositive();
        assertThat(CeasefireValue.stakeAgainst(test.game, test.nekro, test.sol)).isZero();
        assertThat(MoveBoost.withOneMore(test.sol, () -> CeasefireValue.stakeAgainst(test.game, test.nekro, test.sol)))
                .isPositive();
    }
}
