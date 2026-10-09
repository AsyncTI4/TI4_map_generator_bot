package ti4.ai.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.testbed.TestBedService;
import ti4.testUtils.BaseTi4Test;

class AiProfileTest extends BaseTi4Test {

    @Test
    void survivesAStoredValueRoundTrip() {
        Player seat = newSeat();
        AiProfile profile = new AiProfile(
                "nekro",
                AggressionLevel.AGGRESSIVE,
                AiProfile.AggressionMode.LOCKED,
                AiProfile.PauseState.MANUAL,
                123456789L);

        profile.writeTo(seat);

        assertThat(AiProfile.of(seat)).isEqualTo(profile);
        assertThat(seat.getStoredValueMap().values()).allMatch(TestBedService::isSaveSafe);
    }

    @Test
    void missingValuesFallBackToARunningDynamicMiddleProfile() {
        AiProfile profile = AiProfile.of(newSeat());

        assertThat(profile.baseLevel()).isEqualTo(AggressionLevel.OPPORTUNIST);
        assertThat(profile.mode()).isEqualTo(AiProfile.AggressionMode.DYNAMIC);
        assertThat(profile.isPaused()).isFalse();
    }

    @Test
    void clearRemovesEveryProfileValue() {
        Player seat = newSeat();
        AiProfile.createHidden("nekro", new Random(1)).writeTo(seat);

        AiProfile.clear(seat);

        assertThat(seat.getStoredValueMap()).isEmpty();
    }

    // The hidden draw favours the middle levels, but every level stays possible.
    @Test
    void hiddenDrawFavoursTheMiddleLevels() {
        Map<AggressionLevel, Integer> counts = new EnumMap<>(AggressionLevel.class);
        Random random = new Random(42);
        for (int i = 0; i < 12_000; i++) counts.merge(AggressionLevel.drawHidden(random), 1, Integer::sum);

        assertThat(counts).containsOnlyKeys(AggressionLevel.values());
        assertThat(counts.get(AggressionLevel.OPPORTUNIST)).isGreaterThan(counts.get(AggressionLevel.PEACEFUL));
        assertThat(counts.get(AggressionLevel.OPPORTUNIST)).isGreaterThan(counts.get(AggressionLevel.WARLORD));
    }

    private static Player newSeat() {
        Game game = new Game();
        game.setName("ai-profile-test");
        return game.addPlayer("7100000123456789", "Nekro AI");
    }
}
