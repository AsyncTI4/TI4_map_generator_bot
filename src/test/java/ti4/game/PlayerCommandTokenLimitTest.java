package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class PlayerCommandTokenLimitTest extends BaseTi4Test {

    private static final String ENDURANCE_STEROIDS = "endurance_steroids";

    private Game game;
    private Player red;
    private Player blue;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("cc-limit-test");
        red = new Player("red-user", "Red", game);
        red.setColor("red");
        blue = new Player("blue-user", "Blue", game);
        blue.setColor("blue");
    }

    @Test
    void defaultsToSixteen() {
        assertThat(red.getCommandTokenLimit()).isEqualTo(16);
    }

    @Test
    void globalOverrideReplacesDefault() {
        game.setStoredValue("ccLimit", "20");

        assertThat(red.getCommandTokenLimit()).isEqualTo(20);
        assertThat(blue.getCommandTokenLimit()).isEqualTo(20);
    }

    @Test
    void perColourOverrideBeatsGlobalOverride() {
        game.setStoredValue("ccLimit", "20");
        game.setStoredValue("ccLimitred", "12");

        assertThat(red.getCommandTokenLimit()).isEqualTo(12);
        // Another colour's override must not leak onto this player.
        assertThat(blue.getCommandTokenLimit()).isEqualTo(20);
    }

    @Test
    void perColourOverrideAppliesWithoutGlobalOverride() {
        game.setStoredValue("ccLimitblue", "10");

        assertThat(red.getCommandTokenLimit()).isEqualTo(16);
        assertThat(blue.getCommandTokenLimit()).isEqualTo(10);
    }

    @Test
    void enduranceSteroidsAddsTwo() {
        red.addRelic(ENDURANCE_STEROIDS);

        assertThat(red.getCommandTokenLimit()).isEqualTo(18);
        assertThat(blue.getCommandTokenLimit()).isEqualTo(16);
    }

    @Test
    void exhaustedEnduranceSteroidsStillAddsTwo() {
        // Exhausting the relic only uses its action; the two neutral tokens stay in reinforcements.
        red.addRelic(ENDURANCE_STEROIDS);
        red.addExhaustedRelic(ENDURANCE_STEROIDS);

        assertThat(red.getCommandTokenLimit()).isEqualTo(18);
    }

    @Test
    void enduranceSteroidsStacksOnGlobalOverride() {
        game.setStoredValue("ccLimit", "20");
        red.addRelic(ENDURANCE_STEROIDS);

        assertThat(red.getCommandTokenLimit()).isEqualTo(22);
    }

    @Test
    void enduranceSteroidsStacksOnPerColourOverride() {
        game.setStoredValue("ccLimit", "20");
        game.setStoredValue("ccLimitred", "12");
        red.addRelic(ENDURANCE_STEROIDS);

        assertThat(red.getCommandTokenLimit()).isEqualTo(14);
    }
}
