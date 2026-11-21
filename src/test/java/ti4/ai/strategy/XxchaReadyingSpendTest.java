package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ExpectedReadying;
import ti4.ai.scoring.ScoringReserve;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Xxcha can ready planets with its agent Ggrocuto Rinn (1 planet) and its Thunder's Edge hero Xxekir Grom (4 planets),
// so a planet it spends now can be spent again before the status phase. With Archon's Gift the seat holds 10 influence
// on 4 planets and keeps 8 for Sway the Council: without readying it cannot buy a single Leadership token, with both
// leaders it spends its planets on 3 tokens and readies them again for the objective. The leaders are put on the Nekro
// test seat so the board matches DiplomacyRefundsLeadershipTest.
class XxchaReadyingSpendTest extends BaseTi4Test {

    private static final String AGENT = "xxchaagent";
    private static final String HERO = "xxchahero-te";

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

    private void giveReadyingLeaders() {
        seat.addLeader(AGENT);
        seat.addLeader(HERO);
        seat.unsafeGetLeader(HERO).setLocked(false);
    }

    private int tokensBought() {
        return TokenPurchase.best(test.game, seat, StrategyCardRules.reinforcements(test.game, seat))
                .map(TokenPurchase.Purchase::tokens)
                .orElse(0);
    }

    @Test
    void theAgentAndHeroReadyFivePlanetsBeforeTheStatusPhase() {
        giveReadyingLeaders();

        assertThat(ExpectedReadying.readiesBeforeStatus(test.game, seat))
                .isEqualTo(ExpectedReadying.XXCHA_AGENT_READIES + ExpectedReadying.XXEKIR_GROM_READIES);
    }

    @Test
    void aPassedSeatReadiesNothingWithItsLeaders() {
        giveReadyingLeaders();
        seat.setPassed(true);

        assertThat(ExpectedReadying.readiesBeforeStatus(test.game, seat)).isZero();
    }

    @Test
    void spendsEveryPlanetOnLeadershipWhenItsLeadersReadyThemForTheObjective() {
        assertThat(ScoringReserve.of(test.game, seat).influence()).isEqualTo(8);
        seat.addBreakthrough("xxchabt");
        seat.setBreakthroughUnlocked("xxchabt", true);
        int withoutLeaders = tokensBought();

        giveReadyingLeaders();

        assertThat(withoutLeaders).isZero();
        assertThat(tokensBought()).isEqualTo(TokenPurchase.MAX_TOKENS);
    }

    @Test
    void anExhaustedAgentReadiesNothing() {
        seat.addLeader(AGENT);
        seat.unsafeGetLeader(AGENT).setExhausted(true);

        assertThat(ExpectedReadying.readiesBeforeStatus(test.game, seat)).isZero();
        assertThat(tokensBought()).isZero();
    }
}
