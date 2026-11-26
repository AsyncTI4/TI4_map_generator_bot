package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.xxcha.QuashRules;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Quash costs a strategy token. With an empty pool the Scepter of Emelpar pays instead (a token from reinforcements),
// and must be exhausted so it cannot pay for a second Quash in the same agenda phase.
class QuashPaymentTest extends BaseTi4Test {

    private Player xxcha;

    @BeforeEach
    void setUp() {
        xxcha = new AiTestGame().nekro;
        xxcha.addAbility("quash");
    }

    private boolean quashOffered() {
        return AgendaWhensAftersHelper.getPossibleWhenButtons(xxcha).stream()
                .map(Button::getCustomId)
                .anyMatch("queueWhen_ability_quash"::equals);
    }

    @Test
    void spendsAStrategyTokenWhenThePoolHasOne() {
        xxcha.setStrategicCC(2);
        xxcha.addRelic("emelpar");

        assertThat(ButtonHelperFactionSpecific.payForQuash(xxcha)).isFalse();
        assertThat(xxcha.getStrategicCC()).isEqualTo(1);
        assertThat(xxcha.hasRelicReady("emelpar")).isTrue();
    }

    @Test
    void exhaustsTheScepterWhenThePoolIsEmpty() {
        xxcha.setStrategicCC(0);
        xxcha.addRelic("emelpar");
        assertThat(quashOffered()).isTrue();

        assertThat(ButtonHelperFactionSpecific.payForQuash(xxcha)).isTrue();

        assertThat(xxcha.getStrategicCC()).isZero();
        assertThat(xxcha.hasRelicReady("emelpar")).isFalse();
        // The second agenda of the phase: nothing left to pay with.
        assertThat(quashOffered()).isFalse();
        assertThat(QuashRules.canQuash(xxcha)).isFalse();
    }
}
