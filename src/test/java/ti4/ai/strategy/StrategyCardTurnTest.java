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
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// During the action phase the AI re-plans each held strategy card from the live board every turn, plays a card early
// when its plan says now is the moment, and nudges the tactical planner to set up multi-step plans.
class StrategyCardTurnTest extends BaseTi4Test {

    private static final int DIPLOMACY = 2;
    private static final int CONSTRUCTION = 4;
    private static final int WARFARE = 6;
    private static final int IMPERIAL = 8;
    private static final String MECATOL = "000";

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

    private Tile solLandingPartyNextTo(String position) {
        Tile next = test.place("26", AiTestGame.neighbourOf(position));
        test.units(next, "space", test.sol, UnitType.Carrier, 2);
        test.units(next, "space", test.sol, UnitType.Infantry, 6);
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

    // The landing party that threatened home moves out of reach: the lock is no longer worth the card. A minor colony
    // it moves on to is not worth locking either.
    @Test
    void dropsTheDiplomacyLockWhenTheThreatMovesAway() {
        test.nekro.addSC(DIPLOMACY);
        Tile nearHome = solLandingPartyNextTo(AiTestGame.HOME);
        AiTurnContext context = test.context(turnStart(DIPLOMACY));

        StrategyCardReplanner.refresh(context);
        assertThat(stored(context, DIPLOMACY).flatMap(plan -> plan.benefit(CardUse.DIPLOMACY_PROTECT)))
                .map(Benefit::target)
                .contains(AiTestGame.HOME);

        test.game.removeTile(nearHome.getPosition());
        changed();
        StrategyCardReplanner.refresh(context);

        assertThat(stored(context, DIPLOMACY).flatMap(plan -> plan.benefit(CardUse.DIPLOMACY_PROTECT)))
                .isEmpty();
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

    // Thunder's Edge Warfare's tactical action places no token, so the fleet it moves can move again. Rather than spend
    // a token on the first worthwhile tactical action of the round and waste the card later (Xxcha played it on its 6th
    // turn to move nothing), the AI uses Warfare for that action, in round 1 and after.
    @Test
    void usesThundersEdgeWarfareForTheFirstWorthwhileTacticalAction() {
        test.nekro.addSC(WARFARE);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Infantry, 2);
        test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        changed();

        assertThat(StrategyCardTurn.dueNow(test.context(turnStart(WARFARE)), WARFARE))
                .isTrue();

        test.game.setRound(1);
        changed();

        assertThat(StrategyCardTurn.dueNow(test.context(turnStart(WARFARE)), WARFARE))
                .isTrue();
    }

    // With nothing worth activating yet the card is kept for later in the round, not spent on a token-free shuffle.
    @Test
    void keepsThundersEdgeWarfareWhileNothingIsWorthActivating() {
        test.nekro.addSC(WARFARE);
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.place("47", AiTestGame.neighbourOf(AiTestGame.HOME));
        changed();

        assertThat(StrategyCardTurn.dueNow(test.context(turnStart(WARFARE)), WARFARE))
                .isFalse();
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

    // Round 1 with Thunder's Edge Construction and Nekro's real start (the fleet pool is full). With two systems in
    // reach the carrier moves out first, which frees the fleet room Construction needs for the second carrier; once
    // it has gone, Construction is played.
    @Test
    void movesOutBeforeConstructionWhenTwoSystemsAreInReach() {
        openingWithConstruction();
        Tile first = test.place("27", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.place("28", otherNeighbour());
        changed();

        assertThat(StrategyCardTurn.dueNow(test.context(turnStart(CONSTRUCTION)), CONSTRUCTION))
                .isFalse();

        home.removeUnit("space", Units.getUnitKey(UnitType.Carrier, test.nekro.getColor()), 1);
        home.removeUnit("mordaiii", Units.getUnitKey(UnitType.Infantry, test.nekro.getColor()), 2);
        test.units(first, "space", test.nekro, UnitType.Carrier, 1);
        test.units(first, "newalbion", test.nekro, UnitType.Infantry, 1);
        test.units(first, "starpoint", test.nekro, UnitType.Infantry, 1);
        test.nekro.addPlanet("newalbion");
        test.nekro.addPlanet("starpoint");
        changed();

        assertThat(StrategyCardTurn.dueNow(test.context(turnStart(CONSTRUCTION)), CONSTRUCTION))
                .isTrue();
    }

    // Only one system with planets is in reach: Construction comes first and fills the carrier with infantry.
    @Test
    void playsConstructionFirstWhenOnlyOneSystemIsInReach() {
        openingWithConstruction();
        Tile first = test.place("27", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.place("28", beyond(first.getPosition()));
        changed();

        assertThat(StrategyCardTurn.dueNow(test.context(turnStart(CONSTRUCTION)), CONSTRUCTION))
                .isTrue();
    }

    private void openingWithConstruction() {
        test.game.setRound(1);
        test.nekro.addSC(CONSTRUCTION);
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(3);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Cruiser, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
    }

    private static String otherNeighbour() {
        String neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
        return ti4.model.MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position) && !position.equals(neighbour))
                .findFirst()
                .orElseThrow();
    }

    private static String beyond(String position) {
        List<String> nearHome = ti4.model.MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        return ti4.model.MapPositionMapper.getAdjacentTilePositions(position).stream()
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
