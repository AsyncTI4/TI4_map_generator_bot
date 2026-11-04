package ti4.service.fow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ti4.game.Game;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

class FogPresetServiceTest extends BaseTi4Test {

    private Game game;

    @BeforeEach
    void setUpFogGame() {
        game = new Game();
        game.setName("fog-preset-test");
        game.setFowMode(true);
    }

    // The bug this replaces: Normal after Standard used to leave half of FoW+'s forced options on.
    @ParameterizedTest
    @EnumSource(FogPreset.class)
    void aPresetGivesTheSameResultWhateverWasPickedBefore(FogPreset target) {
        for (FogPreset before : FogPreset.values()) {
            FogPresetService.apply(game, before);
            FogPresetService.apply(game, target);

            assertThat(FogPresetService.changes(game, target))
                    .as(before + " then " + target)
                    .isEmpty();
            assertThat(FogPresetService.matching(game)).contains(target);
        }
    }

    @Test
    void normalTurnsOffFowPlusAndEverythingItForced() {
        FogPresetService.apply(game, FogPreset.STANDARD);
        FogPresetService.apply(game, FogPreset.NORMAL);

        assertThat(FOWPlusService.isActive(game)).isFalse();
        FOWPlusService.FORCED_FOWPLUS_OPTIONS.forEach(forced -> assertThat(game.getFowOption(forced.getLeft()))
                .as(forced.getLeft().name())
                .isFalse());
        assertThat(game.getFowOption(FOWOption.MANAGED_COMMS)).isFalse();
    }

    @Test
    void normalAndBeginnerKeepTheGmToolsAndRulesOptions() {
        FogPresetService.apply(game, FogPreset.STANDARD);
        game.setFowOption(FOWOption.CLASSIC_MAP_LAYOUT, true);
        game.setNoFractureMode(true);

        FogPresetService.apply(game, FogPreset.BEGINNER);

        assertThat(game.getFowOption(FOWOption.GM_TURN_MAP)).isTrue();
        assertThat(game.getFowOption(FOWOption.FOG_QOL_01)).isTrue();
        assertThat(game.getFowOption(FOWOption.CLASSIC_MAP_LAYOUT)).isTrue();
        assertThat(game.isNoFractureMode()).isTrue();
        assertThat(game.getFowOption(FOWOption.BRIGHT_NOVAS)).isTrue();
    }

    @Test
    void theStatusNamesTheClosestPresetAfterAManualChange() {
        FogPresetService.apply(game, FogPreset.STANDARD);
        game.setFowOption(FOWOption.GHOST_HEXES, false);

        assertThat(FogPresetService.status(game))
                .contains("custom")
                .contains("Standard")
                .contains("1 difference");
    }

    @Test
    void thePreviewListsWhatChangesAndFitsInOneMessage() {
        FogPresetService.apply(game, FogPreset.STANDARD);

        String preview = FogPresetService.preview(game, FogPreset.NORMAL);
        Map<FOWOption, Boolean> changes = FogPresetService.changes(game, FogPreset.NORMAL);

        assertThat(changes).containsEntry(FOWOption.FOW_PLUS, false).containsEntry(FOWOption.HIDE_MAP, false);
        assertThat(preview).contains("Turns off").contains(FOWOption.HIDE_MAP.getTitle());
        assertThat(preview.length()).isLessThan(4000);
    }

    @Test
    void oldWizardIdsStillResolve() {
        assertThat(List.of("standard", "plus", "normal", "lite", "beginner").stream()
                        .map(FogPreset::fromId))
                .containsExactly(
                        FogPreset.STANDARD,
                        FogPreset.FOG_PLUS,
                        FogPreset.NORMAL,
                        FogPreset.BEGINNER,
                        FogPreset.BEGINNER);
    }
}
