package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.model.DeckModel;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class SabotagePolicyTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;
    private static final long LATE = NOW + 45 * 60_000L;

    private CardTable table;
    private MockedStatic<GameMessageManager> messages;

    @BeforeEach
    void setUp() {
        table = new CardTable();
        table.nekro.setActionCard("sabo1", 1);
        messages = Mockito.mockStatic(GameMessageManager.class);
        windowOpenFor();
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    private void windowOpenFor(String... factions) {
        messages.when(() -> GameMessageManager.getOne(anyString(), anyString()))
                .thenAnswer(call -> Optional.of(new GameMessage(
                        call.getArgument(1), GameMessageType.ACTION_CARD, new LinkedHashSet<>(List.of(factions)), 0L)));
    }

    private static AiPrompt window(String title, String faction) {
        return prompt(
                "window-" + title, PromptSource.PUBLIC, NOW, "no_sabotage", "sabotage_ac_" + title + "_" + faction);
    }

    private Optional<PromptButton> cancelWith(String title) {
        return SabotagePolicy.cancelWith(table.test.context(window(title, "sol")), window(title, "sol"));
    }

    private boolean cancels(String title) {
        return cancelWith(title).isPresent();
    }

    private static List<String> cancellableTitles() {
        DeckModel deck = Mapper.getDeck("action_cards_te");
        return deck.getCardIDs().stream()
                .map(Mapper::getActionCard)
                .filter(model -> !ti4.helpers.ActionCardHelper.cannotBeSabotaged(model))
                .map(model -> model.getName())
                .distinct()
                .sorted()
                .toList();
    }

    // ---- the baseline ------------------------------------------------------------------------

    private void overruleIsPlayed() {
        table.game.getGameStats().recordAcPlay("Overrule", table.sol);
    }

    private TreeSet<String> cancelledTitles() {
        table.test.isActive(table.sol, "action");
        TreeSet<String> cancelled = new TreeSet<>();
        for (String title : cancellableTitles()) {
            if (cancels(title)) cancelled.add(title);
        }
        return cancelled;
    }

    // "Save your Sabotage for Overrule": while it has not shown up, the seat's only Sabotage is worth 3, which
    // nothing an uninvolved seat sees at standing 1 reaches except Overrule itself.
    @Test
    void anUninvolvedSeatSavesItsOnlySabotageForOverruleWhileOverruleIsUnseen() {
        assertThat(cancelledTitles()).containsExactly("Overrule");
    }

    // With nothing at stake on the board, and a player who is neither a contender nor behind, the seat spends its
    // only Sabotage on a card only when human players cancel it often and it matters to the table.
    @Test
    void anUninvolvedSeatSpendsItsSabotageOnTheCardsHumansCancelMostOnceOverruleIsPlayed() {
        overruleIsPlayed();

        assertThat(cancelledTitles())
                .containsExactly(
                        "Confounding Legal Text",
                        "Imperial Rider",
                        "Lie in Wait",
                        "Overrule",
                        "Parley",
                        "Political Stability",
                        "Rout");
    }

    // involvement x standing x cancel appetite: a constant that moves puts a cell of this grid on the other side of
    // the 1.41 a lone Sabotage is worth.
    @Test
    void theBaselineGrid() {
        double sabotage = ActionCardPrior.keepPrior("sabo1");
        Map<String, Double> appetites = Map.of("Overrule", 4.0, "Public Disgrace", 3.26, "Mining Initiative", 0.1);
        double[] involvements = {Involvement.OTHERWISE, Involvement.AGENDA_PHASE, Involvement.AIMED_AT_SEAT};
        double[] standings = {TableStanding.TRAILING, TableStanding.EVEN, TableStanding.LEADING, TableStanding.CONTENDER
        };
        boolean[][] overrule = {
            {false, true, true, true},
            {true, true, true, true},
            {true, true, true, true},
        };
        boolean[][] disgrace = {
            {false, false, true, true},
            {false, true, true, true},
            {true, true, true, true},
        };
        boolean[][] mining = {
            {false, false, false, false},
            {false, false, false, false},
            {false, false, false, false},
        };
        for (int i = 0; i < involvements.length; i++) {
            for (int s = 0; s < standings.length; s++) {
                String cell = " at involvement " + involvements[i] + ", standing " + standings[s];
                assertThat(SabotagePolicy.baseline(appetites.get("Overrule"), involvements[i], standings[s])
                                >= sabotage)
                        .as("Overrule" + cell)
                        .isEqualTo(overrule[i][s]);
                assertThat(SabotagePolicy.baseline(appetites.get("Public Disgrace"), involvements[i], standings[s])
                                >= sabotage)
                        .as("Public Disgrace" + cell)
                        .isEqualTo(disgrace[i][s]);
                assertThat(SabotagePolicy.baseline(appetites.get("Mining Initiative"), involvements[i], standings[s])
                                >= sabotage)
                        .as("Mining Initiative" + cell)
                        .isEqualTo(mining[i][s]);
            }
        }
    }

    // ---- the players behind the card ---------------------------------------------------------

    @Test
    void passesOnEveryOrdinaryCardFromAPlayerWhoTrailsTheSeat() {
        overruleIsPlayed();
        table.test.isActive(table.sol, "action");
        assertThat(cancels("Political Stability")).isTrue();

        table.points(table.nekro, 5);
        table.points(table.sol, 3);

        assertThat(cancels("Mining Initiative")).isFalse();
        assertThat(cancels("Political Stability")).isFalse();
        assertThat(cancels("Overrule")).isFalse();
    }

    // Sol is two points short of winning and holds Mecatol Rex: Overrule on Imperial would score the last points.
    @Test
    void cancelsOverruleByAPlayerWhoCouldWinWithIt() {
        table.points(table.sol, 8);
        table.test.place("18", "000");
        table.sol.addPlanet("mr");

        assertThat(cancels("Overrule")).isTrue();
    }

    @Test
    void cancelsAnImperialRiderFromAContender() {
        table.points(table.sol, 8);

        assertThat(cancels("Imperial Rider")).isTrue();
    }

    // The rider is on the outcome that the votes already cast carry, and its owner is a point from winning: the
    // card is worth the game, whatever the seat's keep floor.
    @Test
    void cancelsAnImperialRiderThatCannotBeVotedDownFromARivalAtMatchPoint() {
        table.third("xxcha", "red");
        table.points(table.sol, 9);
        table.game.setPhaseOfGame("agendaVoting");
        table.game.setCurrentAgendaInfo("Directive_For/Against_7_arms_reduction");
        table.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":0,\"red\":8,\"black\":0}");
        table.game.setCurrentAgendaVote("for", "red_8;blue_Imperial Rider");

        assertThat(cancels("Imperial Rider")).isTrue();
    }

    // ---- the reserve -------------------------------------------------------------------------

    // While anyone could win, the last Sabotage is kept for what threatens a point.
    @Test
    void keepsItsLastSabotageForAContendersImportantCards() {
        table.points(table.sol, 8);

        table.test.isActive(table.sol, "action");

        assertThat(SabotagePolicy.marginalKeep(table.game, table.nekro)).isEqualTo(SabotagePolicy.CONTENDER_RESERVE);
        assertThat(cancels("Mining Initiative")).isFalse();
        assertThat(cancels("Frontline Deployment")).isFalse();
        assertThat(cancels("Overrule")).isTrue();
    }

    @Test
    void aSpareSabotageIsWorthLessThanTheReserve() {
        table.points(table.sol, 8);
        table.nekro.setActionCard("sabo2", 2);

        assertThat(SabotagePolicy.marginalKeep(table.game, table.nekro))
                .isCloseTo(0.6 * ActionCardPrior.keepPrior("sabo1"), within(EXACT));
    }

    @Test
    void theContenderReserveOnlyAppliesWhileSomeoneCouldWin() {
        overruleIsPlayed();

        assertThat(SabotagePolicy.marginalKeep(table.game, table.nekro))
                .isCloseTo(ActionCardPrior.keepPrior("sabo1"), within(EXACT));
    }

    @Test
    void theLastSabotageIsWorthTheOverruleReserveWhileOverruleIsUnseen() {
        assertThat(SabotagePolicy.marginalKeep(table.game, table.nekro, "Mining Initiative"))
                .isCloseTo(SabotagePolicy.OVERRULE_RESERVE, within(EXACT));
    }

    // Cancelling Overrule is what the reserve is for, so it is held to the Sabotage's ordinary worth.
    @Test
    void cancellingOverruleItselfIsExemptFromTheReserve() {
        assertThat(SabotagePolicy.marginalKeep(table.game, table.nekro, "Overrule"))
                .isCloseTo(ActionCardPrior.keepPrior("sabo1"), within(EXACT));
    }

    @Test
    void aContenderReserveStillOutweighsTheOverruleReserve() {
        table.points(table.sol, 8);

        assertThat(SabotagePolicy.marginalKeep(table.game, table.nekro, "Mining Initiative"))
                .isCloseTo(SabotagePolicy.CONTENDER_RESERVE, within(EXACT));
    }

    @Test
    void aSpareSabotageIsNotHeldBackForOverrule() {
        table.nekro.setActionCard("sabo2", 2);

        assertThat(SabotagePolicy.marginalKeep(table.game, table.nekro, "Mining Initiative"))
                .isCloseTo(0.6 * ActionCardPrior.keepPrior("sabo1"), within(EXACT));
    }

    @Test
    void overruleIsSeenOncePlayedDiscardedPurgedOrHeldByTheSeat() {
        assertThat(SabotagePolicy.overruleUnseen(table.game, table.nekro)).isTrue();

        table.nekro.setActionCard("overrule", 5);
        assertThat(SabotagePolicy.overruleUnseen(table.game, table.nekro)).isFalse();
        table.nekro.getActionCards().remove("overrule");
        assertThat(SabotagePolicy.overruleUnseen(table.game, table.nekro)).isTrue();

        table.game.setStoredValue("removeOverrule", "true");
        assertThat(SabotagePolicy.overruleUnseen(table.game, table.nekro)).isFalse();
        table.game.removeStoredValue("removeOverrule");
        assertThat(SabotagePolicy.overruleUnseen(table.game, table.nekro)).isTrue();

        table.game.getDiscardActionCards().put("overrule", 7);
        assertThat(SabotagePolicy.overruleUnseen(table.game, table.nekro)).isFalse();
        table.game.getDiscardActionCards().remove("overrule");
        assertThat(SabotagePolicy.overruleUnseen(table.game, table.nekro)).isTrue();

        overruleIsPlayed();
        assertThat(SabotagePolicy.overruleUnseen(table.game, table.nekro)).isFalse();
    }

    // A card aimed at the seat is worth 1.2 x its appetite whatever the reserve, so Public Disgrace clears 3.
    @Test
    void aCardAimedAtTheSeatStillClearsTheOverruleReserve() {
        table.test.isActive(table.nekro, "strategy");

        assertThat(SabotagePolicy.overruleUnseen(table.game, table.nekro)).isTrue();
        assertThat(cancels("Public Disgrace")).isTrue();
    }

    // ---- the points the seat would lose --------------------------------------------------------

    // Public Disgrace takes the seat's pick. With Imperial and Mecatol Rex picked, the pick would win the game.
    @Test
    void alwaysCancelsAPublicDisgraceThatWouldCostItTheWin() {
        table.points(table.nekro, 9);
        table.test.isActive(table.nekro, "strategy");
        table.nekro.addSC(8);
        table.test.place("18", "000");
        table.nekro.addPlanet("mr");

        assertThat(cancels("Public Disgrace")).isTrue();
        assertThat(SabotagePolicy.cancelValue(table.test.context(), "Public Disgrace", table.sol))
                .isGreaterThanOrEqualTo(ScoringStakes.WIN_VALUE);
    }

    // Aimed at the seat, Public Disgrace is worth cancelling for what humans do with it alone.
    @Test
    void cancelsAPublicDisgraceAimedAtItEvenWhenItCostsNothing() {
        table.test.isActive(table.nekro, "strategy");

        assertThat(cancels("Public Disgrace")).isTrue();
    }

    // Sol defends a planet with Parley while Nekro invades it, and taking it completes Expand Borders.
    @Test
    void cancelsAParleyAgainstAnInvasionThatCompletesAnObjective() {
        table.reveal("expand_borders");
        List<String> held = List.of("abyz", "fria", "wellon", "lazar", "sakulag");
        String[] tiles = {"38", "38", "19", "31", "31"};
        String[] spots = {"201", "201", "202", "203", "203"};
        for (int i = 0; i < held.size(); i++) {
            if (table.game.getTileByPosition(spots[i]) == null) table.system(tiles[i], spots[i]);
            table.nekro.addPlanet(held.get(i));
        }
        Tile front = table.system("37", "204");
        table.test.units(front, "arinam", table.nekro, UnitType.Infantry, 2);
        table.test.units(front, "arinam", table.sol, UnitType.Infantry, 1);
        table.game.setActiveSystem("204");

        double invaded = SabotagePolicy.cancelValue(table.test.context(), "Parley", table.sol);
        table.game.setActiveSystem("");
        double elsewhere = SabotagePolicy.cancelValue(table.test.context(), "Parley", table.sol);

        assertThat(invaded - elsewhere).isGreaterThanOrEqualTo(ScoringStakes.scoreValue(table.game, table.nekro, 1));
    }

    // ---- the guards --------------------------------------------------------------------------

    @Test
    void neverCancelsItsOwnCard() {
        assertThat(SabotagePolicy.cancelWith(
                        table.test.context(window("Overrule", "nekro")), window("Overrule", "nekro")))
                .isEmpty();
    }

    @Test
    void neverCancelsWithoutASabotage() {
        table.nekro.getActionCards().clear();

        assertThat(cancels("Overrule")).isFalse();
    }

    @Test
    void neverCancelsWhenOverTheHandLimit() {
        for (int card = 2; card < 12; card++) table.nekro.setActionCard("intercept" + card, card);

        assertThat(cancels("Overrule")).isFalse();
    }

    @Test
    void neverCancelsACardThatCannotBeCanceled() {
        table.points(table.sol, 9);

        assertThat(cancels("Black Market Dealings")).isFalse();
        assertThat(cancels("Sabotage")).isFalse();
    }

    @Test
    void neverCancelsWhenShameixsBaneBlocksIt() {
        Player spy = Mockito.spy(table.sol);
        Mockito.doReturn(true).when(spy).hasUnlockedBreakthrough("toldarbthonor");
        table.game.getPlayers().put(spy.getUserID(), spy);
        table.points(table.nekro, 5);

        assertThat(cancels("Overrule")).isFalse();
    }

    @Test
    void aLegacySeatNeverCancels() {
        ti4.ai.AiSettings.markLegacyActionCards(table.nekro);

        assertThat(cancels("Overrule")).isFalse();
    }

    // ---- when it answers ---------------------------------------------------------------------

    private AiDecision decide(AiPrompt... prompts) {
        return SabotageRules.answerWindows(table.test.contextAt(LATE, prompts)).orElseThrow();
    }

    // The seat answers at the same time whichever button it presses, so the timing never reveals its hand.
    @Test
    void waitsTheSameWhateverItWillPress() {
        AiPrompt overrule = window("Overrule", "sol");
        AiPrompt mining = window("Mining Initiative", "sol");

        Optional<AiDecision> cancelLater = SabotageRules.answerWindows(table.test.context(overrule));
        Optional<AiDecision> passLater = SabotageRules.answerWindows(table.test.context(mining));
        table.nekro.getActionCards().clear();
        Optional<AiDecision> withoutLater = SabotageRules.answerWindows(table.test.context(overrule));

        assertThat(cancelLater).containsInstanceOf(AiDecision.Wait.class);
        assertThat(((AiDecision.Wait) cancelLater.get()).untilMillis())
                .isEqualTo(((AiDecision.Wait) withoutLater.get()).untilMillis());
        assertThat(passLater).containsInstanceOf(AiDecision.Wait.class);
    }

    @Test
    void pressesTheSabotageButtonInsteadOfNoSabotage() {
        table.nekro.setActionCard("sabo1", 1);

        AiDecision decision = decide(window("Overrule", "sol"));

        assertThat(AiTestGame.pressedId(decision)).isEqualTo("sabotage_ac_Overrule_sol");
        table.nekro.getActionCards().clear();
        assertThat(AiTestGame.pressedId(decide(window("Overrule", "sol")))).isEqualTo("no_sabotage");
    }

    // A window can be closed by another player between two ticks: the seat looks again right before pressing.
    @Test
    void doesNotPressOnAWindowThatHasClosed() {
        messages.when(() -> GameMessageManager.getOne(anyString(), anyString())).thenReturn(Optional.empty());

        assertThat(SabotageRules.answerWindows(table.test.contextAt(LATE, window("Overrule", "sol"))))
                .isEmpty();
    }

    // The window scrolled out of the messages the AI reads after it was seen: it still cancels, from the button it
    // remembered.
    @Test
    void cancelsAWindowThatScrolledOutAfterItWasSeen() {
        long created = NOW - 3_600_000L;
        String windowId = String.valueOf(net.dv8tion.jda.api.utils.TimeUtil.getDiscordTimestamp(created));
        table.game.setMainChannelID("777");
        AiPrompt seen = prompt(windowId, PromptSource.PUBLIC, created, "no_sabotage", "sabotage_ac_Overrule_sol");
        GameMessage open = new GameMessage(windowId, GameMessageType.ACTION_CARD, new LinkedHashSet<>(), 0L);
        messages.when(() -> GameMessageManager.getOne(anyString(), anyString())).thenReturn(Optional.of(open));
        messages.when(() -> GameMessageManager.getAll(anyString(), Mockito.eq(GameMessageType.ACTION_CARD)))
                .thenReturn(List.of(open));

        SabotageRules.answerWindows(table.test.contextAt(created + 1_000L, seen));
        AiDecision later =
                SabotageRules.answerWindows(table.test.contextAt(NOW)).orElseThrow();

        assertThat(AiTestGame.pressedId(later)).isEqualTo("sabotage_ac_Overrule_sol");
    }

    // ---- before it carries on ----------------------------------------------------------------

    // Parley is played against its own invasion: the seat answers before it rolls the next combat dice.
    @Test
    void answersAWindowAimedAtItBeforeContinuingACombat() {
        table.reveal("expand_borders");
        Tile front = table.system("37", "204");
        table.test.units(front, "arinam", table.nekro, UnitType.Infantry, 2);
        table.test.units(front, "arinam", table.sol, UnitType.Infantry, 1);
        table.game.setActiveSystem("204");
        table.points(table.sol, 3);
        AiPrompt parley = window("Parley", "sol");
        AiPrompt roll = prompt("roll", PromptSource.COMBAT_THREAD, NOW, "combatRoll_204_Planet_arinam");

        Optional<AiDecision> early = SabotageRules.answerWindowsAimedAtSeat(table.test.contextAt(LATE, roll, parley));
        AiDecision overall = new NekroBrain().decide(table.test.contextAt(LATE, roll, parley));

        assertThat(early).isPresent();
        assertThat(AiTestGame.pressedId(early.get())).isIn("no_sabotage", "sabotage_ac_Parley_sol");
        assertThat(AiTestGame.pressedId(overall)).isIn("no_sabotage", "sabotage_ac_Parley_sol");
    }

    @Test
    void leavesAWindowNotAimedAtItToItsUsualPlace() {
        AiPrompt rider = window("Technology Rider", "sol");

        assertThat(SabotageRules.answerWindowsAimedAtSeat(table.test.contextAt(LATE, rider)))
                .isEmpty();
    }

    // Coup d'Etat is aimed at the seat's turn: once it has answered, it keeps the turn until the others have too.
    @Test
    void holdsItsTurnWhileAWindowAimedAtItIsStillOpen() {
        table.third("hacan", "yellow");
        AiPrompt coup = window("Coup d'Etat", "sol");

        windowOpenFor("nekro");
        Optional<AiDecision> held = SabotageRules.answerWindowsAimedAtSeat(table.test.contextAt(LATE, coup));
        assertThat(held).containsInstanceOf(AiDecision.Wait.class);

        windowOpenFor("nekro", "hacan");
        assertThat(SabotageRules.answerWindowsAimedAtSeat(table.test.contextAt(LATE, coup)))
                .isEmpty();
    }

    // ---- follow-ups --------------------------------------------------------------------------

    @Test
    void pressesCrisisSabotagedWhenCrisisWasCancelled() {
        table.game.getGameStats().recordAcPlay("Crisis", table.sol);
        table.game.getGameStats().markLatestPlayCanceled("Crisis");
        AiPrompt crisis = AiTestGame.withContent(
                prompt("crisis", PromptSource.PUBLIC, NOW, "FFCC_nekro_turnEnd", "crisisSabotaged_black"),
                table.nekro.getRepresentation() + ", please resolve _Crisis_.");

        assertThat(AiTestGame.pressedId(
                        SabotageRules.followUp(table.test.context(crisis)).orElseThrow()))
                .isEqualTo("crisisSabotaged_black");
    }

    @Test
    void doesNotPressCrisisSabotagedForACrisisThatStands() {
        table.game.getGameStats().recordAcPlay("Crisis", table.sol);
        AiPrompt crisis = prompt("crisis", PromptSource.PUBLIC, NOW, "FFCC_nekro_turnEnd", "crisisSabotaged_black");

        assertThat(SabotageRules.followUp(table.test.context(crisis))).isEmpty();
    }
}
