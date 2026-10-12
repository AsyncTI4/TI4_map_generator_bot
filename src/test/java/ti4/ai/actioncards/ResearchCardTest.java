package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
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
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.scoring.PaymentRules;
import ti4.ai.strategy.ResearchPolicy;
import ti4.ai.strategy.StrategyCardRules;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

class ResearchCardTest extends BaseTi4Test {

    private static final long SETTLED = NOW + 11_000L;
    private static final double EXACT = 1e-9;

    private CardTable table;
    private MockedStatic<GameMessageManager> messages;

    @BeforeEach
    void setUp() {
        table = new CardTable();
        table.nekro.removeAbility("propagation");
        table.nekro.setTechs(List.of("nm", "inf2", "gls"));
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

    private Optional<Plan> plan(String alias) {
        return AiActionCards.of(alias).orElseThrow().planFor(table.game, table.nekro);
    }

    private String turnKey() {
        return "acPlay|turn:" + table.test.context().turnKey();
    }

    private void inTargetStage(String alias, String planned) {
        table.test.memory.put(turnKey(), new CardPlay(Stage.TARGET, NOW, NOW, alias, "", true, 0, planned).encode());
    }

    private AiPrompt addressed(String id, String... customIds) {
        return AiTestGame.withContent(
                prompt(id, PromptSource.PUBLIC, SETTLED, customIds),
                table.nekro.getRepresentationUnfogged() + ", please choose.");
    }

    // ---- Focused Research --------------------------------------------------------------------

    @Test
    void focusedResearchIsWorthTheBestTechnologyLessItsFourTradeGoods() {
        table.nekro.setTg(6);
        String best = ResearchPolicy.bestResearchable(table.game, table.nekro).orElseThrow();

        assertThat(plan("f_researched").orElseThrow().value())
                .isCloseTo(ResearchPolicy.researchValue(table.game, table.nekro, best) - 4, within(EXACT));
    }

    @Test
    void focusedResearchNeedsFourTradeGoods() {
        table.nekro.setTg(3);

        assertThat(plan("f_researched")).isEmpty();
    }

    // Negotiate Trade Routes needs exactly the 5 trade goods the seat holds, so 4 of them are not its to spend.
    @Test
    void focusedResearchLeavesTheTradeGoodsTheScoringReserveNeeds() {
        table.reveal("trade_routes");
        table.nekro.setTg(5);

        assertThat(plan("f_researched")).isEmpty();
    }

    // Propagation turns every research into command tokens, so the card is of no use to Nekro.
    @Test
    void focusedResearchAndDivertFundingAreUselessToNekro() {
        table.nekro.addAbility("propagation");
        table.nekro.setTg(6);

        assertThat(plan("f_researched")).isEmpty();
        assertThat(plan("divert_funding")).isEmpty();
    }

    @Test
    void focusedResearchResearchesAfterTheWindowAndPaysNothingMore() {
        table.nekro.setTg(6);
        inTargetStage("f_researched", "");
        AiPrompt get = addressed("get", "acquireATech");

        assertThat(AiTestGame.pressedId(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, get))
                        .orElseThrow()))
                .isEqualTo("acquireATech");

