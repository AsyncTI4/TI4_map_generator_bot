package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ExpectedReadying;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Xxcha can ready planets with its agent Ggrocuto Rinn (1 planet) and its Thunder's Edge hero Xxekir Grom (up to 4
// planets), so a planet it spends now can be spent again before the status phase. With Archon's Gift the seat holds 10
// influence on 4 planets (each at the higher of its resources and influence) and keeps 8 for Sway the Council: without
// readying it cannot buy a single Leadership token. The hero's readies only count when the hero would be played this
// round; on this board it has nothing to spend, score or defend, so only the agent's ready is planned on. The leaders
// are put on the Nekro test seat so the board matches DiplomacyRefundsLeadershipTest.
class XxchaReadyingSpendTest extends BaseTi4Test {

    private static final String AGENT = "xxchaagent";
    private static final String HERO = "xxchahero-te";
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
    void onlyTheAgentReadiesWhenTheHeroWouldNotBePlayed() {
        giveReadyingLeaders();

        assertThat(ExpectedReadying.readiesBeforeStatus(test.game, seat))
                .isEqualTo(ExpectedReadying.XXCHA_AGENT_READIES);
        assertThat(ExpectedReadying.spareReadies(test.game, seat)).isEqualTo(ExpectedReadying.XXCHA_AGENT_READIES);
    }

    @Test
    void aPassedSeatReadiesNothingWithItsLeaders() {
        giveReadyingLeaders();
        seat.setPassed(true);

        assertThat(ExpectedReadying.readiesBeforeStatus(test.game, seat)).isZero();
    }

    // An unlocked hero that would not be played adds no tokens over what the agent's single ready allows.
    @Test
    void anUnplayedHeroBuysNoMoreLeadershipTokensThanTheAgentAlone() {
        assertThat(ScoringReserve.of(test.game, seat).influence()).isEqualTo(8);
        seat.addBreakthrough("xxchabt");
        seat.setBreakthroughUnlocked("xxchabt", true);
        int withoutLeaders = tokensBought();
        seat.addLeader(AGENT);
        int withTheAgent = tokensBought();

        seat.addLeader(HERO);
        seat.unsafeGetLeader(HERO).setLocked(false);

        assertThat(withoutLeaders).isZero();
        assertThat(tokensBought()).isEqualTo(withTheAgent);
    }

    @Test
    void anExhaustedAgentReadiesNothing() {
        seat.addLeader(AGENT);
        seat.unsafeGetLeader(AGENT).setExhausted(true);

        assertThat(ExpectedReadying.readiesBeforeStatus(test.game, seat)).isZero();
        assertThat(tokensBought()).isZero();
    }

    // With XXCHA_LEADER_READIES off (and the other two split flags on) the seat plans as before 4b2988f8f6: its
    // leaders ready nothing, so the same board buys no Leadership tokens even with both leaders unlocked.
    @Test
    void withLeaderReadiesOffTheLeadersReadyNothingAndNoTokensAreBought() {
        assertThat(ScoringReserve.of(test.game, seat).influence()).isEqualTo(8);
        seat.addBreakthrough("xxchabt");
        seat.setBreakthroughUnlocked("xxchabt", true);
        giveReadyingLeaders();
        AiExperiments.disable(seat, Set.of(Experiment.XXCHA_LEADER_READIES));

        assertThat(AiExperiments.enabled(seat, Experiment.DIPLOMACY_READIED_PLANNING))
                .isTrue();
        assertThat(AiExperiments.enabled(seat, Experiment.TECH_FOLLOW_KEEP)).isTrue();
        assertThat(ExpectedReadying.readiesBeforeStatus(test.game, seat)).isZero();
        assertThat(ExpectedReadying.spareReadies(test.game, seat)).isZero();
        assertThat(ExpectedReadying.plannedSpareReadies(test.game, seat)).isZero();
        assertThat(tokensBought()).isZero();
    }

    // DIPLOMACY_READIED_PLANNING off (the other two on): the seat's own unplayed Diplomacy no longer lets the reserved
    // payment or the paid research plan on planets it readies, as before 4b2988f8f6, while the Leadership purchase
    // still counts the Diplomacy readies as it did before that commit.
    @Test
    void withDiplomacyPlanningOffOnlyTheLeadershipPurchaseCountsDiplomacy() {
        seat.addSC(DIPLOMACY);
        int tokensWithPlanning = tokensBought();
        Wallet wallet = Wallet.of(test.game, seat);
        Wallet.Payment payment = wallet.plan(SpendCost.resources(1)).orElseThrow();
        assertThat(ExpectedReadying.plannedSpareReadies(test.game, seat)).isEqualTo(ExpectedReadying.DIPLOMACY_READIES);
        assertThat(StrategyCardRules.readiedAgainCovers(test.game, seat, wallet, payment, 1))
                .isTrue();

        AiExperiments.disable(seat, Set.of(Experiment.DIPLOMACY_READIED_PLANNING));

        assertThat(AiExperiments.enabled(seat, Experiment.XXCHA_LEADER_READIES)).isTrue();
        assertThat(AiExperiments.enabled(seat, Experiment.TECH_FOLLOW_KEEP)).isTrue();
        assertThat(ExpectedReadying.spareReadies(test.game, seat)).isEqualTo(ExpectedReadying.DIPLOMACY_READIES);
        assertThat(ExpectedReadying.plannedSpareReadies(test.game, seat)).isZero();
        assertThat(StrategyCardRules.readiedAgainCovers(test.game, seat, wallet, payment, 1))
                .isFalse();
        SpendCost cost = SpendCost.influence(4);
        assertThat(ScoringReserve.planAfterReserve(test.game, seat, wallet, cost))
                .isEqualTo(ScoringReserve.planAfterReserve(wallet, ScoringReserve.of(test.game, seat), cost));
        assertThat(tokensBought()).isEqualTo(tokensWithPlanning).isEqualTo(2);
    }

    // With Diplomacy planning off, the leader readies are still planned on.
    @Test
    void withDiplomacyPlanningOffTheAgentReadyIsStillPlanned() {
        seat.addSC(DIPLOMACY);
        seat.addLeader(AGENT);
        AiExperiments.disable(seat, Set.of(Experiment.DIPLOMACY_READIED_PLANNING));

        assertThat(ExpectedReadying.plannedSpareReadies(test.game, seat))
                .isEqualTo(ExpectedReadying.XXCHA_AGENT_READIES);
    }
}
