package ti4.helpers.thirtyk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
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
        sol = addPlayer("sol-id", "sol", "blue");
        hacan = addPlayer("hacan-id", "hacan", "green");
    }

    @Test
    void compoundInterestPaysDoubleOnceAndClears() {
        ThirtyKAcState.addCompoundInterest(game, sol, 3);

        assertThat(ThirtyKAcState.collectCompoundInterest(game, sol)).isEqualTo(6);
        assertThat(ThirtyKAcState.collectCompoundInterest(game, sol)).isZero();
        assertThat(ThirtyKAcState.collectCompoundInterest(game, hacan)).isZero();
    }

    @Test
    void warProfiteeringRemembersTargetAndSystem() {
        ThirtyKAcState.setWarProfiteering(game, sol, hacan, "201");

        assertThat(ThirtyKAcState.warProfiteering(game, sol))
                .isEqualTo(new ThirtyKAcState.WarProfiteering("hacan", "201"));
        ThirtyKAcState.clearWarProfiteering(game, sol);
        assertThat(ThirtyKAcState.warProfiteering(game, sol)).isNull();
    }

    @Test
    void purgedFragmentsCanBeTakenOneByOne() {
        ThirtyKAcState.setPurgedFragments(game, hacan, List.of("crf1", "crf2", "crf3"));
        ThirtyKAcState.removePurgedFragment(game, "crf2");

        ThirtyKAcState.PurgedFragments purged = ThirtyKAcState.purgedFragments(game);
        assertThat(purged.purgerFaction()).isEqualTo("hacan");
        assertThat(purged.fragments()).containsExactly("crf1", "crf3");
    }

    @Test
    void storedValuesAvoidTheSaveFormatSeparators() {
        ThirtyKAcState.setPlanetLoss(game, sol, "mr", hacan);
        ThirtyKAcState.setPurgedFragments(game, hacan, List.of("crf1"));
        ThirtyKAcState.setWarProfiteering(game, sol, hacan, "201");

        assertThat(game.getStoredValue(ThirtyKAcState.CONFLAGRATION + "sol")).doesNotContain(",", ":");
        assertThat(game.getStoredValue(ThirtyKAcState.PURGED_FRAGMENTS)).doesNotContain(",", ":");
        assertThat(ThirtyKAcState.planetLoss(game, sol)).isEqualTo(new ThirtyKAcState.PlanetLoss("mr", "hacan"));
    }

    @Test
    void cumulativeSupportIsCappedAtFour() {
        assertThat(ThirtyKAcState.cumulativeSupportInfantry(0)).isZero();
        assertThat(ThirtyKAcState.cumulativeSupportInfantry(3)).isEqualTo(3);
        assertThat(ThirtyKAcState.cumulativeSupportInfantry(7)).isEqualTo(4);
    }

    private Player addPlayer(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(faction);
        player.setColor(color);
        return player;
    }
}
