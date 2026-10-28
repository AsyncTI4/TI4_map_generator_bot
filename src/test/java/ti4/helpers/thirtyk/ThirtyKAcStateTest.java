package ti4.helpers.thirtyk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.thirtyk.ThirtyKAcState.ProfiteeringResult;
import ti4.testUtils.BaseTi4Test;

/** Stored-value state the 30k action cards keep between playing a card and its payout. */
class ThirtyKAcStateTest extends BaseTi4Test {

    private Game game;
    private Player sol;
    private Player hacan;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("thirtyk-ac-state-test");
        game.setAcDeckID("action_cards_30k");
        sol = addPlayer("sol-id", "sol", "blue");
        hacan = addPlayer("hacan-id", "hacan", "green");
    }

    @Test
    void compoundInterestPaysDoubleOnce() {
        ThirtyKAcState.addCompoundInterest(game, sol, 3);

        assertThat(ThirtyKAcState.collectCompoundInterest(game, sol)).isEqualTo(6);
        assertThat(ThirtyKAcState.collectCompoundInterest(game, sol)).isZero();
    }

    @Test
    void warProfiteeringPaysOnlyWhenTheBackedPlayerIsTheLastWithShips() {
        assertThat(ThirtyKAcState.profiteeringResult("hacan", List.of("hacan"))).isEqualTo(ProfiteeringResult.WON);
        assertThat(ThirtyKAcState.profiteeringResult("hacan", List.of("sol", "hacan")))
                .isEqualTo(ProfiteeringResult.PENDING);
        assertThat(ThirtyKAcState.profiteeringResult("hacan", List.of())).isEqualTo(ProfiteeringResult.LOST);
    }

    // Everything a card leaves open for "this turn" is gone after end of turn, so stale bets or windows
    // can never pay out in a later turn. The stored values must also survive the save format (no , or :).
    @Test
    void endOfTurnClearsTurnState() {
        ThirtyKAcState.setWarProfiteering(game, sol, hacan, "201");
        ThirtyKAcState.setExtendedCampaigning(game, sol, hacan);
        // Saves from before the paged target lists still carry one-shot choice keys.
        game.setStoredValue("thkOneShotthkAcFoldingStartsol", "open");
        ThirtyKAcState.setFinalStand(game, sol, "mr");
        assertThat(game.getStoredValueMap().values()).noneMatch(value -> value.contains(",") || value.contains(":"));

        ThirtyKTurnState.clearEndOfTurn(game);

        assertThat(ThirtyKAcState.warProfiteering(game, sol)).isNull();
        assertThat(ThirtyKAcState.takeExtendedCampaigning(game, sol, hacan)).isFalse();
        assertThat(game.getStoredValue("thkOneShotthkAcFoldingStartsol")).isEmpty();
        assertThat(ThirtyKAcState.hasFinalStand(game, sol, "mr")).isFalse();
    }

    // Conflagration and Antidisestablishmentarianism are answered by a player who is not active, often after
    // the active player's turn has ended, so their windows last until the next strategy phase.
    @Test
    void reactionWindowsOutliveTheTurnButNotTheActionPhase() {
        ThirtyKAcState.setPlanetLoss(game, sol, "mr", hacan);
        ThirtyKAcState.setStructureLost(game, hacan, "201");

        ThirtyKTurnState.clearEndOfTurn(game);
        assertThat(ThirtyKAcState.planetLoss(game, sol)).isNotNull();
        assertThat(ThirtyKAcState.structureLoss(game)).isNotNull();

        ThirtyKTurnState.clearActionPhase(game);
        assertThat(ThirtyKAcState.planetLoss(game, sol)).isNull();
        assertThat(ThirtyKAcState.structureLoss(game)).isNull();
    }

    @Test
    void finalStandAppliesOnlyToTheChosenPlanet() {
        ThirtyKAcState.setFinalStand(game, sol, "mr");
        assertThat(ThirtyKAcState.hasFinalStand(game, sol, "mr")).isTrue();
        assertThat(ThirtyKAcState.hasFinalStand(game, sol, "jord")).isFalse();
        assertThat(ThirtyKAcState.hasFinalStand(game, hacan, "mr")).isFalse();
    }

    // Sol plays Dummy Rider on "for" and hedges with Objection Gambit on "against"; Hacan follows on "for".
    // When "for" loses, each wrong entry returns exactly its own card, once.
    @Test
    void wrongDummyRiderReturnsOnlyTheCardOnThatOutcome() {
        ThirtyKAcState.setDummyRider(game, sol, "For");
        ThirtyKAcState.addDummyRiderCopy(game, sol, "against", "objectiongambit30k");
        ThirtyKAcState.addDummyRiderCopy(game, hacan, "for", "hidewithinthefineprint30k");

        assertThat(ThirtyKAcState.takeWrongDummyRiderCard(game, sol, "for")).isEqualTo("dummyrider30k");
        assertThat(ThirtyKAcState.takeWrongDummyRiderCard(game, sol, "for")).isNull();
        assertThat(ThirtyKAcState.takeWrongDummyRiderCard(game, hacan, "for")).isEqualTo("hidewithinthefineprint30k");
        assertThat(ThirtyKAcState.takeWrongDummyRiderCard(game, sol, "against")).isEqualTo("objectiongambit30k");
    }

    // A vetoed or discarded agenda never resolves; the next agenda's vote reset must drop rider state,
    // and only a played 30k card (not the legacy card with the same name) arms the rider hooks.
    @Test
    void riderStateIsPerCardAndClearsWithTheAgenda() {
        ThirtyKAcState.setDummyRider(game, sol, "for");
        ThirtyKAcState.markRiderCard(game, sol, "Public Outcry");
        assertThat(ThirtyKAcState.playedRiderCard(game, sol, "Public Outcry")).isTrue();
        assertThat(ThirtyKAcState.playedRiderCard(game, sol, "Dummy Rider")).isFalse();
        assertThat(ThirtyKAcState.playedRiderCard(game, hacan, "Public Outcry")).isFalse();

        ThirtyKActionCardHooks.onAgendaVotesReset(game);

        assertThat(ThirtyKAcState.takeWrongDummyRiderCard(game, sol, "for")).isNull();
        assertThat(ThirtyKAcState.playedRiderCard(game, sol, "Public Outcry")).isFalse();
    }

    private Player addPlayer(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(faction);
        player.setColor(color);
        return player;
    }
}
