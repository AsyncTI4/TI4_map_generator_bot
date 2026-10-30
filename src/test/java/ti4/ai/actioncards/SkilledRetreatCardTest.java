package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import ti4.ai.tactical.CombatForecast;
import ti4.ai.tactical.CombatMoment;
import ti4.ai.tactical.CombatRules;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// Skilled Retreat ("At the start of a combat round", space combat only) moves the seat's ships to an adjacent system
// without other players' ships and places a command token there. The engine's resolve button is the owned
// "retreat_<pos>_skilled" (ActionCardHelper, automation "skilled_retreat"), which posts owned
// "retreatUnitsFrom_<from>_<to>_skilled" destination buttons (ButtonHelperModifyUnits.getRetreatSystemButtons).
class SkilledRetreatCardTest extends BaseTi4Test {

    private static final long SETTLED = NOW + 11_000L;
    private static final int HAND_INDEX = 616;
    private static final String SPACE = "space";
    private static final String TITLE = "Skilled Retreat";
    private static final double BIG_PLAN = 40.0;

    private CardTable table;
    private MockedStatic<GameMessageManager> messages;
    private String position;
    private Tile tile;
    private List<String> neighbours;

    @BeforeEach
    void setUp() {
        table = new CardTable();
        position = CardTable.SOL_SPOT;
        tile = table.system("26", position);
        table.game.setActiveSystem(position);
        neighbours = MapPositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate))
                .toList();
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

    private CombatMoment moment() {
        return new CombatMoment(position, SPACE, false, 0);
    }

    private String key() {
        return PlayMemory.combatKey(position, SPACE, 0);
    }

    private Optional<Stage> stage() {
        return table.test.memory.get(key()).flatMap(CardPlay::decode).map(CardPlay::stage);
    }

    private static AiPrompt hand() {
        return prompt("hand", PromptSource.AI_THREAD, NOW, "ac_play_from_hand_" + HAND_INDEX, "cardsInfo");
    }

    private Optional<AiDecision> atRoundStart() {
        return CombatCardRules.atRoundStart(table.test.context(hand()), moment());
    }

    // Two dreadnoughts against six: the fight is all but lost and 8 resources of ships go with it.
    private void hopelessFight() {
        table.ships(table.nekro, tile, UnitType.Dreadnought, 2);
        table.ships(table.sol, tile, UnitType.Dreadnought, 6);
        table.nekro.setActionCard("s_retreat1", HAND_INDEX);
    }

    private Tile emptyNeighbour() {
        return table.system("19", neighbours.getFirst());
    }

    @Test
    void aHopelessFightAwayFromHomeIsLeftForAnEmptyNeighbour() {
        hopelessFight();
        emptyNeighbour();

        assertThat(SkilledRetreatCard.value(table.test.context(), moment()))
                .isGreaterThan(ActionCardPrior.keepPrior("s_retreat1"));
        assertThat(pressedId(atRoundStart().orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    // The seat would roll its first round after the grace period; the card is played at that moment instead.
    @Test
    void theRetreatIsPlayedInsteadOfTheSeatsRoll() {
        hopelessFight();
        emptyNeighbour();
        AiPrompt rolls = prompt("roll", PromptSource.COMBAT_THREAD, NOW, "combatRoll_" + position + "_" + SPACE);

        Optional<AiDecision> decision =
                CombatRules.next(table.test.contextAt(NOW + 120_000L, rolls, hand()), CombatCardRules::atRoundStart);

        assertThat(pressedId(decision.orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    @Test
    void theCardIsKeptWithoutADestination() {
        hopelessFight();

        assertThat(atRoundStart()).isEmpty();
    }

    @Test
    void theCardIsNotPlayedAtHome() {
        Tile home = table.game.getTileByPosition(AiTestGame.HOME);
        table.game.setActiveSystem(AiTestGame.HOME);
        table.ships(table.nekro, home, UnitType.Dreadnought, 2);
        table.ships(table.sol, home, UnitType.Dreadnought, 6);
        table.nekro.setActionCard("s_retreat1", HAND_INDEX);
        table.system("19", AiTestGame.neighbourOf(AiTestGame.HOME));
        CombatMoment atHome = new CombatMoment(AiTestGame.HOME, SPACE, false, 0);

        assertThat(SkilledRetreatCard.value(table.test.context(), atHome)).isZero();
        assertThat(CombatCardRules.atRoundStart(table.test.context(hand()), atHome))
                .isEmpty();
    }

    @Test
    void theCardIsKeptInAnEvenFight() {
        table.ships(table.nekro, tile, UnitType.Dreadnought, 4);
        table.ships(table.sol, tile, UnitType.Dreadnought, 4);
        table.nekro.setActionCard("s_retreat1", HAND_INDEX);
        emptyNeighbour();

        assertThat(atRoundStart()).isEmpty();
    }

    @Test
    void theCardIsNotPlayedInAGroundCombat() {
        hopelessFight();
        emptyNeighbour();
        table.test.units(tile, "lodor", table.nekro, UnitType.Infantry, 1);
        table.test.units(tile, "lodor", table.sol, UnitType.Infantry, 4);
        CombatMoment ground = new CombatMoment(position, "lodor", true, 0);

        assertThat(SkilledRetreatCard.value(table.test.context(), ground)).isZero();
    }

    private void remember(TacticalPlan plan) {
        TacticalRules.remember(table.test.context(), plan);
    }

    private static TacticalPlan planInto(Tile target, double score) {
        return new TacticalPlan(TacticalPlan.Kind.EXPAND, target.getPosition(), List.of(), Map.of(), score);
    }

    // The seat plans to get to the only destination: the card's token would lock that action out for the round,
    // which costs more than the ships the retreat saves.
    @Test
    void theCardIsKeptWhenTheOnlyDestinationIsWhereTheSeatsPlanGoes() {
        hopelessFight();
        Tile wanted = emptyNeighbour();
        remember(planInto(wanted, BIG_PLAN));

        assertThat(atRoundStart()).isEmpty();
    }

    // The seat's plan moves the carrier out of the only destination: the token would lock it in.
    @Test
    void theCardIsKeptWhenTheOnlyDestinationHoldsUnitsThePlanMustMoveOut() {
        hopelessFight();
        Tile staging = emptyNeighbour();
        table.ships(table.nekro, staging, UnitType.Carrier, 1);
        Tile elsewhere = table.system("27", AiTestGame.neighbourOf(AiTestGame.HOME));
        remember(new TacticalPlan(
                TacticalPlan.Kind.EXPAND,
                elsewhere.getPosition(),
                List.of(new UnitMove(staging.getPosition(), SPACE, UnitType.Carrier, 1)),
                Map.of(),
                BIG_PLAN));

        assertThat(atRoundStart()).isEmpty();
    }

    // A small plan into the destination costs less than the eight resources of ships the retreat saves.
    @Test
    void aMinorPlanDoesNotStopTheRetreat() {
        hopelessFight();
        Tile wanted = emptyNeighbour();
        remember(planInto(wanted, 1.0));

        assertThat(pressedId(atRoundStart().orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    // Ruling VI: no token is placed where the seat already has one, so nothing new is locked.
    @Test
    void aPlannedSystemThatAlreadyHoldsTheSeatsTokenIsAFineDestination() {
        hopelessFight();
        Tile wanted = emptyNeighbour();
        remember(planInto(wanted, BIG_PLAN));
        wanted.addCC(Mapper.getCCID(table.nekro.getColor()));

        assertThat(pressedId(atRoundStart().orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    // Ruling III: Skilled Retreat cannot move into a nebula.
    @Test
    void aNebulaIsNoDestination() {
        hopelessFight();
        table.system("42", neighbours.getFirst());

        assertThat(atRoundStart()).isEmpty();
    }

    @Test
    void aSystemWithAnotherPlayersShipsIsNoDestination() {
        hopelessFight();
        Tile occupied = emptyNeighbour();
        table.ships(table.sol, occupied, UnitType.Destroyer, 1);

        assertThat(atRoundStart()).isEmpty();
    }

    // Three cruisers each, Nekro attacking: about a coin flip, not a heavy enough loss for the value rule.
    private void closeFightAgainstASecretHolder() {
        closeFightWhileLeading();
        table.sol.setSecret("sar");
    }

    private void closeFightWhileLeading() {
        table.ships(table.nekro, tile, UnitType.Cruiser, 3);
        table.ships(table.sol, tile, UnitType.Cruiser, 3);
        table.nekro.setActionCard("s_retreat1", HAND_INDEX);
        emptyNeighbour();
        table.points(table.nekro, 2);
    }

    // Sol holds an unscored secret (only the count is public) and Nekro leads on points: Spark a Rebellion is a
    // public possibility, and Sol is favoured to win.
    @Test
    void theCardDeniesAFavouredOpponentAWinThatCouldScoreASecret() {
        closeFightAgainstASecretHolder();
        double loss = CombatForecast.lossChance(table.game, table.nekro, position, SPACE)
                .orElseThrow();

        assertThat(loss).isBetween(SkilledRetreatCard.SECRET_WORRY_LOSS, 0.65);
        assertThat(SkilledRetreatCard.value(table.test.context(), moment())).isZero();
        assertThat(pressedId(atRoundStart().orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    @Test
    void noSecretIsFearedFromAnOpponentWithoutUnscoredSecrets() {
        closeFightWhileLeading();

        assertThat(atRoundStart()).isEmpty();
    }

    @Test
    void noSecretIsFearedWhenNoPublicConditionForOneHolds() {
        closeFightAgainstASecretHolder();
        table.points(table.sol, 3);

        assertThat(atRoundStart()).isEmpty();
    }

    private AiPrompt announcement() {
        return prompt(
                "announce",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_moveAlongAfterAllHaveReactedToAC_" + TITLE,
                "no_sabotage",
                "sabotage_ac_" + TITLE + "_nekro");
    }

    @Test
    void theRetreatResolvesAfterItsWindowAndAvoidsThePlannedSystem() {
        hopelessFight();
        Tile empty = table.system("19", neighbours.get(0));
        Tile dock = table.system("27", neighbours.get(1));
        remember(planInto(dock, BIG_PLAN));
        assertThat(pressedId(atRoundStart().orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
        table.nekro.getActionCards().clear();
        table.game.getGameStats().recordAcPlay(TITLE, table.nekro);
        AiPrompt announce = announcement();
        String resolveId = "FFCC_nekro_retreat_" + position + "_skilled";
        AiPrompt resolve = prompt("resolve", PromptSource.PUBLIC, NOW, resolveId);

        assertThat(ActionCardRules.continuePlay(table.test.contextAt(NOW + 1_000L, announce, resolve)))
                .isEmpty();
        assertThat(pressedId(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, announce, resolve))
                        .orElseThrow()))
                .isEqualTo(resolveId);
        assertThat(stage()).contains(Stage.TARGET);

        String toDock = "FFCC_nekro_retreatUnitsFrom_" + position + "_" + dock.getPosition() + "_skilled";
        String toEmpty = "FFCC_nekro_retreatUnitsFrom_" + position + "_" + empty.getPosition() + "_skilled";
        AiPrompt destinations = prompt("destinations", PromptSource.PUBLIC, SETTLED, toDock, toEmpty);

        assertThat(pressedId(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, destinations))
                        .orElseThrow()))
                .isEqualTo(toEmpty);
        assertThat(stage()).contains(Stage.DONE);
    }

    @Test
    void aSabotagedRetreatIsNotResolved() {
        hopelessFight();
        emptyNeighbour();
        atRoundStart();
        table.nekro.getActionCards().clear();
        table.game.getGameStats().recordAcPlay(TITLE, table.nekro);
        table.game.getGameStats().markLatestPlayCanceled(TITLE);
        AiPrompt resolve = prompt("resolve", PromptSource.PUBLIC, NOW, "FFCC_nekro_retreat_" + position + "_skilled");

        assertThat(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, announcement(), resolve)))
                .isEmpty();
        assertThat(stage()).contains(Stage.CANCELED);
    }

    @Test
    void theSkilledRetreatCardsHaveRules() {
        for (String alias : SkilledRetreatCard.ALIASES) {
            assertThat(AiActionCards.usable(alias)).as(alias).isTrue();
        }
    }
}
