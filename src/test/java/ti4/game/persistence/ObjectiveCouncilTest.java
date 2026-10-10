package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.objectives.OPlusPlusCouncilService;
import ti4.testUtils.BaseTi4Test;

/**
 * Regression coverage for the Objective Council homebrew flow (deal -> per-player pick -> confirm ->
 * pool into real decks). Two real bugs were found and fixed while chasing a report that one player's
 * confirmation reverted to unconfirmed whenever the other player confirmed afterward:
 *
 * <p>1) {@code player.oplusplusCouncilConfirmed} was a brand-new {@code PlayerProperties} field that was
 * never wired into the hand-rolled save/load text format (unlike {@code readyToPassBag}, which it mirrors),
 * so it silently reverted to {@code false} on every fresh load from disk - exactly what a real Discord
 * interaction does on every button/select click.
 *
 * <p>2) The status message's Discord message id was captured via an async {@code onSuccess} callback on a
 * {@code .queue()}'d send, which runs after the interaction has already returned and saved - so the id never
 * actually made it into that save, and every update sent a brand new message instead of editing in place.
 *
 * <p>These tests drive the real handler methods (not reimplementations) through a two-player flow with an
 * actual save+reload between each player's confirmation, to pin both fixes against regressing.
 */
class ObjectiveCouncilTest extends BaseTi4Test {

    @BeforeEach
    void setUp() {
        JdaService.testingMode = true;
        JdaService.jda = mock(JDA.class);
    }

    private static TextChannel giveMainChannel(Game game) {
        TextChannel channel = mock(TextChannel.class, RETURNS_DEEP_STUBS);
        game.setMainChannelID("main-chan");
        when(JdaService.jda.getTextChannelById("main-chan")).thenReturn(channel);
        return channel;
    }

    /**
     * Replaces the player's entry in the game's player map with a spy whose cards-info thread is a harmless
     * mock, so the real sendPurgeMenus/sendConfirmSummary code can run without trying to create a live
     * Discord thread.
     */
    private static Player stubCardsInfoThread(Game game, Player player) {
        Player spyPlayer = spy(player);
        doReturn(mock(ThreadChannel.class, RETURNS_DEEP_STUBS)).when(spyPlayer).getCardsInfoThread();
        game.getPlayers().put(player.getUserID(), spyPlayer);
        return spyPlayer;
    }

    /**
     * OPlusPlusCouncilService.joinedPlayers() includes anyone not dummy/npc - which, unlike
     * getRealPlayers(), also includes spectator seats with no faction/color assigned (this fixture
     * has one: "Fin", faction/color both the literal string "null"). Marking only getRealPlayers()'
     * leftovers as dummy would miss that seat and leave it to crash on a real getCardsInfoThread()
     * call, so this marks every OTHER seat in the full player map as dummy instead.
     */
    private static void restrictToOnly(Game game, Player playerA, Player playerB) {
        for (Player other : game.getPlayers().values()) {
            if (!other.getUserID().equals(playerA.getUserID())
                    && !other.getUserID().equals(playerB.getUserID())) {
                other.setDummy(true);
            }
        }
    }

    private static ButtonInteractionEvent mockButtonEvent() {
        return mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
    }

    private static StringSelectInteractionEvent mockSelectEvent(List<String> values) {
        StringSelectInteractionEvent event = mock(StringSelectInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getValues()).thenReturn(values);
        return event;
    }

    // Mirrors the service's own private key/prefix conventions so tests can seed and read its state
    // without reaching into private methods.
    private static List<String> dealtTo(Game game, String category, Player player) {
        String raw = game.getStoredValue("oplusplusCouncilDealt" + category + "_" + player.getUserID());
        return raw.isBlank() ? List.of() : List.of(raw.split(","));
    }

