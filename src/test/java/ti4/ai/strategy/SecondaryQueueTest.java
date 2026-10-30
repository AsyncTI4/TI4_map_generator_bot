package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class SecondaryQueueTest extends BaseTi4Test {

    private static final int IMPERIAL = 8;
    private static final String HUMAN = "7000000000000001";

    private AiTestGame test;
    private Player human;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        human = test.addSeat(HUMAN, "hacan", "green");
        test.game.setSpeaker(test.sol);
        test.sol.addSC(IMPERIAL);
        test.game.setSCPlayed(IMPERIAL, true);
        test.nekro.setStrategicCC(2);
        test.isActive(test.sol, "action");
    }

    private static AiPrompt imperialCard() {
        return prompt("imperial", PromptSource.PUBLIC, NOW, "sc_follow_8", "sc_no_follow_8", "sc_draw_so");
    }

    private AiTurnContext nekroContext() {
        return test.context(imperialCard());
    }

    // Follow order from the holder (sol) is hacan, then nekro.
    @Test
    void nekroFollowsOnlyAfterTheHoldersPrimaryResolved() {
        human.addFollowedSC(IMPERIAL);
        SecondaryQueue.primaryStarted(test.game, IMPERIAL, NOW);

        assertThat(StrategyCardRules.follow(nekroContext())).isEmpty();
        assertThat(StrategyCardRules.awaitFollowTurn(nekroContext())).containsInstanceOf(AiDecision.Wait.class);

        SecondaryQueue.primaryResolved(test.game, IMPERIAL);

        assertThat(StrategyCardRules.follow(nekroContext()).map(AiTestGame::pressedId))
                .contains("sc_draw_so");
        assertThat(StrategyCardRules.awaitFollowTurn(nekroContext())).isEmpty();
    }

    @Test
    void waitsForAnEarlierPlayerWhoHasNotFollowedYet() {
        assertThat(SecondaryQueue.blockerFor(test.game, test.nekro, IMPERIAL, NOW))
                .isNotEmpty();

        human.addFollowedSC(IMPERIAL);

        assertThat(SecondaryQueue.blockerFor(test.game, test.nekro, IMPERIAL, NOW))
                .isEmpty();
    }

    @Test
    void waitsForAnEarlierPlayerStillResolvingTheirFollow() {
        human.addFollowedSC(IMPERIAL);
        SecondaryQueue.followStarted(test.game, human, IMPERIAL, NOW);

        assertThat(SecondaryQueue.blockerFor(test.game, test.nekro, IMPERIAL, NOW))
                .isNotEmpty();

        SecondaryQueue.followFinished(test.game, human, IMPERIAL);

        assertThat(SecondaryQueue.blockerFor(test.game, test.nekro, IMPERIAL, NOW))
                .isEmpty();
    }

    @Test
    void theFirstFollowerNeverWaitsForThoseBehindIt() {
        assertThat(SecondaryQueue.blockerFor(test.game, human, IMPERIAL, NOW)).isEmpty();
    }

    @Test
    void doesNotDeclineBeforeItsTurn() {
        assertThat(StrategyCardRules.awaitFollowTurn(nekroContext())).isPresent();

        human.addFollowedSC(IMPERIAL);

        assertThat(StrategyCardRules.awaitFollowTurn(nekroContext())).isEmpty();
    }

    @Test
    void anAbandonedStepStopsBlockingAfterTenMinutes() {
        human.addFollowedSC(IMPERIAL);
        SecondaryQueue.primaryStarted(test.game, IMPERIAL, NOW);

        assertThat(SecondaryQueue.blockerFor(test.game, test.nekro, IMPERIAL, NOW + 11 * 60_000L))
                .isEmpty();
    }

    @Test
    void theHolderWaitsUntilEveryoneElseFollowedOrDeclined() {
        test.isActive(test.nekro, "action");
        test.sol.getSCs().clear();
        test.nekro.addSC(IMPERIAL);
        test.game.setSpeaker(test.nekro);
        AiTurnContext holder = test.context(imperialCard());
        StrategyCardRules.play(holder, imperialCard(), imperialCard().buttons().getFirst(), IMPERIAL);
        StrategyCardRules.resolvePrimary(holder);

        assertThat(StrategyCardRules.awaitingSecondaries(holder)).isTrue();
        assertThat(StrategyCardRules.awaitSecondaries(holder)).containsInstanceOf(AiDecision.Wait.class);

        test.sol.addFollowedSC(IMPERIAL);
        human.addFollowedSC(IMPERIAL);

        assertThat(StrategyCardRules.awaitingSecondaries(holder)).isFalse();
        assertThat(StrategyCardRules.awaitSecondaries(holder)).isEmpty();
    }
}
