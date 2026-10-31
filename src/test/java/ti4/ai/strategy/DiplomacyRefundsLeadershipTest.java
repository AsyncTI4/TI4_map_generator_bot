package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Live game aiplay1, round 2: Sardakk held Diplomacy and declined Leadership to keep its influence, then played
// Diplomacy with nothing to ready. Diplomacy readies 2 planets, so the 2 planets a Leadership purchase exhausts first
// come back before anything the AI keeps them for.
class DiplomacyRefundsLeadershipTest extends BaseTi4Test {

    private static final int DIPLOMACY = 2;

    private AiTestGame test;
    private Player seat;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        seat = test.nekro;
        seat.setTg(0);
        test.nekroHome();
        test.place("37", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.place("19", "101");
        test.place("25", "102");
        for (String planet : new String[] {"meer", "arinam", "wellon", "quann"}) seat.addPlanet(planet);
        test.game.getRevealedPublicObjectives().put("sway_council", 1);
    }

    private int tokensBought() {
        return TokenPurchase.best(test.game, seat, StrategyCardRules.reinforcements(test.game, seat))
                .map(TokenPurchase.Purchase::tokens)
                .orElse(0);
    }

    @Test
    void spendsThePlanetsItsOwnDiplomacyWillReady() {
        assertThat(ti4.ai.scoring.ScoringReserve.of(test.game, seat).influence())
                .isEqualTo(8);
        int withoutDiplomacy = tokensBought();

        seat.addSC(DIPLOMACY);

        assertThat(withoutDiplomacy).isZero();
        assertThat(tokensBought()).isEqualTo(2);
    }

    @Test
    void aPlayedDiplomacyReadiesNothingMore() {
        seat.addSC(DIPLOMACY);
        test.game.setSCPlayed(DIPLOMACY, true);

        assertThat(tokensBought()).isZero();
    }
}
