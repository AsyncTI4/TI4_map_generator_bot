package ti4.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;
import ti4.testUtils.BaseTi4Test;

class GameColorsServiceTest extends BaseTi4Test {

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
        settings.setPrefersColorAccessibilityCues(true);
        userSettingsManager.when(() -> UserSettingsManager.get(userId)).thenReturn(settings);
    }

    @Test
    void isCommonRedGreenConfusionPairFlagsKnownPairs() {
        assertThat(GameColorsService.isCommonRedGreenConfusionPair("RED", "GREEN"))
                .isTrue();
        assertThat(GameColorsService.isCommonRedGreenConfusionPair("GREEN", "RED"))
                .isTrue();
        assertThat(GameColorsService.isCommonRedGreenConfusionPair("ORANGE", "GREEN"))
                .isTrue();
        assertThat(GameColorsService.isCommonRedGreenConfusionPair("GREEN", "ORANGE"))
                .isTrue();
    }

    @Test
    void isCommonRedGreenConfusionPairIgnoresUnrelatedHues() {
        assertThat(GameColorsService.isCommonRedGreenConfusionPair("RED", "ORANGE"))
                .isFalse();
        assertThat(GameColorsService.isCommonRedGreenConfusionPair("BLUE", "PURPLE"))
                .isFalse();
        assertThat(GameColorsService.isCommonRedGreenConfusionPair("RED", "RED"))
                .isFalse();
    }

    @Test
    void isCommonRedGreenConfusionPairCatchesSplitColorsViaHueFallback() {
        assertThat(GameColorsService.isCommonRedGreenConfusionPair(
                        Mapper.getColor("splitred"), Mapper.getColor("splitgreen")))
                .isTrue();
    }

    @Test
    void isCommonRedGreenConfusionPairCatchesMultiHueGradientsViaRgbFallback() {
        // "rainbow" is bucketed as a generic MULTI1 hue, but is literally a red+green two-tone
        // color, so it can only be caught via the RGB-component fallback, not the hue-string map.
        assertThat(GameColorsService.isCommonRedGreenConfusionPair(
                        Mapper.getColor("rainbow"), Mapper.getColor("green")))
                .isTrue();
    }

    @Test
    void isCommonRedGreenConfusionPairDoesNotFalselyFlagUnrelatedMultiHueGradient() {
        assertThat(GameColorsService.isCommonRedGreenConfusionPair(Mapper.getColor("rainbow"), Mapper.getColor("blue")))
                .isFalse();
    }

    @Test
    void hasColorAccessibilityPlayerIsFalseByDefault() {
        Game game = new Game();
        Player p1 = game.addPlayer("p1", "Player One");
        p1.setColor("red");
        Player p2 = game.addPlayer("p2", "Player Two");
        p2.setColor("green");

        assertThat(GameColorsService.hasColorAccessibilityPlayer(game)).isFalse();
    }

    @Test
    void hasColorAccessibilityPlayerIsTrueWhenAnyPlayerOptsIn() {
        Game game = new Game();
        Player p1 = game.addPlayer("p1", "Player One");
        p1.setColor("red");
        Player p2 = game.addPlayer("p2", "Player Two");
        p2.setColor("green");
        enableColorAccessibility("p2");

        assertThat(GameColorsService.hasColorAccessibilityPlayer(game)).isTrue();
    }

    @Test
    void conflictsWithUsedColorsOnlyChecksExactHueWhenNoAccessibilityPlayerPresent() {
        Game game = new Game();
        Player p1 = game.addPlayer("p1", "Player One");
        p1.setColor("green");

        assertThat(GameColorsService.conflictsWithUsedColors(game, Mapper.getColor("green")))
                .isTrue();
        assertThat(GameColorsService.conflictsWithUsedColors(game, Mapper.getColor("red")))
                .isFalse();
    }

    @Test
    void conflictsWithUsedColorsChecksConfusableHuesWhenAccessibilityPlayerPresent() {
        Game game = new Game();
        Player p1 = game.addPlayer("p1", "Player One");
        p1.setColor("green");
        Player p2 = game.addPlayer("p2", "Player Two");
        p2.setColor("blue");
        enableColorAccessibility("p2");

        assertThat(GameColorsService.conflictsWithUsedColors(game, Mapper.getColor("red")))
                .isTrue();
        assertThat(GameColorsService.conflictsWithUsedColors(game, Mapper.getColor("orange")))
                .isTrue();
        assertThat(GameColorsService.conflictsWithUsedColors(game, Mapper.getColor("purple")))
                .isFalse();
    }

    @Test
    void conflictsWithUsedColorsCatchesMultiHueGradientsViaRgbFallback() {
        // This is the gap that a plain hue-string comparison (e.g. the old getHuesToAvoid) missed:
        // "watermelon" is bucketed MULTI2, not RED or GREEN, so only the RGB-component fallback in
        // isCommonRedGreenConfusionPair can catch that it conflicts with an already-used green.
        Game game = new Game();
        Player p1 = game.addPlayer("p1", "Player One");
        p1.setColor("green");
        enableColorAccessibility("p1");

        assertThat(GameColorsService.conflictsWithUsedColors(game, Mapper.getColor("watermelon")))
                .isTrue();
    }
}
