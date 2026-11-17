package ti4.service.fow.setup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.fow.FOWPlusService;
import ti4.service.option.FOWOptionService;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

class FowSetupPanelSelectionTest extends BaseTi4Test {

    @Test
    void rolesMenuOnlyChangesThePlayersItLists() {
        FowSetupWizardState state = new FowSetupWizardState();
        state.getNonPlayingUserIds().add("gm");
        state.getNonPlayingUserIds().add("observer-on-another-page");

        // The menu lists "gm", "alice" and "bob"; the GM picked "gm" and "alice" as playing.
        FowSetupPanelPlayers.applyRoles(state, List.of("gm", "alice", "bob"), List.of("gm", "alice"));

        assertThat(state.getNonPlayingUserIds()).containsExactlyInAnyOrder("bob", "observer-on-another-page");
    }

    @Test
    void banMenuOnlyChangesTheFactionsItLists() {
        // "sol" sits on another ban menu page, so this menu's selection must leave it banned.
        List<String> banned = List.of("sol", "arborec");

        List<String> updated =
                FowSetupPanelFactions.applyBans(banned, List.of("arborec", "winnu", "xxcha"), List.of("winnu"));

        assertThat(updated).containsExactlyInAnyOrder("sol", "winnu");
    }

    @Test
    void aSubViewLeftOverFromAnotherStepKeepsTheStepNavigation() {
        Game game = new Game();
        game.setName("fow-panel-stale-subview");
        game.setFowMode(true);
        FowSetupWizardState state = new FowSetupWizardState();
        // Legacy step buttons change the step without clearing the sub-view, so the token can belong to another step.
        state.setStep(FowSetupStep.GAME_TYPE);
        state.setSubView("start:confirm");

        List<MessageTopLevelComponent> panel = FowSetupPanel.build(game, state, null);

        assertThat(panel)
                .filteredOn(ActionRow.class::isInstance)
                .map(ActionRow.class::cast)
                .flatMap(ActionRow::getComponents)
                .filteredOn(StringSelectMenu.class::isInstance)
                .map(component -> ((StringSelectMenu) component).getCustomId())
                .contains("fowWizStep");
    }

    // dealSOToAll deals to everyone again, so the panel must stop a second deal once anyone holds a secret.
    @Test
    void dealButtonTurnsOffOnceAnyoneHasASecret() {
        Game game = new Game();
        game.setName("fow-panel-deal-once");
        game.setFowMode(true);
        Player player = game.addPlayer("player-1", "player-1");
        player.setFaction(game, "arborec");
        player.setColor("red");
        FowSetupWizardState state = new FowSetupWizardState();
        state.setStep(FowSetupStep.DEAL_SO);

        assertThat(dealButton(game, state).isDisabled()).isFalse();

        player.setSecret("cm");

        assertThat(dealButton(game, state).isDisabled()).isTrue();
    }

    private static Button dealButton(Game game, FowSetupWizardState state) {
        return FowSetupPanel.build(game, state, null).stream()
                .filter(ActionRow.class::isInstance)
                .map(ActionRow.class::cast)
                .flatMap(row -> row.getButtons().stream())
                .filter(button -> "fowWizDealSo".equals(button.getCustomId()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void embeddedFogToggleCannotChangeAnOptionFowPlusForces() {
        Game game = new Game();
        game.setName("fow-panel-toggle-lock");
        game.setFowMode(true);
        FOWPlusService.enable(game);
        FOWOption forced = FOWPlusService.FORCED_FOWPLUS_OPTIONS.getFirst().getLeft();
        boolean forcedValue = FOWPlusService.FORCED_FOWPLUS_OPTIONS.getFirst().getRight();

        FOWOptionService.applyToggle(game, forced.getCategory() + "_" + !forcedValue + "_" + forced);

        assertThat(FOWOptionService.isEnabled(game, forced)).isEqualTo(forcedValue);
    }

    @Test
    void embeddedFogToggleChangesAFreeOption() {
        Game game = new Game();
        game.setName("fow-panel-toggle-free");
        game.setFowMode(true);

        FOWOptionService.applyToggle(game, FOWOption.GHOST_HEXES.getCategory() + "_true_" + FOWOption.GHOST_HEXES);

        assertThat(FOWOptionService.isEnabled(game, FOWOption.GHOST_HEXES)).isTrue();
    }
}
