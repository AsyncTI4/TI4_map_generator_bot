package ti4.ai.nekro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.AiSettings;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiMemory;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.ai.profile.AggressionLevel;
import ti4.ai.profile.AiProfile;
import ti4.ai.scoring.PaymentRules;
import ti4.ai.trade.TradeCardRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

class NekroBrainTest extends BaseTi4Test {

    private static final long NOW = 1_800_000_000_000L;
    private static final String AI_ID = "7100000123456789";

    private final NekroBrain brain = new NekroBrain();
    private final AiMemory memory = new AiMemory();
    private Game game;
    private Player nekro;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("nekro-brain-test");
        game.setRound(1);
        nekro = game.addPlayer(AI_ID, "Nekro AI");
        nekro.setFaction("nekro");
        nekro.setColor("black");
        Player sol = game.addPlayer("200000000000000001", "Human");
        sol.setFaction("sol");
        sol.setColor("blue");
    }

    // ---- strategy phase ---------------------------------------------------------------------

    @Test
    void picksTheHighestValueStrategyCardIncludingTradeGoods() {
        becomeActive("strategy");
        game.setScTradeGood(4, 3);
        AiPrompt picks = publicPrompt("picks", NOW, scPickButtons());

        AiDecision decision = decide(picks);

        assertPressed(decision, "FFCC_nekro_scPick_4");
    }

    @Test
    void ignoresPickButtonsOwnedBySomeoneElse() {
        becomeActive("strategy");
        AiPrompt picks = publicPrompt("picks", NOW, "FFCC_sol_scPick_1", "FFCC_sol_scPick_2");

        assertThat(decide(picks)).isInstanceOf(AiDecision.Idle.class);
    }

    // Under the Checks and Balances law the picker gives its card away. The pick and the hand-off are planned
    // together: while the player with the most points can still receive a card, it is handed the card least useful
    // to it.
    @Test
    void handsTheLeaderItsLeastUsefulCardUnderChecksAndBalances() {
        game.setStrategyCardsPerPlayer(1);
        game.addLaw("checks", null);
        becomeActive("strategy");
        Player hacan = game.addPlayer("200000000000000002", "Hacan");
        hacan.setFaction("hacan");
        hacan.setColor("yellow");
        game.scorePublicObjective(hacan.getUserID(), game.addCustomPO("Test points", 3));
        AiPrompt picks = publicPrompt("picks", NOW, scPickButtons());

        AiDecision pick = decide(picks);

        assertThat(pick).isInstanceOf(AiDecision.Press.class);
        String picked = ((AiDecision.Press) pick).button().handlerId();
        String card = picked.substring(picked.lastIndexOf('_') + 1);
        AiPrompt giveAway = publicPrompt(
                "give", NOW, "checksNBalancesPt2_" + card + "_sol", "checksNBalancesPt2_" + card + "_hacan");
        assertPressed(decide(giveAway), "checksNBalancesPt2_" + card + "_hacan");
    }

    // In 3 and 4 player games every seat picks two strategy cards.
    @Test
    void picksASecondStrategyCardWhenAskedAgain() {
        becomeActive("strategy");
        nekro.addSC(1);
        AiPrompt picks = publicPrompt("picks", NOW, "FFCC_nekro_scPick_2", "FFCC_nekro_scPick_8");

        assertPressed(decide(picks), "FFCC_nekro_scPick_8");
    }

    // Public Disgrace returns the card and leaves the pick buttons up; the AI must pick something else.
    @Test
    void picksTheNextBestCardAfterAPickBounced() {
        becomeActive("strategy");
        AiPrompt picks = publicPrompt("picks", NOW, "FFCC_nekro_scPick_2", "FFCC_nekro_scPick_8");

        AiDecision decision = decide(Set.of("picks|FFCC_nekro_scPick_8"), picks);

        assertPressed(decision, "FFCC_nekro_scPick_2");
    }

    // ---- action phase -----------------------------------------------------------------------

    @Test
    void playsItsStrategyCardBeforeAnythingElse() {
        becomeActive("action");
        nekro.addSC(1);
        AiPrompt turn = publicPrompt(
                "turn", NOW, "FFCC_nekro_tacticalAction", "FFCC_nekro_componentAction", "FFCC_nekro_strategicAction_1");

        assertPressed(decide(turn), "FFCC_nekro_strategicAction_1");
    }

    @Test
    void endsTheTurnOnceAnEndTurnButtonAppears() {
        becomeActive("action");
        nekro.addSC(1);
        game.setSCPlayed(1, true);
        AiPrompt end = publicPrompt("end", NOW, "FFCC_nekro_endOfTurnAbilities", "FFCC_nekro_doAnotherAction");

        assertPressed(decide(end), "FFCC_nekro_endOfTurnAbilities");
    }

    @Test
    void passesWhenItsStrategyCardHasBeenPlayed() {
        becomeActive("action");
        nekro.addSC(1);
        game.setSCPlayed(1, true);
        AiPrompt turn = publicPrompt(
                "turn", NOW, "FFCC_nekro_tacticalAction", "FFCC_nekro_componentAction", "FFCC_nekro_passForRound");

        assertPressed(decide(turn), "FFCC_nekro_passForRound");
    }

    // Old turn messages can linger in history; acting on them could end a fresh turn by accident.
    @Test
    void ignoresTurnButtonsPostedBeforeItsTurnStarted() {
        becomeActive("action");
        AiPrompt stale = publicPrompt("stale", NOW - 3_600_000L, "FFCC_nekro_endOfTurnAbilities");

        assertThat(decide(stale)).isInstanceOf(AiDecision.Idle.class);
    }

    @Test
    void doesNotTakeTurnsWhenSomeoneElseIsActive() {
        game.setPhaseOfGame("action");
        game.setActivePlayerID("200000000000000001");
        game.setLastActivePlayerChange(new Date(NOW - 1000));
        AiPrompt turn = publicPrompt("turn", NOW, "FFCC_nekro_passForRound");

        assertThat(decide(turn)).isInstanceOf(AiDecision.Idle.class);
    }

    // ---- reaction windows -------------------------------------------------------------------

    @Test
    void declinesToFollowOtherPlayersStrategyCards() {
        game.setPhaseOfGame("action");
        game.setSCPlayed(3, true);
        AiPrompt card = publicPrompt("card", NOW, "sc_follow_3", "sc_no_follow_3");

        assertPressed(decide(card), "sc_no_follow_3");

        nekro.addFollowedSC(3);
        assertThat(decide(card)).isInstanceOf(AiDecision.Idle.class);
    }

    // Following Leadership pays with influence, so it cannot start while another payment is open. Declining it then
    // would lose the follow for good; the seat waits instead, and declines only once it is free to decide.
    @Test
    void waitsInsteadOfDecliningLeadershipWhileStillPaying() {
        game.setPhaseOfGame("action");
        game.setSCPlayed(1, true);
        AiPrompt card = publicPrompt("card", NOW, "sc_follow_1", "sc_no_follow_1");
        PaymentRules.expectNothing(context(Set.of()), "a technology", PaymentRules.TECHNOLOGY_DONE);

        assertThat(decide(card)).isInstanceOf(AiDecision.Idle.class);

        PaymentRules.forget(context(Set.of()));
        assertPressed(decide(card), "sc_no_follow_1");
    }

    @Test
    void declinesWhensAndAftersOnlyOnce() {
        game.setPhaseOfGame("agendawaiting");
        AiPrompt whens = hiddenPrompt("whens", NOW, "queueAWhen", "declineToQueueAWhen");

        assertPressed(decide(whens), "declineToQueueAWhen");

        game.setStoredValue("declinedWhens", "nekro_");
        assertThat(decide(whens)).isInstanceOf(AiDecision.Idle.class);
    }

    // ---- trading ----------------------------------------------------------------------------

    // Nekro's home at 301 with Sol's destroyer next door, so they are neighbours, and Hacan (who may trade with
    // anyone) holding commodities, so Nekro's own commodities have another outlet. Nekro is active in the action
    // phase; both have 3 commodities.
    private static AiTestGame tradingTable() {
        AiTestGame table = new AiTestGame();
        table.nekroHome();
        Tile beside = table.place("46", AiTestGame.neighbourOf(AiTestGame.HOME));
        table.units(beside, "space", table.sol, UnitType.Destroyer, 1);
        table.addSeat("7100000555555555", "hacan", "red").setCommodities(6);
        table.aiIsActive("action");
        table.nekro.setCommodities(3);
        table.sol.setCommodities(3);
        return table;
    }

    /** Sol's offer number {@code number} to Nekro, as it shows in Nekro's thread. */
    private static AiPrompt solOffers(AiTestGame table, String id, long created, int number, String... items) {
        table.sol.getTransactionItems().clear();
        for (String item : items) table.sol.addTransactionItem(item);
        table.game.setStoredValue("offerFromsolTonekro", String.valueOf(number));
        return prompt(
                id,
                PromptSource.AI_THREAD,
                created,
                "acceptOffer_blue_" + number,
                "rejectOffer_blue",
                "resetOffer_blue");
    }

    private static final String[] EVEN_WASH = {"sendingsol_receivingnekro_Comms_3", "sendingnekro_receivingsol_Comms_3"
    };

    // Offers are answered on value by the trading rules: an even wash with a peer is worth accepting.
    @Test
    void answersOffersThroughTheTradingRules() {
        AiTestGame table = tradingTable();
        AiPrompt wash = solOffers(table, "wash", NOW, 1, EVEN_WASH);

        assertPressed(brain.decide(table.context(wash)), "acceptOffer_blue_1");
    }

    // Answering an offer comes before handing a card to a Spy and before ending the turn; finishing a half-built offer
    // also comes before ending the turn; and withdrawing a crossed offer comes before answering the new one.
    @Test
    void ordersTheTradingRulesAroundTheOthers() {
        AiTestGame table = tradingTable();
        table.sol.setTg(4);
        table.nekro.setActionCard("sabo1", 9);
        AiPrompt end = prompt("end", PromptSource.PUBLIC, NOW - 500, "FFCC_nekro_turnEnd");
        AiPrompt spy = hiddenPrompt("spy", NOW - 500, "spyStep3_sol");
        // Sol asks for Nekro's 3 commodities for 2 trade goods; Nekro counters with 3 for 3.
        AiPrompt ask = solOffers(
                table, "ask", NOW - 500, 1, "sendingnekro_receivingsol_Comms_3", "sendingsol_receivingnekro_TGs_2");
        long now = NOW;
        assertPressed(brain.decide(table.contextAt(now, ask, spy, end)), "resetOffer_blue");
        table.nekro.getActionCards().clear();

        String[] steps = {
            "getNewTransaction_blue_black",
            "newTransact_TGs_blue_black",
            "offerToTransact_TGs_blue_black_3",
            "getNewTransaction_black_blue",
            "newTransact_Comms_black_blue",
            "offerToTransact_Comms_black_blue_3",
            "sendOffer_blue"
        };
        String[][] screens = {
            builder("offering", "black", "blue", "Comms"),
            builder("asking", "blue", "black", "TGs"),
            picker("TGs", "blue", "black", 4),
            builder("asking", "blue", "black", "TGs"),
            builder("offering", "black", "blue", "Comms"),
            picker("Comms", "black", "blue", 3),
            builder("offering", "black", "blue", "Comms")
        };
        for (int step = 0; step < steps.length; step++) {
            now += 3_000L;
            AiPrompt screen = hiddenPrompt("step-" + step, now - 2_000L, screens[step]);
            assertPressed(brain.decide(table.contextAt(now, screen, end)), steps[step]);
            if (steps[step].startsWith("offerToTransact_")) table.nekro.addTransactionItem(itemFor(steps[step]));
        }
        table.game.setStoredValue("offerFromnekroTosol", "1");

        now += 3_000L;
        AiPrompt mine = hiddenPrompt("mine", now - 2_000L, "rescindOffer_blue");
        AiPrompt another = solOffers(table, "another", now - 1_000L, 2, EVEN_WASH);
        assertPressed(brain.decide(table.contextAt(now, mine, another, end)), "rescindOffer_blue");
    }

    // Nekro played Trade and Hacan followed it, and Nekro could also wash with Sol at the start of its turn. The Trade
    // bill comes first (settleTrade before startDeals), the half-built bill is finished before any new deal
    // (continueDraft first), and both come before ending the turn (takeTurn last).
    @Test
    void ordersDraftsBillsAndOwnDealsBeforeTheTurn() {
        AiTestGame table = tradingTable();
        int trade = 5;
        table.nekro.addSC(trade);
        table.game.setSCPlayed(trade, true);
        TradeCardRules.recordPlay(table.context());
        table.game.setStoredValue("followedSC" + trade + "_" + table.game.getRound(), "_hacan");
        AiPrompt entry = hiddenPrompt("various", NOW - 60_000L, "cardsInfo", "transaction");
        AiPrompt end = prompt("end", PromptSource.PUBLIC, NOW - 500, "FFCC_nekro_turnEnd");

        assertPressed(brain.decide(table.contextAt(NOW, entry, end)), "transaction");

        AiPrompt picker =
                hiddenPrompt("picker", NOW + 1_000L, "FFCC_nekro_transactWith_sol", "FFCC_nekro_transactWith_hacan");
        assertPressed(brain.decide(table.contextAt(NOW + 3_000L, picker, end)), "FFCC_nekro_transactWith_hacan");
    }

    // With -Dai.trading=false the AI declines every offer, as before trading existed.
    @Test
    void declinesOffersWithTradingSwitchedOff() {
        AiTestGame table = tradingTable();
        AiPrompt wash = solOffers(table, "wash", NOW, 1, EVEN_WASH);
        System.setProperty(AiSettings.TRADING_PROPERTY, "false");
        try {
            assertPressed(brain.decide(table.context(wash)), "rejectOffer_blue");
        } finally {
            System.clearProperty(AiSettings.TRADING_PROPERTY);
        }
    }

    /** The bot's offer builder between {@code sender} and {@code receiver}, with the rows this test needs. */
    private static String[] builder(String mode, String sender, String receiver, String row) {
        String partner = "black".equals(sender) ? receiver : sender;
        String pair = sender + "_" + receiver;
        return new String[] {
            "newTransact_" + row + "_" + pair,
            "newTransact_SendDebt_" + pair,
            "offering".equals(mode)
                    ? "newTransact_Details_" + pair + "_~MDL"
                    : "newTransact_DetailsInvert_" + pair + "_~MDL",
            "resetOffer_" + partner,
            "getNewTransaction_" + receiver + "_" + sender,
            "sendOffer_" + partner,
            "deleteButtons"
        };
    }

    private static String[] picker(String type, String sender, String receiver, int most) {
        String[] ids = new String[most];
        for (int amount = 1; amount <= most; amount++) {
            ids[amount - 1] = "offerToTransact_" + type + "_" + sender + "_" + receiver + "_" + amount;
        }
        return ids;
    }

    /** The transaction item the bot stores when the AI presses a picker button. */
    private static String itemFor(String pick) {
        String[] fields = pick.split("_");
        String sender = "black".equals(fields[2]) ? "nekro" : "sol";
        String receiver = "black".equals(fields[3]) ? "nekro" : "sol";
        return "sending" + sender + "_receiving" + receiver + "_" + fields[1] + "_" + fields[4];
    }

    // ---- status phase -----------------------------------------------------------------------

    @Test
    void declinesPublicThenSecretScoringWhenNothingQualifies() {
        game.setPhaseOfGame("statusScoring");
        AiPrompt scoring =
                publicPrompt("scoring", NOW, "po_scoring_1", "po_no_scoring", "get_so_score_buttons", "so_no_scoring");

        assertPressed(decide(scoring), "po_no_scoring");

        game.setStoredValue("nekroround1PO", "None");
        assertPressed(decide(scoring), "so_no_scoring");

        game.setStoredValue("nekroround1SO", "None");
        assertThat(decide(scoring)).isInstanceOf(AiDecision.Idle.class);
    }

    @Test
    void startsStatusHomeworkThenGainsTokensUntilTheAllowanceIsUsed() {
        game.setPhaseOfGame("statusHomework");
        AiPrompt homework = publicPrompt("homework", NOW, "redistributeCCButtons", "pass_on_abilities");
        assertPressed(decide(homework), "redistributeCCButtons");

        game.setStoredValue("statusHomeworkReactionFornekroRound1", "added");
        nekro.setTacticalCC(3);
        nekro.setFleetCC(3);
        nekro.setStrategicCC(2);
        game.setStoredValue("originalCCsFornekro", nekro.getCCRepresentation());
        AiPrompt gain = hiddenPrompt(
                "gain",
                NOW + 1000,
                "FFCC_nekro_increase_tactic_cc",
                "FFCC_nekro_increase_fleet_cc",
                "FFCC_nekro_increase_strategy_cc",
                "FFCC_nekro_deleteButtons");
        assertPressed(decide(homework, gain), "FFCC_nekro_increase_tactic_cc");

        nekro.setTacticalCC(5);
        AiDecision done = decide(homework, gain);
        assertPressed(done, "FFCC_nekro_deleteButtons");
        assertThat(((AiDecision.Press) done).button().label()).startsWith("Done");
    }

    // A third strategy token is surplus while tactics are short, so it is moved back during redistribution and the
    // freed token is gained again as a tactic token: the seat starts the round able to act.
    @Test
    void movesSurplusStrategyTokensIntoTacticsWhileRedistributing() {
        game.setPhaseOfGame("statusHomework");
        AiPrompt homework = publicPrompt("homework", NOW, "redistributeCCButtons", "pass_on_abilities");
        game.setStoredValue("statusHomeworkReactionFornekroRound1", "added");
        nekro.setTacticalCC(0);
        nekro.setFleetCC(3);
        nekro.setStrategicCC(3);
        game.setStoredValue("originalCCsFornekro", nekro.getCCRepresentation());
        AiPrompt gain = hiddenPrompt(
                "gain",
                NOW + 1000,
                "FFCC_nekro_increase_tactic_cc",
                "FFCC_nekro_increase_fleet_cc",
                "FFCC_nekro_increase_strategy_cc",
                "FFCC_nekro_decrease_tactic_cc",
                "FFCC_nekro_decrease_fleet_cc",
                "FFCC_nekro_decrease_strategy_cc",
                "FFCC_nekro_deleteButtons");

        nekro.setTacticalCC(2);
        assertPressed(decide(homework, gain), "FFCC_nekro_decrease_strategy_cc");

        nekro.setStrategicCC(2);
        assertPressed(decide(homework, gain), "FFCC_nekro_increase_tactic_cc");

        nekro.setTacticalCC(3);
        assertPressed(decide(homework, gain), "FFCC_nekro_deleteButtons");
    }

    // ---- secrets ----------------------------------------------------------------------------

    @Test
    void discardsDownToOneSecretAtTheStartOfTheGame() {
        nekro.setSecret("survey");
        nekro.setSecret("prove_endurance");
        List<String> discardIds = nekro.getSecretsUnscored().values().stream()
                .map(index -> "discardSecret_" + index)
                .toList();
        AiPrompt discard = hiddenPrompt("discard", NOW, discardIds.toArray(String[]::new));

        AiDecision decision = decide(discard);

        assertThat(decision).isInstanceOf(AiDecision.Press.class);
        assertThat(((AiDecision.Press) decision).button().handlerId()).isIn(discardIds);

        nekro.removeSecret(nekro.getSecretsUnscored().values().iterator().next());
        assertThat(decide(discard)).isInstanceOf(AiDecision.Idle.class);
    }

    // Every real game reveals the Custodians token as a custom public objective at creation, which must not count
    // as the first objectives being revealed, or the AI would keep both starting secrets and round 1 never starts.
    @Test
    void stillDiscardsAStartingSecretWhenCustodiansIsRevealed() {
        game.addCustomPO("Custodians", 1);
        nekro.setSecret("survey");
        nekro.setSecret("prove_endurance");
        List<String> discardIds = nekro.getSecretsUnscored().values().stream()
                .map(index -> "discardSecret_" + index)
                .toList();

        AiDecision decision = decide(hiddenPrompt("discard", NOW, discardIds.toArray(String[]::new)));

        assertThat(decision).isInstanceOf(AiDecision.Press.class);
        assertThat(((AiDecision.Press) decision).button().handlerId()).isIn(discardIds);
    }

    // ---- sabotage ---------------------------------------------------------------------------

    // It answers every sabotage window after the same seeded delay, whether or not it holds a Sabotage, so the
    // timing never reveals its hand.
    @Test
    void passesOnSabotageAfterADelayWhateverItsHand() {
        try (MockedStatic<GameMessageManager> messages = Mockito.mockStatic(GameMessageManager.class)) {
            messages.when(() -> GameMessageManager.getOne(anyString(), anyString()))
                    .thenAnswer(
                            call -> Optional.of(new GameMessage(call.getArgument(1), GameMessageType.ACTION_CARD, 0L)));
            AiPrompt fresh = publicPrompt("fresh", NOW, "no_sabotage");
            assertThat(decide(fresh)).isInstanceOf(AiDecision.Wait.class);

            AiPrompt old = publicPrompt("old", NOW - 3_600_000L, "no_sabotage");
            assertPressed(decide(old), "no_sabotage");

            nekro.setActionCard("sabo1");
            assertPressed(decide(old), "no_sabotage");
        }
    }

    // With no human at the table there is nothing to hide, so an all-AI game answers a sabotage window within seconds
    // instead of the 10-40 minutes that stop a human reading the AI's hand from its timing.
    @Test
    void passesOnSabotageWithinSecondsWhenNoHumanPlays() {
        AiTestGame table = AiTestGame.withSolAi();
        try (MockedStatic<GameMessageManager> messages = Mockito.mockStatic(GameMessageManager.class)) {
            messages.when(() -> GameMessageManager.getOne(anyString(), anyString()))
                    .thenAnswer(
                            call -> Optional.of(new GameMessage(call.getArgument(1), GameMessageType.ACTION_CARD, 0L)));
            AiPrompt window = publicPrompt("window", NOW - 30_000L, "no_sabotage");

            assertPressed(brain.decide(table.contextFor(table.nekro, Set.of(), NOW, window)), "no_sabotage");
        }
    }

    // In a busy channel (the start of the strategy phase, when Summit is played) a sabotage window can scroll out of
    // the messages the AI reads before its delay is up. It still answers it, from the bot's list of open windows, or
    // the card's owner would wait forever.
    @Test
    void passesOnASabotageWindowThatScrolledOutOfView() {
        game.setMainChannelID("777");
        String windowId = String.valueOf(net.dv8tion.jda.api.utils.TimeUtil.getDiscordTimestamp(NOW - 3_600_000L));
        GameMessage window = new GameMessage(windowId, GameMessageType.ACTION_CARD, 0L);
        try (MockedStatic<GameMessageManager> messages = Mockito.mockStatic(GameMessageManager.class)) {
            messages.when(() -> GameMessageManager.getAll(anyString(), Mockito.eq(GameMessageType.ACTION_CARD)))
                    .thenReturn(List.of(window));
            messages.when(() -> GameMessageManager.getOne(anyString(), anyString()))
                    .thenReturn(Optional.of(window));

            AiDecision decision = decide();

            assertPressed(decision, "no_sabotage");
            assertThat(((AiDecision.Press) decision).prompt().messageId()).isEqualTo(windowId);
            assertThat(((AiDecision.Press) decision).prompt().channelId()).isEqualTo("777");
        }
    }

    // A window that already resolved (its tracked message is gone) keeps its button; it must not be pressed again.
    @Test
    void leavesClosedSabotageWindowsAlone() {
        try (MockedStatic<GameMessageManager> messages = Mockito.mockStatic(GameMessageManager.class)) {
            messages.when(() -> GameMessageManager.getOne(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            AiPrompt window = publicPrompt("window", NOW - 3_600_000L, "no_sabotage");

            assertThat(decide(window)).isInstanceOf(AiDecision.Idle.class);
        }
    }

    // ---- agendas ----------------------------------------------------------------------------

    // Every supported faction plays with this brain, so abstaining must use the seat's own faction, not "nekro".
    @Test
    void anAiSeatOfAnyFactionAbstainsWithItsOwnButtonOnItsTurn() {
        AiTestGame table = AiTestGame.withSolAi();
        table.isActive(table.sol, "agendavoting");
        AiPrompt vote = AiTestGame.prompt(
                "vote",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_resolveAgendaVote_0",
                "FFCC_sol_resolveAgendaVote_3",
                "FFCC_sol_resolveAgendaVote_0");

        assertPressed(brain.decide(table.contextFor(table.sol, Set.of(), NOW, vote)), "FFCC_sol_resolveAgendaVote_0");
        assertThat(brain.decide(table.contextFor(table.nekro, Set.of(), NOW, vote)))
                .isInstanceOf(AiDecision.Idle.class);
    }

    @Test
    void doesNotAbstainTwiceFromTheSameVotePrompt() {
        AiTestGame table = AiTestGame.withSolAi();
        table.isActive(table.sol, "agendavoting");
        AiPrompt vote = AiTestGame.prompt("vote", PromptSource.PUBLIC, NOW, "FFCC_sol_resolveAgendaVote_0");

        AiDecision decision =
                brain.decide(table.contextFor(table.sol, Set.of("vote|FFCC_sol_resolveAgendaVote_0"), NOW, vote));

        assertThat(decision).isInstanceOf(AiDecision.Idle.class);
    }

    // The speaker decides tied agendas. The tie buttons are posted for the speaker and must only be pressed by it.
    @Test
    void theSpeakerAiBreaksAnAgendaTie() {
        AiTestGame table = AiTestGame.withSolAi();
        table.game.setPhaseOfGame("agendawaiting");
        table.game.setSpeakerUserID(table.sol.getUserID());
        table.isActive(table.sol, "agendawaiting");
        AiPrompt tie = AiTestGame.prompt(
                "tie",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_resolveAgendaVote_outcomeTie* for",
                "resolveAgendaVote_outcomeTie* for",
                "resolveAgendaVote_outcomeTie* against");

        assertPressed(
                brain.decide(table.contextFor(table.sol, Set.of(), NOW, tie)), "resolveAgendaVote_outcomeTie* for");
        assertThat(brain.decide(table.contextFor(table.nekro, Set.of(), NOW, tie)))
                .isInstanceOf(AiDecision.Idle.class);
    }

    @Test
    void theSpeakerPrefersItsOwnTieButtonWhenItComesFirst() {
        AiTestGame table = AiTestGame.withSolAi();
        table.game.setSpeakerUserID(table.sol.getUserID());
        table.isActive(table.sol, "agendawaiting");
        AiPrompt tie = AiTestGame.prompt(
                "tie",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_sol_resolveAgendaVote_outcomeTie* against",
                "resolveAgendaVote_outcomeTie* for");

        assertPressed(
                brain.decide(table.contextFor(table.sol, Set.of(), NOW, tie)),
                "FFCC_sol_resolveAgendaVote_outcomeTie* against");
    }

    // ---- one action per turn ----------------------------------------------------------------

    // Once the AI has pressed its tactical action this turn, the turn buttons stay up; it must not also play its
    // strategy card or pass.
    @Test
    void takesNoSecondActionAfterStartingATacticalAction() {
        AiTestGame table = new AiTestGame();
        table.aiIsActive("action");
        table.nekro.addSC(1);
        AiPrompt turn = AiTestGame.prompt(
                "turn",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_tacticalAction",
                "FFCC_nekro_strategicAction_1",
                "FFCC_nekro_passForRound");

        assertPressed(brain.decide(table.context(turn)), "FFCC_nekro_strategicAction_1");

        AiDecision afterTactical = brain.decide(table.context(Set.of("turn|FFCC_nekro_tacticalAction"), turn));

        assertThat(afterTactical).isInstanceOf(AiDecision.Idle.class);
    }

    @Test
    void doesNotPassAfterPlayingItsStrategyCardThisTurn() {
        AiTestGame table = new AiTestGame();
        table.aiIsActive("action");
        table.nekro.addSC(1);
        table.game.setSCPlayed(1, true);
        AiPrompt turn = AiTestGame.prompt(
                "turn",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_tacticalAction",
                "FFCC_nekro_strategicAction_1",
                "FFCC_nekro_passForRound");

        AiDecision afterStrategic = brain.decide(table.context(Set.of("turn|FFCC_nekro_strategicAction_1"), turn));

        assertThat(afterStrategic).isInstanceOf(AiDecision.Idle.class);
    }

    // Pressing Tactical Action posts the system picker; the one-action gate must still let the AI pick a system.
    @Test
    void picksTheSystemAfterPressingTacticalAction() {
        AiTestGame table = new AiTestGame();
        table.nekroHome();
        table.units(table.game.getTileByPosition(AiTestGame.HOME), "space", table.nekro, UnitType.Carrier, 1);
        table.units(table.game.getTileByPosition(AiTestGame.HOME), "mordaiii", table.nekro, UnitType.Infantry, 2);
        String target = AiTestGame.neighbourOf(AiTestGame.HOME);
        table.place("26", target);
        table.aiIsActive("action");
        AiPrompt turn = AiTestGame.prompt(
                "turn", PromptSource.PUBLIC, NOW - 500, "FFCC_nekro_tacticalAction", "FFCC_nekro_strategicAction_1");
        AiPrompt picker = AiTestGame.prompt(
                "picker", PromptSource.PUBLIC, NOW, "ringTile_" + target, "ringTile_" + AiTestGame.HOME);

        AiDecision decision = brain.decide(table.context(Set.of("turn|FFCC_nekro_tacticalAction"), turn, picker));

        assertThat(decision).isInstanceOf(AiDecision.Press.class);
        assertThat(((AiDecision.Press) decision).button().handlerId()).startsWith("ringTile_");
    }

    // A strategy-card message from an earlier round is still in the channel after status cleanup resets the
    // follows; the AI must not answer it again.
    @Test
    void ignoresFollowButtonsForCardsNotPlayedThisRound() {
        game.setPhaseOfGame("action");
        AiPrompt oldCard = publicPrompt("card", NOW, "sc_follow_3", "sc_no_follow_3");

        assertThat(decide(oldCard)).isInstanceOf(AiDecision.Idle.class);
    }

    // When every voter abstains on an Elect Planet agenda, the speaker first chooses whose planets to pick from.
    @Test
    void speakerChoosesWhosePlanetsBreakAnElectPlanetTie() {
        game.setSpeakerUserID(AI_ID);
        becomeActive("agendawaiting");
        nekro.addPlanet("mordaiii");
        Player sol = game.getPlayer("200000000000000001");
        sol.addPlanet("jord");
        sol.addPlanet("lodor");
        AiPrompt tie = publicPrompt(
                "tie",
                NOW,
                "tiedPlanets_resolveAgendaVote_outcomeTie*_nekro",
                "tiedPlanets_resolveAgendaVote_outcomeTie*_sol");

        assertPressed(decide(tie), "tiedPlanets_resolveAgendaVote_outcomeTie*_sol");
    }

    // After an agenda is resolved by hand, the engine posts tie-style buttons for whoever resolves it; the speaker
    // AI must leave those to the players.
    @Test
    void leavesAManuallyResolvedAgendaAlone() {
        game.setSpeakerUserID(AI_ID);
        game.setPhaseOfGame("agendaEnd");
        AiPrompt manual =
                publicPrompt("manual", NOW, "tiedPlanets_agendaResolution_sol", "resolveAgendaVote_outcomeTie*_jord");

        assertThat(decide(manual)).isInstanceOf(AiDecision.Idle.class);
    }

    @Test
    void stillEndsTheTurnAfterItsAction() {
        AiTestGame table = new AiTestGame();
        table.aiIsActive("action");
        AiPrompt turn = AiTestGame.prompt("turn", PromptSource.PUBLIC, NOW - 500, "FFCC_nekro_tacticalAction");
        AiPrompt end = AiTestGame.prompt("end", PromptSource.PUBLIC, NOW, "FFCC_nekro_turnEnd");

        AiDecision decision = brain.decide(table.context(Set.of("turn|FFCC_nekro_tacticalAction"), turn, end));

        assertPressed(decision, "FFCC_nekro_turnEnd");
    }

    // ---- helpers ----------------------------------------------------------------------------

    private void becomeActive(String phase) {
        game.setPhaseOfGame(phase);
        game.setActivePlayerID(AI_ID);
        game.setLastActivePlayerChange(new Date(NOW - 1000));
    }

    private AiDecision decide(AiPrompt... prompts) {
        return decide(Set.of(), prompts);
    }

    private AiDecision decide(Set<String> pressedKeys, AiPrompt... prompts) {
        return brain.decide(context(pressedKeys, prompts));
    }

    private AiTurnContext context(Set<String> pressedKeys, AiPrompt... prompts) {
        AiProfile profile = new AiProfile(
                "nekro",
                AggressionLevel.OPPORTUNIST,
                AiProfile.AggressionMode.DYNAMIC,
                AiProfile.PauseState.RUNNING,
                7L);
        return new AiTurnContext(game, nekro, profile, List.of(prompts), pressedKeys, memory, NOW);
    }

    private static void assertPressed(AiDecision decision, String customId) {
        assertThat(decision).isInstanceOf(AiDecision.Press.class);
        assertThat(((AiDecision.Press) decision).button().customId()).isEqualTo(customId);
    }

    private static String[] scPickButtons() {
        List<String> ids = new ArrayList<>();
        for (int initiative = 1; initiative <= 8; initiative++) ids.add("FFCC_nekro_scPick_" + initiative);
        return ids.toArray(String[]::new);
    }

    private static AiPrompt publicPrompt(String messageId, long created, String... customIds) {
        return prompt(messageId, PromptSource.PUBLIC, created, customIds);
    }

    private static AiPrompt hiddenPrompt(String messageId, long created, String... customIds) {
        return prompt(messageId, PromptSource.AI_THREAD, created, customIds);
    }

    private static AiPrompt prompt(String messageId, PromptSource source, long created, String... customIds) {
        List<PromptButton> buttons = new ArrayList<>();
        for (int i = 0; i < customIds.length; i++) {
            String label =
                    customIds[i].endsWith("deleteButtons") ? "Done Redistributing Command Tokens" : "Option " + i;
            buttons.add(PromptButton.of(i, Button.secondary(customIds[i], label)));
        }
        return new AiPrompt("channel", messageId, source, "", buttons, created);
    }
}