        AiPrompt options =
                prompt("options", PromptSource.AI_THREAD, SETTLED, "FFCC_nekro_getTech_nm", "FFCC_nekro_getTech_hm");
        assertThat(StrategyCardRules.chooseTechnology(table.test.contextAt(SETTLED, options)))
                .isPresent();
    }

    // ---- Divert Funding ----------------------------------------------------------------------

    @Test
    void divertFundingReturnsTheLeastValuableTechnologyItMayReturn() {
        Plan plan = plan("divert_funding").orElseThrow();

        assertThat(plan.target()).isEqualTo("gls");
        assertThat(plan.value()).isLessThan(ResearchPolicy.heldValue("nm") + ResearchPolicy.heldValue("gls"));
    }

    @Test
    void divertFundingNeverReturnsAUnitUpgradeOrAFactionTechnology() {
        table.nekro.setTechs(List.of("inf2", "vax"));

        assertThat(plan("divert_funding")).isEmpty();
    }

    @Test
    void divertFundingReturnsTheTechnologyItAnnouncedThenResearchesAnother() {
        table.nekro.setTechs(List.of("gd", "gls", "nm"));
        inTargetStage("divert_funding", "gd");
        AiPrompt returns = prompt(
                "returns",
                PromptSource.PUBLIC,
                SETTLED,
                "FFCC_nekro_divertFunding@gls",
                "FFCC_nekro_divertFunding@gd",
                "FFCC_nekro_divertFunding@nm");

        assertThat(AiTestGame.pressedId(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, returns))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_divertFunding@gd");
        assertThat(CardPlay.decode(table.test.memory.get(turnKey()).orElse("")).map(CardPlay::stage))
                .contains(Stage.TARGET);

        AiPrompt get = addressed("get", "acquireATech");
        assertThat(AiTestGame.pressedId(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, get))
                        .orElseThrow()))
                .isEqualTo("acquireATech");
        assertThat(CardPlay.decode(table.test.memory.get(turnKey()).orElse("")).map(CardPlay::stage))
                .contains(Stage.DONE);
    }

    // The technology it returned is back in the deck and would be offered again; researching it would waste the card.
    @Test
    void neverResearchesTheTechnologyItJustReturned() {
        StrategyCardRules.expectResearchOtherThan(table.test.contextAt(SETTLED), 0, "gd");
        AiPrompt options =
                prompt("options", PromptSource.AI_THREAD, SETTLED, "FFCC_nekro_getTech_gd", "FFCC_nekro_getTech_pa");

        Optional<AiDecision> choice = StrategyCardRules.chooseTechnology(table.test.contextAt(SETTLED, options));

        assertThat(AiTestGame.pressedId(choice.orElseThrow())).isEqualTo("FFCC_nekro_getTech_pa");
    }

    // ---- Plagiarize --------------------------------------------------------------------------

    private void solNextToNekroWith(String... techs) {
        table.system("46", "302");
        table.ships(table.sol, table.game.getTileByPosition("302"), ti4.helpers.Units.UnitType.Destroyer, 1);
        table.sol.setTechs(List.of(techs));
        table.game.setLastModifiedDate(System.nanoTime());
    }

    private void nekroHoldsInfluence() {
        table.system("19", "202");
        table.nekro.addPlanet("wellon");
        table.system("37", "203");
        table.nekro.addPlanet("meer");
    }

    @Test
    void plagiarizeCopiesTheBestTechnologyAnAdjacentPlayerHasLessWhatTheInfluenceCosts() {
        solNextToNekroWith("gd", "dxa");
        nekroHoldsInfluence();

        Plan plan = plan("plagiarize").orElseThrow();

        assertThat(plan.target()).isEqualTo("gd");
        assertThat(plan.value()).isLessThan(ResearchPolicy.value(table.game, table.nekro, "gd"));
        assertThat(plan.value()).isPositive();
    }

    // Mordai II has 4 resources and no influence: the 5 influence cannot be paid.
    @Test
    void plagiarizeNeedsFiveInfluence() {
        solNextToNekroWith("gd");

        assertThat(plan("plagiarize")).isEmpty();
    }

    @Test
    void plagiarizeNeedsATechnologyANeighbourHas() {
        nekroHoldsInfluence();

        assertThat(plan("plagiarize")).isEmpty();
    }

    @Test
    void plagiarizeTakesTheTechnologyAndPaysTheInfluenceHonestly() {
        solNextToNekroWith("gd", "dxa");
        nekroHoldsInfluence();
        inTargetStage("plagiarize", "gd");
        AiPrompt techs = prompt("techs", PromptSource.PUBLIC, SETTLED, "getTech_dxa__noPay", "getTech_gd__noPay");

        AiDecision press = ActionCardRules.continuePlay(table.test.contextAt(SETTLED, techs))
                .orElseThrow();

        assertThat(AiTestGame.pressedId(press)).isEqualTo("getTech_gd__noPay");
        assertThat(PaymentRules.isPending(table.test.contextAt(SETTLED))).isTrue();
        assertThat(CardPlay.decode(table.test.memory.get(turnKey()).orElse("")).map(CardPlay::stage))
                .contains(Stage.DONE);
    }

    // ---- the objectives a technology completes ----------------------------------------------

    // Diversify Research takes two technologies in each of two colours. Nekro owns one biotic technology (Neural
    // Motivator) and, with Gravity Drive and Antimass Deflectors, a pair of propulsion ones.
    @Test
    void researchPolicySeesTheObjectiveATechnologyWouldComplete() {
        table.nekro.setTechs(List.of("nm", "gd", "amd"));
        table.reveal("diversify");

        assertThat(ResearchPolicy.publicPointsOneTechShort(table.game, table.nekro))
                .isEqualTo(1);
        assertThat(ResearchPolicy.publicPointsCompletedBy(table.game, table.nekro, "pa"))
                .isEqualTo(1);
        assertThat(ResearchPolicy.publicPointsCompletedBy(table.game, table.nekro, "ps"))
                .isZero();
    }
}
