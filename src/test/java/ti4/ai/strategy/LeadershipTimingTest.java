package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.StrategyCard;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

// In the live game aiplay1 the Leadership holder followed Technology and Diplomacy, then had no strategy token left
// for Construction although a 4-structure public was in play. Leadership was only played once nothing better was left
// to do. A held Leadership is now played first whenever the follows the seat wants this round outnumber its strategy
// tokens, so the 3 tokens arrive before the shortage bites.
class LeadershipTimingTest extends BaseTi4Test {

    private static final int LEADERSHIP = 1;
    private static final int CONSTRUCTION = 4;
    private static final int IMPERIAL = 8;

    private AiTestGame test;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.game.setVp(10);
        test.aiIsActive("action");
        test.nekro.addSC(LEADERSHIP);
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(3);
        test.sol.addSC(IMPERIAL);
        test.sol.addSC(CONSTRUCTION);
        test.game
                .getRevealedPublicObjectives()
                .put("build_defenses", test.game.getRevealedPublicObjectives().size() + 1);
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    private static AiPrompt turnStart() {
        return prompt(
                "turn",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_tacticalAction",
                "FFCC_nekro_strategicAction_" + LEADERSHIP);
    }

    // Imperial (a secret to draw) and Construction (a 4-structure public 3 short) are both wanted, with one strategy
    // token: Leadership is played at once rather than kept for the end of the round.
    @Test
    void playsLeadershipFirstWhenTheWantedFollowsOutnumberTheStrategyTokens() {
        test.nekro.setStrategicCC(1);
        changed();
        assertThat(StrategyCardRules.strategyTokensWantedForFollows(test.game, test.nekro))
                .isEqualTo(2);
        AiPrompt turn = turnStart();

        Optional<AiDecision> decision = StrategyCardTurn.playIfDue(test.context(turn), List.of(turn));

        assertThat(decision.map(AiTestGame::pressedId)).contains("FFCC_nekro_strategicAction_" + LEADERSHIP);
    }

    // The same shortage with Construction wanted for an early forward dock, no objective involved.
    @Test
    void playsLeadershipFirstForAWantedDockFollow() {
        test.game.getRevealedPublicObjectives().remove("build_defenses");
        test.place("26", "101");
        test.nekro.addPlanet("lodor");
        test.nekro.setStrategicCC(1);
        changed();
        assertThat(StrategyCardRules.strategyTokensWantedForFollows(test.game, test.nekro))
                .isEqualTo(2);

        assertThat(StrategyCardTurn.dueNow(test.context(turnStart()), LEADERSHIP))
                .isTrue();
    }

    // With a token for each wanted follow and tactic tokens to spare there is no hurry.
    @Test
    void keepsLeadershipWhileTheStrategyTokensCoverTheWantedFollows() {
        test.nekro.setStrategicCC(2);
        changed();

        assertThat(StrategyCardTurn.dueNow(test.context(turnStart()), LEADERSHIP))
                .isFalse();
    }

    // Once the cards it wanted to follow have been played, the shortage is gone and so is the reason to hurry.
    @Test
    void keepsLeadershipOnceTheWantedCardsHaveBeenPlayed() {
        test.nekro.setStrategicCC(0);
        test.game.setSCPlayed(IMPERIAL, true);
        test.game.setSCPlayed(CONSTRUCTION, true);
        changed();

        assertThat(StrategyCardTurn.dueNow(test.context(turnStart()), LEADERSHIP))
                .isFalse();
    }

    // Leadership gains nothing with every token already on the sheet, so the shortage alone does not make it due.
    @Test
    void keepsLeadershipWithNoReinforcements() {
        test.nekro.setStrategicCC(1);
        test.nekro.setTacticalCC(12);
        changed();
        assertThat(StrategyCardRules.reinforcements(test.game, test.nekro)).isZero();

        assertThat(StrategyCardTurn.dueNow(test.context(turnStart()), LEADERSHIP))
                .isFalse();
    }

    @Test
    void theConstructionFollowCountsTowardTheShortage() {
        assertThat(StrategyCardRules.wantsToFollow(test.game, test.nekro, StrategyCard.CONSTRUCTION))
                .isTrue();
    }
}
