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
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.ai.tactical.SetupHint;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.testUtils.BaseTi4Test;

// During the action phase the AI re-plans each held strategy card from the live board every turn, plays a card early
// when its plan says now is the moment, and nudges the tactical planner to set up multi-step plans.
class StrategyCardTurnTest extends BaseTi4Test {

    private static final int DIPLOMACY = 2;
    private static final int WARFARE = 6;
    private static final int IMPERIAL = 8;
    private static final String MECATOL = "000";
    private static final String FAR_SIDE = "310";

    private AiTestGame test;
    private Tile home;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
        test.game.setVp(10);
        test.aiIsActive("action");
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    private static AiPrompt turnStart(int... initiatives) {
        String[] ids = new String[initiatives.length + 1];
        ids[0] = "FFCC_nekro_tacticalAction";
        for (int i = 0; i < initiatives.length; i++) ids[i + 1] = "FFCC_nekro_strategicAction_" + initiatives[i];
        return prompt("turn", PromptSource.PUBLIC, NOW, ids);
    }

    private Tile solDreadnoughtNextTo(String position) {
        Tile next = test.place("26", AiTestGame.neighbourOf(position));
        test.units(next, "space", test.sol, UnitType.Dreadnought, 1);
        changed();
        return next;
    }

    private void holdMecatol() {
        test.place("18", MECATOL);
        test.nekro.addPlanet("mr");
        changed();
    }

    private Optional<CardPlan> stored(AiTurnContext context, int initiative) {
        return CardPlanMemory.read(context, initiative);
    }

    // Mecatol Rex is lost to another player mid-round: Imperial's plan falls back to drawing a secret, the change is
    // recorded, and there is no longer a reason to play the card early.
    @Test
    void replansImperialWhenMecatolRexIsLost() {
        holdMecatol();
        test.nekro.addSC(IMPERIAL);
        AiTurnContext context = test.context(turnStart(IMPERIAL));
        CardPlanMemory.write(
                context, StrategyCardPlanner.plan(PlanInputs.of(test.game, test.nekro, PlanMoment.PICK), IMPERIAL));
        assertThat(stored(context, IMPERIAL).flatMap(CardPlan::main))
                .map(Benefit::use)
                .contains(CardUse.IMPERIAL_MECATOL);

        test.nekro.removePlanet("mr");
        changed();
        StrategyCardReplanner.refresh(context);

        assertThat(stored(context, IMPERIAL).flatMap(CardPlan::main))
                .map(Benefit::use)
                .contains(CardUse.IMPERIAL_SECRET);
        assertThat(CardPlanMemory.replannedFrom(context, IMPERIAL)).contains(CardUse.IMPERIAL_MECATOL);
        assertThat(StrategyCardTurn.dueNow(context, IMPERIAL)).isFalse();
    }

    // The fleet that threatened home moves off towards another Nekro system: Diplomacy now locks that one.
    @Test
    void retargetsDiplomacyWhenTheThreatMoves() {
        Tile mehar = test.place("24", FAR_SIDE);
        test.nekro.addPlanet("meharxull");
        test.nekro.addSC(DIPLOMACY);
        Tile nearHome = solDreadnoughtNextTo(AiTestGame.HOME);
        AiTurnContext context = test.context(turnStart(DIPLOMACY));

        StrategyCardReplanner.refresh(context);
        assertThat(stored(context, DIPLOMACY).flatMap(plan -> plan.benefit(CardUse.DIPLOMACY_PROTECT)))
                .map(Benefit::target)
                .contains(AiTestGame.HOME);

        test.game.removeTile(nearHome.getPosition());
        solDreadnoughtNextTo(mehar.getPosition());
        StrategyCardReplanner.refresh(context);

        assertThat(stored(context, DIPLOMACY).flatMap(plan -> plan.benefit(CardUse.DIPLOMACY_PROTECT)))
                .map(Benefit::target)
                .contains(FAR_SIDE);
    }

    // A Mecatol Rex point an enemy fleet can reach is cashed before that fleet moves.
    @Test
    void playsImperialAtOnceWhileItsMecatolPointIsThreatened() {
        holdMecatol();
        test.nekro.addSC(IMPERIAL);
        solDreadnoughtNextTo(MECATOL);
        AiPrompt turn = turnStart(IMPERIAL);

        Optional<AiDecision> decision = StrategyCardTurn.playIfDue(test.context(turn), List.of(turn));

        assertThat(decision.map(AiTestGame::pressedId)).contains("FFCC_nekro_strategicAction_8");
        assertThat(decision.map(found -> ((AiDecision.Press) found).reason()))
                .hasValueSatisfying(reason -> assertThat(reason).contains("Imperial"));
    }

