package ti4.service.option;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.entities.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ti4.game.Game;
import ti4.message.componentsV2.MessageV2Builder;
import ti4.service.fow.FOWPlusService;
import ti4.service.fow.FogPreset;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.service.option.FOWOptionService.FOWOptionCategory;
import ti4.testUtils.BaseTi4Test;

/**
 * The options panel is one Components V2 message. Discord refuses the whole send when it holds more than 40
 * components or 4000 characters, so a tab that grows too big has to fail the build, not the GM's panel.
 */
class FOWOptionPanelLimitsTest extends BaseTi4Test {

    private Game game;

    @BeforeEach
    void setUpWorstCaseGame() {
        game = new Game();
        game.setName("fog-panel-test");
        game.setFowMode(true);
        // Anonymous comms adds a settings button to the Comms tab.
        game.setFowOption(FOWOption.ANONYMOUS_COMMS, true);
    }

    @ParameterizedTest
    @EnumSource(FOWOptionCategory.class)
    void everyTabFitsInOneMessage(FOWOptionCategory tab) {
        assertThat(FOWOptionService.optionsIn(tab)).hasSizeLessThanOrEqualTo(FOWOptionService.MAX_OPTIONS_PER_TAB);
        assertFits(FOWOptionService.optionsPanel(game, tab));

        FOWPlusService.enable(game);
        assertFits(FOWOptionService.optionsPanel(game, tab));
    }

    @Test
    void everyVisibleOptionIsOnATab() {
        for (FOWOption option : FOWOption.values()) {
            assertThat(option.isVisible()).as(option.name()).isEqualTo(option.getCategory() != null);
        }
    }

    @ParameterizedTest
    @EnumSource(FogPreset.class)
    void thePresetScreensFitInOneMessage(FogPreset preset) {
        assertFits(FOWOptionService.presetMenu(game));
        assertFits(FOWOptionService.presetPreview(game, preset));
        assertFits(FOWOptionService.fowPlusMenu(game));
    }

    @Test
    void oldTabNamesFromPanelsPostedBeforeTheRebuildStillOpen() {
        assertThat(FOWOptionCategory.fromString("GAME")).isEqualTo(FOWOptionCategory.COMMS);
        assertThat(FOWOptionCategory.fromString("OTHER")).isEqualTo(FOWOptionCategory.FLOW);
        assertThat(FOWOptionCategory.fromString("PLAYERSTATS")).isEqualTo(FOWOptionCategory.PLAYERSTATS);
    }

    private static void assertFits(List<MessageTopLevelComponent> components) {
        int count =
                components.stream().mapToInt(MessageV2Builder::CountComponents).sum();
        int characters =
                components.stream().mapToInt(MessageV2Builder::CountCharacters).sum();
        assertThat(count).isLessThanOrEqualTo(Message.MAX_COMPONENT_COUNT_IN_COMPONENT_TREE);
        assertThat(characters).isLessThanOrEqualTo(Message.MAX_CONTENT_LENGTH_COMPONENT_V2);
    }
}
