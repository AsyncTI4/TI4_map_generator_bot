package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.fow.GMService;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;
import ti4.testUtils.BaseTi4Test;

class ButtonHelperColorClashTest extends BaseTi4Test {

    private static Player addPlayerWithColor(Game game, String id, String color) {
        Player player = game.addPlayer(id, id);
        player.setFaction(""); // avoids the heavier Player.setFaction(Game, String) leader/ability init
        player.setColor(color);
        return player;
    }

    private static void stubUserSettings(
            MockedStatic<UserSettingsManager> userSettingsManager, String accessibilityUserId) {
        stubUserSettings(userSettingsManager, accessibilityUserId, UserSettings.COLOR_VISION_RED_GREEN);
    }

    private static void stubUserSettings(
            MockedStatic<UserSettingsManager> userSettingsManager, String accessibilityUserId, String colorVisionPref) {
        userSettingsManager.when(() -> UserSettingsManager.get(anyString())).thenAnswer(invocation -> {
            UserSettings settings = new UserSettings();
            settings.setUserId(invocation.getArgument(0));
            if (invocation.getArgument(0).equals(accessibilityUserId)) {
                settings.setColorVisionPref(colorVisionPref);
            }
            return settings;
        });
    }

    @Test
    void sendsNothingWhenNoClashAndNoAccessibilityPlayer() {
        Game game = new Game();
        game.setName("test-game");
        // Contrast ratio ~8.15 (well above the 2.5 threshold) and not a red-green confusion pair.
        addPlayerWithColor(game, "p1", "black");
        addPlayerWithColor(game, "p2", "lightgray");

        try (MockedStatic<UserSettingsManager> userSettingsManager = mockStatic(UserSettingsManager.class);
                MockedStatic<MessageHelper> messageHelper = mockStatic(MessageHelper.class)) {
            stubUserSettings(userSettingsManager, null);

            ButtonHelper.resolveSetupColorChecker(game);

            messageHelper.verifyNoInteractions();
        }
    }

    @Test
    void onlySendsOncePerGame() {
        Game game = new Game();
        game.setName("test-game");
        addPlayerWithColor(game, "p1", "red");
        addPlayerWithColor(game, "p2", "green");

        try (MockedStatic<UserSettingsManager> userSettingsManager = mockStatic(UserSettingsManager.class);
                MockedStatic<MessageHelper> messageHelper = mockStatic(MessageHelper.class)) {
            stubUserSettings(userSettingsManager, "p2");

            ButtonHelper.resolveSetupColorChecker(game);
            ButtonHelper.resolveSetupColorChecker(game);

            // 1 generic public message + 1 private message to each of the 2 affected players = 3,
            // and it must stay at 3 (not 6) on the second call thanks to the colorClashChecked guard.
            messageHelper.verify(() -> MessageHelper.sendMessageToChannel(any(), anyString()), times(3));
        }
    }

    @Test
    void neitherPublicNorPrivateMessagesRevealAccessibilityPreference() {
        Game game = new Game();
        game.setName("test-game");
        addPlayerWithColor(game, "p1", "red");
        addPlayerWithColor(game, "p2", "green");

        try (MockedStatic<UserSettingsManager> userSettingsManager = mockStatic(UserSettingsManager.class);
                MockedStatic<MessageHelper> messageHelper = mockStatic(MessageHelper.class)) {
            stubUserSettings(userSettingsManager, "p2");

            ButtonHelper.resolveSetupColorChecker(game);

            ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
            messageHelper.verify(() -> MessageHelper.sendMessageToChannel(any(), textCaptor.capture()), times(3));
            assertThat(textCaptor.getAllValues())
                    .noneMatch(text -> text.toLowerCase().contains("confus"))
                    .noneMatch(text -> text.toLowerCase().contains("accessibility"));
        }
    }

    @Test
    void fowGamesRouteDetailToGmChannelOnlyAndIncludeTheReason() {
        Game game = new Game();
        game.setName("test-game");
        game.setFowMode(true);
        addPlayerWithColor(game, "p1", "red");
        addPlayerWithColor(game, "p2", "green");
        TextChannel gmChannel = mock(TextChannel.class);

        try (MockedStatic<UserSettingsManager> userSettingsManager = mockStatic(UserSettingsManager.class);
                MockedStatic<MessageHelper> messageHelper = mockStatic(MessageHelper.class);
                MockedStatic<GMService> gmService = mockStatic(GMService.class)) {
            stubUserSettings(userSettingsManager, "p2");
            gmService.when(() -> GMService.getGMChannel(game)).thenReturn(gmChannel);
            gmService.when(() -> GMService.gmPing(game)).thenReturn("");

            ButtonHelper.resolveSetupColorChecker(game);

            ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
            messageHelper.verify(
                    () -> MessageHelper.sendMessageToChannel(eq(gmChannel), textCaptor.capture()), times(1));
            // Only one message total (to the GM channel) - nothing else leaked to any other channel.
            messageHelper.verify(() -> MessageHelper.sendMessageToChannel(any(), anyString()), times(1));
            assertThat(textCaptor.getValue()).containsIgnoringCase("commonly confused hues");
        }
    }

    @Test
    void otherCvdPrefSkipsRedGreenLogicAndPostsPlainNoticeInstead() {
        Game game = new Game();
        game.setName("test-game");
        // red/green would normally be flagged as a confusion pair, but "other" doesn't enable that
        // heuristic - and the pair has good luminance contrast (~8.15), so no low-contrast issue either.
        addPlayerWithColor(game, "p1", "black");
        addPlayerWithColor(game, "p2", "lightgray");

        try (MockedStatic<UserSettingsManager> userSettingsManager = mockStatic(UserSettingsManager.class);
                MockedStatic<MessageHelper> messageHelper = mockStatic(MessageHelper.class)) {
            stubUserSettings(userSettingsManager, "p2", UserSettings.COLOR_VISION_OTHER);

            ButtonHelper.resolveSetupColorChecker(game);

            ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
            messageHelper.verify(
                    () -> MessageHelper.sendMessageToChannel(eq(game.getActionsChannel()), textCaptor.capture()),
                    times(1));
            assertThat(textCaptor.getValue())
                    .containsIgnoringCase("change_unit_decal")
                    .doesNotContainIgnoringCase("hard to tell apart");
        }
    }

    @Test
    void otherCvdPrefRoutesToGmChannelInFowGames() {
        Game game = new Game();
        game.setName("test-game");
        game.setFowMode(true);
        addPlayerWithColor(game, "p1", "black");
        addPlayerWithColor(game, "p2", "lightgray");
        TextChannel gmChannel = mock(TextChannel.class);

        try (MockedStatic<UserSettingsManager> userSettingsManager = mockStatic(UserSettingsManager.class);
                MockedStatic<MessageHelper> messageHelper = mockStatic(MessageHelper.class);
                MockedStatic<GMService> gmService = mockStatic(GMService.class)) {
            stubUserSettings(userSettingsManager, "p2", UserSettings.COLOR_VISION_OTHER);
            gmService.when(() -> GMService.getGMChannel(game)).thenReturn(gmChannel);
            gmService.when(() -> GMService.gmPing(game)).thenReturn("");

            ButtonHelper.resolveSetupColorChecker(game);

            messageHelper.verify(() -> MessageHelper.sendMessageToChannel(eq(gmChannel), anyString()), times(1));
            messageHelper.verify(() -> MessageHelper.sendMessageToChannel(any(), anyString()), times(1));
        }
    }
}
