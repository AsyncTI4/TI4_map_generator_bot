package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.StrategyCard;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.strategy.CardFacade;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

// Strategize performs the secondary of any readied or unchosen strategy card (card text), and the secondary of
// Leadership needs no strategy token (LRR 52, note I; Strategize ruling II exempts Leadership). A seat that is out of
// tactic tokens while Leadership is still to be played can therefore buy tokens now and keep acting instead of passing.
class StrategizeLeadershipTest extends BaseTi4Test {

    private static final long SETTLED = NOW + 11_000L;
    private static final int HAND_INDEX = 412;
    private static final String LEADERSHIP_BUTTON = "leadershipGenerateCCButtons";

    private CardTable table;
    private MockedStatic<GameMessageManager> messages;

    @BeforeEach
    void setUp() {
        table = new CardTable();
        table.nekro.setStrategicCC(0);
        table.nekro.setTacticalCC(0);
        table.nekro.setFleetCC(3);
        table.nekro.setActionCard("strategize3", HAND_INDEX);
        table.sol.addSC(initiativeOf(StrategyCard.LEADERSHIP));
        onlyLeadershipIsLeft();
        holdInfluencePlanets();
        Tile home = table.test.nekroHome();
        table.ships(table.nekro, home, UnitType.Carrier, 2);
        table.ships(table.nekro, home, UnitType.Destroyer, 2);
        messages = Mockito.mockStatic(GameMessageManager.class);
        messages.when(() -> GameMessageManager.getOne(anyString(), anyString()))
                .thenAnswer(call -> Optional.of(new GameMessage(
                        call.getArgument(1),
                        GameMessageType.ACTION_CARD,
                        new LinkedHashSet<>(List.of("nekro", "sol")),
                        0L)));
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    private void holdInfluencePlanets() {
        table.test.place("29", "201");
        table.test.place("24", "202");
        table.nekro.addPlanet("qucenn");
        table.nekro.addPlanet("rarron");
        table.nekro.addPlanet("meharxull");
    }

    private int initiativeOf(StrategyCard card) {
        for (int initiative : table.game.getSCList()) {
            if (StrategyCard.of(table.game, initiative) == card) return initiative;
        }
        throw new AssertionError(card + " is not in this game");
    }

    private void onlyLeadershipIsLeft() {
        for (int initiative : table.game.getSCList()) {
            if (StrategyCard.of(table.game, initiative) != StrategyCard.LEADERSHIP) {
                table.game.setSCPlayed(initiative, true);
            }
        }
    }

    private Optional<Plan> planBeforePassing() {
        return AiActionCards.of("strategize3")
                .orElseThrow()
                .planFor(table.game, table.nekro, PlayMoment.BEFORE_PASSING);
    }

    @Test
    void whenOutOfTacticTokensWithLeadershipStillToComeStrategizeBuysTokensInsteadOfPassing() {
        Plan plan = planBeforePassing().orElseThrow();

        assertThat(plan.target()).isEqualTo("LEADERSHIP");
        assertThat(plan.value()).isPositive();
        assertThat(CardFacade.stalledLeadershipValue(table.game, table.nekro)).isPresent();
    }

    @Test
    void theSameSituationDuringTheTurnPlaysNothing() {
        assertThat(AiActionCards.of("strategize3").orElseThrow().planFor(table.game, table.nekro))
                .isEmpty();
    }

    @Test
    void aSeatWithTacticTokensLeftDoesNotNeedLeadershipToKeepPlaying() {
        table.nekro.setTacticalCC(1);

        assertThat(planBeforePassing()).isEmpty();
    }

    @Test
    void nothingToDoOnceLeadershipHasBeenPlayed() {
        table.game.setSCPlayed(initiativeOf(StrategyCard.LEADERSHIP), true);

        assertThat(planBeforePassing()).isEmpty();
    }

    @Test
    void theSeatPlaysItsOwnLeadershipAsAStrategicActionInstead() {
        int leadership = initiativeOf(StrategyCard.LEADERSHIP);
        table.sol.removeSC(leadership);
        table.nekro.addSC(leadership);

        assertThat(planBeforePassing()).isEmpty();
    }

    @Test
    void anUnchosenLeadershipCanBeUsedToo() {
        table.sol.removeSC(initiativeOf(StrategyCard.LEADERSHIP));

        assertThat(planBeforePassing().map(Plan::target)).contains("LEADERSHIP");
    }

    @Test
    void withoutInfluenceToSpendThereIsNothingToBuy() {
        table.nekro.getPlanets().clear();
        table.nekro.setTg(0);

        assertThat(planBeforePassing()).isEmpty();
    }

    @Test
    void notInFogOfWar() {
        table.game.setFowMode(true);

        assertThat(planBeforePassing()).isEmpty();
    }

    @Test
    void aSeatWithAStrategyTokenToSpareRemembersToTakeItBack() {
        table.nekro.setStrategicCC(1);

        assertThat(planBeforePassing().map(Plan::target)).contains("LEADERSHIP+");
    }

    private AiPrompt secondaries() {
        return AiTestGame.withContent(
                prompt("secondaries", PromptSource.PUBLIC, SETTLED, LEADERSHIP_BUTTON, "draw2 AC"),
                table.nekro.getRepresentationUnfogged() + ", please resolve Strategize using these buttons.");
    }

    private Optional<AiDecision> resolveSecondary(String planned) {
        CardPlay play = new CardPlay(Stage.TARGET, SETTLED, NOW, "strategize3", "", false, 0, planned);
        return AiActionCards.of("strategize3")
                .orElseThrow()
                .target()
                .next(table.test.contextAt(SETTLED, secondaries()), "acPlay|test", play);
    }

    @Test
    void theSecondaryOfLeadershipIsPressedAndTheTokensAreRequested() {
        Optional<AiDecision> decision = resolveSecondary("LEADERSHIP");

        assertThat(AiTestGame.pressedId(decision.orElseThrow())).isEqualTo(LEADERSHIP_BUTTON);
        assertThat(table.test.memory.get("tokenGain|" + table.game.getRound()))
                .hasValueSatisfying(value -> assertThat(value).endsWith("|0|2"));
    }

    @Test
    void aStrategyTokenTheBotTookIsGainedBackForFree() {
        Optional<AiDecision> decision = resolveSecondary("LEADERSHIP+");

        assertThat(AiTestGame.pressedId(decision.orElseThrow())).isEqualTo(LEADERSHIP_BUTTON);
        assertThat(table.test.memory.get("tokenGain|" + table.game.getRound()))
                .hasValueSatisfying(value -> assertThat(value).endsWith("|1|2"));
    }

    @Test
    void aPlanForAnotherSecondaryNeverPressesLeadership() {
        assertThat(resolveSecondary("IMPERIAL")).isEmpty();
    }
}
