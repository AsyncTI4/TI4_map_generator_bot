package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class InvolvementTest extends BaseTi4Test {

    // Cards played in a combat aim at the opponent in that system, so Sabotage weighs them as aimed at the seat.
    @Test
    void everyCombatCardTheBotHasIsKnownAsOne() {
        List<String> titles = List.of(
                "Morale Boost",
                "Fighter Prototype",
                "Emergency Repairs",
                "Courageous to the End",
                "Maneuvering Jets",
                "In The Silence Of Space",
                "Solar Flare",
                "Salvage",
                "Crash Landing");

        assertThat(titles).allMatch(Involvement::isCombatCard);
    }

    @Test
    void aCardOutsideCombatIsNotOne() {
        assertThat(Involvement.isCombatCard("Mining Initiative")).isFalse();
    }
}