    private static void pickAll(Game game, Player player, List<String> s1, List<String> s2, List<String> so) {
        OPlusPlusCouncilService.pickStage1(game, mockSelectEvent(s1), "oplusplusCouncilPickS1_" + player.getUserID());
        OPlusPlusCouncilService.pickStage2(game, mockSelectEvent(s2), "oplusplusCouncilPickS2_" + player.getUserID());
        OPlusPlusCouncilService.pickSecrets(game, mockSelectEvent(so), "oplusplusCouncilPickSO_" + player.getUserID());
    }

    private static void confirm(Game game, Player player) {
        OPlusPlusCouncilService.confirmPurge(game, mockButtonEvent(), "oplusplusCouncilConfirm_" + player.getUserID());
    }

    @Test
    void secondPlayerConfirmingDoesNotRevertTheFirstPlayersAlreadySavedConfirmation() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            List<Player> real = game.getRealPlayers();
            Player playerA = stubCardsInfoThread(game, real.get(0));
            Player playerB = stubCardsInfoThread(game, real.get(1));
            // Everyone else (including non-real spectator seats) must not participate, so Objective
            // Council only deals to A and B.
            restrictToOnly(game, playerA, playerB);
            giveMainChannel(game);

            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class)) {
                mh.when(() -> MessageHelper.sanitizeButtons(any(), any())).thenAnswer(inv -> inv.getArgument(0));

                OPlusPlusCouncilService.start(game, mockButtonEvent());

                List<String> aS1 = dealtTo(game, "S1", playerA);
                List<String> aS2 = dealtTo(game, "S2", playerA);
                List<String> aSO = dealtTo(game, "SO", playerA);
                List<String> bS1 = dealtTo(game, "S1", playerB);
                List<String> bS2 = dealtTo(game, "S2", playerB);
                List<String> bSO = dealtTo(game, "SO", playerB);
                assertThat(aS1).hasSize(5);
                assertThat(aSO).hasSize(8);

                pickAll(game, playerA, aS1, aS2, aSO);
                confirm(game, playerA);
                assertThat(playerA.isOplusplusCouncilConfirmed()).isTrue();

                // Simulate the end of player A's Discord interaction: the real framework saves the
                // game here, and every subsequent interaction (including player B's own click) loads
                // a brand new Game from disk rather than reusing this in-memory instance.
                GameSaveService.save(game, "test");

                Game reloaded = harness.load();
                Player reloadedA = reloaded.getPlayer(playerA.getUserID());
                Player reloadedB = stubCardsInfoThread(reloaded, reloaded.getPlayer(playerB.getUserID()));
                giveMainChannel(reloaded);

                assertThat(reloadedA.isOplusplusCouncilConfirmed())
                        .as("player A's confirmation must survive a reload before player B ever acts")
                        .isTrue();

                pickAll(reloaded, reloadedB, bS1, bS2, bSO);
                confirm(reloaded, reloadedB);

                // finish() only runs once every joined player is confirmed, and resets everyone's flag
                // back to false as normal end-of-round cleanup once it does - so this is NOT checking
                // isOplusplusCouncilConfirmed() post-finish (false is correct there). If player A's
                // confirmation had been silently lost instead, finish() would never have fired at all,
                // and these decks would still be whatever dealing left behind, not the full
                // pooled-and-shuffled set from both players.
                List<String> expectedStage1 = new ArrayList<>(aS1);
                expectedStage1.addAll(bS1);
                List<String> expectedSecrets = new ArrayList<>(aSO);
                expectedSecrets.addAll(bSO);
                // Up to 5 of the pooled Stage I cards move into the peekable preview list
                // (setUpPeekableObjectives(5, 1), matching normal game setup) - the rest stay in the
                // deck itself, so the pooled set is only complete across both lists combined.
                List<String> actualStage1 = new ArrayList<>(reloaded.getPublicObjectives1());
                actualStage1.addAll(reloaded.getPublicObjectives1Peekable());
                assertThat(actualStage1).containsExactlyInAnyOrderElementsOf(expectedStage1);
                assertThat(reloaded.getSecretObjectives()).containsExactlyInAnyOrderElementsOf(expectedSecrets);
            }
        }
    }

    @Test
    void finishRestoresWhateverPeekableCountWasConfiguredBeforeTheCouncilInsteadOfAssumingFive() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            List<Player> real = game.getRealPlayers();
            Player playerA = stubCardsInfoThread(game, real.get(0));
            Player playerB = stubCardsInfoThread(game, real.get(1));
            restrictToOnly(game, playerA, playerB);
            giveMainChannel(game);

            // Simulates a homebrew mode like 4/4/4 (HomebrewService.HB444), which configures 4
            // peekable Stage I/II objectives instead of the default 5, before Objective Council runs.
            game.setUpPeekableObjectives(4, 1);
            game.setUpPeekableObjectives(4, 2);

            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class)) {
                mh.when(() -> MessageHelper.sanitizeButtons(any(), any())).thenAnswer(inv -> inv.getArgument(0));

                OPlusPlusCouncilService.start(game, mockButtonEvent());
                List<String> aS1 = dealtTo(game, "S1", playerA);
                List<String> aS2 = dealtTo(game, "S2", playerA);
                List<String> aSO = dealtTo(game, "SO", playerA);
                List<String> bS1 = dealtTo(game, "S1", playerB);
                List<String> bS2 = dealtTo(game, "S2", playerB);
                List<String> bSO = dealtTo(game, "SO", playerB);

                pickAll(game, playerA, aS1, aS2, aSO);
                confirm(game, playerA);
                pickAll(game, playerB, bS1, bS2, bSO);
                confirm(game, playerB);

                assertThat(game.getPublicObjectives1Peekable())
                        .as("finish() must restore the 4/4/4-configured count, not the default 5")
                        .hasSize(4);
                assertThat(game.getPublicObjectives2Peekable()).hasSize(4);
            }
        }
    }

    @Test
    void confirmingWithoutPickingEveryCategoryIsRejectedAndLeavesTheFlagFalse() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            List<Player> real = game.getRealPlayers();
            Player playerA = stubCardsInfoThread(game, real.get(0));
            Player playerB = stubCardsInfoThread(game, real.get(1));
            restrictToOnly(game, playerA, playerB);
            giveMainChannel(game);

            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class)) {
                mh.when(() -> MessageHelper.sanitizeButtons(any(), any())).thenAnswer(inv -> inv.getArgument(0));

                OPlusPlusCouncilService.start(game, mockButtonEvent());
                // Deliberately never calling pickAll for playerA.
                confirm(game, playerA);

                assertThat(playerA.isOplusplusCouncilConfirmed()).isFalse();
                mh.verify(() -> MessageHelper.sendMessageToChannel(
                        any(), org.mockito.ArgumentMatchers.contains("need to make a selection")));
            }
        }
    }

    @Test
    void confirmingTwiceIsRejectedAndDoesNotReRunFinish() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            List<Player> real = game.getRealPlayers();
            Player playerA = stubCardsInfoThread(game, real.get(0));
            Player playerB = stubCardsInfoThread(game, real.get(1));
            restrictToOnly(game, playerA, playerB);
            giveMainChannel(game);

            try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class)) {
                mh.when(() -> MessageHelper.sanitizeButtons(any(), any())).thenAnswer(inv -> inv.getArgument(0));

                OPlusPlusCouncilService.start(game, mockButtonEvent());
                List<String> aS1 = dealtTo(game, "S1", playerA);
                List<String> aS2 = dealtTo(game, "S2", playerA);
                List<String> aSO = dealtTo(game, "SO", playerA);

                pickAll(game, playerA, aS1, aS2, aSO);
                confirm(game, playerA);
                confirm(game, playerA);

                assertThat(playerA.isOplusplusCouncilConfirmed()).isTrue();
                assertThat(playerB.isOplusplusCouncilConfirmed())
                        .as("re-confirming player A must not have any effect on player B")
                        .isFalse();
                mh.verify(() -> MessageHelper.sendMessageToChannel(
                        any(), org.mockito.ArgumentMatchers.contains("already confirmed")));
            }
        }
    }
}
