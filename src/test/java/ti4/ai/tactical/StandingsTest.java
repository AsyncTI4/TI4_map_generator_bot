package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ti4.ai.scoring.Footprint;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The scoreboard the offence reads: what each player can still score this round, how close they are to winning, and
// how much hurting them is worth.
class StandingsTest extends BaseTi4Test {

    private static final int IMPERIAL = 8;
    private static final String MECATOL_POSITION = "000";

    private DefenseScenario board;
    private Player sardakk;

    @BeforeEach
    void setUp() {
        board = new DefenseScenario();
        sardakk = board.sardakk;
        board.test.game.setVp(10);
    }

    private Standings standings() {
        return new DefenseOutlook(board.test.game, board.test.nekro).standings();
    }

    private void score(Player player, int points) {
        board.test.game.scorePublicObjective(player.getUserID(), board.test.game.addCustomPO("Test points", points));
    }

    private Tile placeMecatol() {
        return board.test.place("18", MECATOL_POSITION);
    }

    // Round one: nobody has scored, so no one is hurt more than another and a point denied is worth little.
    @Test
    void everyoneAtZeroIsWeighedEvenly() {
        assertThat(standings().hurtWeight(sardakk)).isCloseTo(1.0, within(1e-9));
        assertThat(standings().winThreat(sardakk)).isZero();
        assertThat(standings().denialWeight(sardakk)).isCloseTo(0.4, within(1e-9));
    }

    // A sole leader two points ahead is hurt more, up to the cap of 1.5.
    @Test
    void aSoleLeaderTwoPointsAheadIsHurtMore() {
        score(sardakk, 2);

        assertThat(standings().hurtWeight(sardakk)).isCloseTo(1.5, within(1e-9));
        assertThat(standings().denialWeight(sardakk)).isCloseTo(0.6, within(1e-9));
    }

    @Test
    void aPlayerBehindIsHurtLess() {
        score(board.test.nekro, 3);

        assertThat(standings().hurtWeight(sardakk)).isLessThan(1.0).isGreaterThanOrEqualTo(0.5);
    }

    // Test 4: eight of ten, the Imperial card unplayed, Mecatol Rex held and a spend objective it can pay (eight
    // influence from the planets it holds and a trade good) make a win this round, so a point denied counts double.
    @Test
    void aWinningPlayerCountsSpendObjectivesAndImperial() {
        placeMecatol();
        sardakk.addPlanet("mr");
        sardakk.setTg(2);
        sardakk.addSC(IMPERIAL);
        score(sardakk, 8);
        double withoutObjective = standings().winThreat(sardakk);

        board.reveal("sway_council");
        Standings standings = standings();

        assertThat(withoutObjective).isLessThan(1.0);
        assertThat(standings.winThreat(sardakk)).isCloseTo(1.0, within(1e-9));
        assertThat(standings.denialWeight(sardakk)).isCloseTo(2.0, within(1e-9));
        assertThat(standings.ready(sardakk)).contains("sway_council");
        assertThat(standings.publicScores(sardakk)).isEqualTo(2);
    }

    // Imperial's primary scores a second public objective, so two ready objectives are worth two points; one Imperial
    // that is already played adds nothing.
    @Test
    void imperialAddsASecondPublicScoreUntilItIsPlayed() {
        sardakk.setTg(8);
        board.reveal("sway_council");
        board.reveal("trade_routes");
        double single = standings().publicPoints(sardakk);

        sardakk.addSC(IMPERIAL);
        double withImperial = new DefenseOutlook(board.test.game, board.test.nekro)
                .standings()
                .publicPoints(sardakk);
        board.test.game.setSCPlayed(IMPERIAL, true);
        double played = new DefenseOutlook(board.test.game, board.test.nekro)
                .standings()
                .publicPoints(sardakk);

        assertThat(single).isEqualTo(1.0);
        assertThat(withImperial).isEqualTo(2.0);
        assertThat(played).isEqualTo(1.0);
    }

    @Test
    void thereIsAtMostOneImperialHolder() {
        assertThat(standings().imperialHolder()).isEmpty();

        sardakk.addSC(IMPERIAL);

        assertThat(new DefenseOutlook(board.test.game, board.test.nekro)
                        .standings()
                        .imperialHolder())
                .contains(sardakk);
    }

    // Test 22 (the standings part): the scoreboard is built from public counts and cards only.
    @Test
    void neverReadsAnotherPlayersHiddenCards() {
        board.reveal("sway_council");
        sardakk.setSecret("sar");
        sardakk.setActionCard("sabo1");
        sardakk.setPromissoryNote("black_ta", 17);
        Player spy = Mockito.spy(sardakk);
        board.test.game.getPlayers().put(spy.getUserID(), spy);
        Standings standings = new DefenseOutlook(board.test.game, board.test.nekro).standings();

        standings.winThreat(spy);
        standings.denialWeight(spy);
        standings.hurtWeight(spy);
        standings.publicPoints(spy);

        verify(spy, never()).getSecretsUnscored();
        verify(spy, never()).getSecrets();
        verify(spy, never()).getActionCards();
        verify(spy, never()).getPromissoryNotes();
    }

    // The objective model behind the denial terms: ships and planets that complete an objective, and the progress
    // losing them costs.
    @Test
    void losingTheShipsThatCompleteAnObjectiveBreaksItAndLosingSomeCostsProgress() {
        board.reveal("raise_fleet");
        Tile fleet = board.test.place("47", board.otherNeighbourOfHome());
        board.test.units(fleet, "space", sardakk, UnitType.Destroyer, 5);
        ObjectiveValue view = ObjectiveValue.publicOnly(board.test.game, sardakk);

        Set<String> broken = view.brokenReady(view.before().withoutShipsIn(fleet.getPosition()));

        assertThat(broken).containsExactly("raise_fleet");
        assertThat(view.progressLoss(view.before().withoutShipsIn(fleet.getPosition())))
                .isZero();
    }

    @Test
    void losingShipsOfAnUnfinishedObjectiveCostsProgressAndBreaksNothing() {
        board.reveal("raise_fleet");
        Tile fleet = board.test.place("47", board.otherNeighbourOfHome());
        board.test.units(fleet, "space", sardakk, UnitType.Destroyer, 3);
        ObjectiveValue view = ObjectiveValue.publicOnly(board.test.game, sardakk);

        Footprint lost = view.before().withoutShipsIn(fleet.getPosition());

        assertThat(view.brokenReady(lost)).isEmpty();
        assertThat(view.progressLoss(lost)).isGreaterThan(0);
        assertThat(view.progressLoss(view.before())).isZero();
    }

    @Test
    void anObjectiveOneShipAwayIsOneStepShort() {
        board.reveal("raise_fleet");
        Tile fleet = board.test.place("47", board.otherNeighbourOfHome());
        board.test.units(fleet, "space", sardakk, UnitType.Destroyer, 4);
        assertThat(ObjectiveValue.publicOnly(board.test.game, sardakk).oneStepShort())
                .isTrue();

        board.test.units(fleet, "space", sardakk, UnitType.Destroyer, 1);

        assertThat(ObjectiveValue.publicOnly(board.test.game, sardakk).oneStepShort())
                .isFalse();
    }
}