    @Test
    void keepsImperialForLaterWhileItsPointIsSafe() {
        holdMecatol();
        test.nekro.addSC(IMPERIAL);
        AiPrompt turn = turnStart(IMPERIAL);

        assertThat(StrategyCardTurn.playIfDue(test.context(turn), List.of(turn)))
                .isEmpty();
    }

    // PoK Warfare takes back a command token. Once the token on home strands ships that could take a free planet
    // next door, playing Warfare now is worth more than waiting.
    @Test
    void playsPokWarfareOnceAHomeTokenStrandsUsefulShips() {
        test.game.setStrategyCardSet("pok");
        test.nekro.addSC(WARFARE);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Infantry, 2);
        test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        changed();
        AiTurnContext context = test.context(turnStart(WARFARE));
        assertThat(StrategyCardTurn.dueNow(context, WARFARE)).isFalse();

        home.addCC(Mapper.getCCID(test.nekro.getColor()));
        changed();

        assertThat(StrategyCardTurn.dueNow(test.context(turnStart(WARFARE)), WARFARE))
                .isTrue();
    }

    // Round 1 opening: Thunder's Edge Warfare's free tactical action places no token, so the fleet it moves can move
    // again next turn. Players fire it on turn 1 to push the home fleet into their slice, rather than hold it.
    @Test
    void playsThundersEdgeWarfareOnTheFirstTurnOfRoundOne() {
        test.nekro.addSC(WARFARE);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Infantry, 2);
        test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        changed();
        assertThat(StrategyCardTurn.dueNow(test.context(turnStart(WARFARE)), WARFARE))
                .isFalse();

        test.game.setRound(1);
        changed();

        assertThat(StrategyCardTurn.dueNow(test.context(turnStart(WARFARE)), WARFARE))
                .isTrue();
    }

    // The free action brings both carriers and the spare ground forces along, not just the one carrier the expansion
    // needs: nothing it moves is locked under a token afterwards, and the rest of the slice lies past the system it
    // takes.
    @Test
    void thundersEdgeWarfareAdvancesTheWholeHomeFleet() {
        test.game.setRound(1);
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 4);
        Tile forward = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.place("27", beyond(forward.getPosition()));
        changed();

        TacticalPlan plan =
                TacticalPlanner.bestForWarfare(test.game, test.nekro).orElseThrow();

        assertThat(plan.target()).isEqualTo(forward.getPosition());
        assertThat(moved(plan, UnitType.Carrier)).isEqualTo(2);
        assertThat(moved(plan, UnitType.Infantry)).isGreaterThan(1);
    }

    private static String beyond(String position) {
        List<String> nearHome = ti4.image.PositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        return ti4.image.PositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate ->
                        !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate) && !nearHome.contains(candidate))
                .findFirst()
                .orElseThrow();
    }

    private static int moved(TacticalPlan plan, UnitType type) {
        return plan.moves().stream()
                .filter(move -> move.type() == type)
                .mapToInt(TacticalPlan.UnitMove::count)
                .sum();
    }

    // A held card's setup bonus steers the tactical planner into the first step of the card's plan.
    @Test
    void aSetupHintSteersTheTacticalPlannerToTheHintedStep() {
        test.nekro.setTg(10);
        test.nekro.setTacticalCC(1);
        changed();
        SetupHint produceAtHome = new SetupHint(TacticalPlan.Kind.PRODUCE, AiTestGame.HOME, 100);

        TacticalPlan plan = TacticalPlanner.best(test.game, test.nekro, List.of(produceAtHome))
                .orElseThrow();

        assertThat(plan.kind()).isEqualTo(TacticalPlan.Kind.PRODUCE);
        assertThat(plan.target()).isEqualTo(AiTestGame.HOME);
        assertThat(plan.score()).isGreaterThanOrEqualTo(100);
    }

    @Test
    void roundTripsSetupHints() {
        List<SetupHint> hints = List.of(
                new SetupHint(TacticalPlan.Kind.PRODUCE, AiTestGame.HOME, 2.5),
                new SetupHint(TacticalPlan.Kind.EXPAND, "202", 1.25));

        assertThat(SetupHint.decodeAll(SetupHint.encodeAll(hints))).isEqualTo(hints);
        assertThat(SetupHint.decodeAll("")).isEmpty();
    }
}
