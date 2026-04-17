package ti4.service.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;

class TacticalActionOutputServiceTest {

    @Test
    void requiresFreshMessageWhenButtonsExceedSingleMessageComponentLimit() {
        List<Button> buttons = IntStream.range(0, 26)
                .mapToObj(i -> Button.primary("button" + i, "Button " + i))
                .toList();

        assertThat(TacticalActionOutputService.requiresFreshMessageForChoosingTileRefresh(buttons))
                .isTrue();
    }

    @Test
    void keepsEditingExistingMessageWhenButtonsFitWithinSingleMessageLimit() {
        List<Button> buttons = IntStream.range(0, 25)
                .mapToObj(i -> Button.primary("button" + i, "Button " + i))
                .toList();

        assertThat(TacticalActionOutputService.requiresFreshMessageForChoosingTileRefresh(buttons))
                .isFalse();
    }
}
