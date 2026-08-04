package ti4.service.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.model.ColorModel;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;
import ti4.testUtils.BaseTi4Test;

class PlayerColorServiceTest extends BaseTi4Test {

    private MockedStatic<UserSettingsManager> userSettingsManager;

    @BeforeEach
    void mockUserSettingsManager() {
        userSettingsManager = Mockito.mockStatic(UserSettingsManager.class);
        userSettingsManager.when(() -> UserSettingsManager.get(anyString())).thenAnswer(invocation -> {
            UserSettings settings = new UserSettings();
            settings.setUserId(invocation.getArgument(0));
            return settings;
        });
    }

    @AfterEach
    void closeUserSettingsManager() {
        userSettingsManager.close();
    }

    private void enableColorAccessibility(String userId) {
        UserSettings settings = new UserSettings();
        settings.setUserId(userId);
        settings.setColorVisionPref(UserSettings.COLOR_VISION_RED_GREEN);
        userSettingsManager.when(() -> UserSettingsManager.get(userId)).thenReturn(settings);
    }

    private static Player addPlayerWithNoFaction(Game game, String id) {
        Player player = game.addPlayer(id, id);
        player.setFaction(""); // avoids the heavier Player.setFaction(Game, String) leader/ability init
        return player;
    }

    @Test
    void fallbackAssignmentAvoidsConfusableHueWhenAccessibilityPlayerPresent() {
        Game game = new Game();
        Player green = addPlayerWithNoFaction(game, "p1");
        green.setColor("green");
        Player toAssign = addPlayerWithNoFaction(game, "p2");
        enableColorAccessibility("p2");

        String assigned = PlayerColorService.getPreferredColor(toAssign);

        assertThat(assigned).isNotIn("red", "orange");
    }

    @Test
    void fallbackAssignmentAvoidsMultiHueGradientConfusionViaRgbFallback() {
        // "watermelon" is bucketed under the generic MULTI2 hue, not RED or GREEN, so only the
        // RGB-component fallback (not a plain hue-string comparison) can tell it conflicts with an
        // already-used green. This is the gap that used to only get caught by the post-setup
        // warning, after the fact - auto-assignment itself now avoids it too.
        Game game = new Game();
        Player green = addPlayerWithNoFaction(game, "p1");
        green.setColor("green");
        Player toAssign = addPlayerWithNoFaction(game, "p2");
        enableColorAccessibility("p2");

        int i = 0;
        for (ColorModel c : Mapper.getColors()) {
            if (!Set.of("green", "watermelon", "purple").contains(c.getName())) {
                Player filler = addPlayerWithNoFaction(game, "filler-" + i++);
                filler.setColor(c.getName());
            }
        }

        String assigned = PlayerColorService.getPreferredColor(toAssign);

        assertThat(assigned).isEqualTo("purple");
    }

    @Test
    void fallbackDegradesGracefullyWhenEveryNonConfusableColorIsExhausted() {
        Game game = new Game();
        Player green = addPlayerWithNoFaction(game, "p1");
        green.setColor("green");
        Player toAssign = addPlayerWithNoFaction(game, "p2");
        enableColorAccessibility("p2");

        // Use up every color except red/orange/green, so the confusion-avoidance filter runs out
        // of non-confusable candidates and must fall back to any unused color rather than fail.
        int i = 0;
        for (ColorModel c : Mapper.getColors()) {
            if (!Set.of("red", "orange", "green").contains(c.getName())) {
                Player filler = addPlayerWithNoFaction(game, "filler-" + i++);
                filler.setColor(c.getName());
            }
        }

        String assigned = PlayerColorService.getPreferredColor(toAssign);

        assertThat(assigned).isNotNull();
    }

    @Test
    void explicitUserPreferredColorIsNotFilteredByConfusionAvoidance() {
        Game game = new Game();
        Player green = addPlayerWithNoFaction(game, "p1");
        green.setColor("green");
        Player toAssign = addPlayerWithNoFaction(game, "p2");

        UserSettings prefs = new UserSettings();
        prefs.setUserId("p2");
        prefs.setColorVisionPref(UserSettings.COLOR_VISION_RED_GREEN);
        prefs.setPreferredColors(List.of("red"));
        userSettingsManager.when(() -> UserSettingsManager.get("p2")).thenReturn(prefs);

        String assigned = PlayerColorService.getPreferredColor(toAssign);

        assertThat(assigned).isEqualTo("red");
    }
}
