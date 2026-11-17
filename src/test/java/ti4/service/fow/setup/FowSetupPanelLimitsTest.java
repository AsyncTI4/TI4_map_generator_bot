package ti4.service.fow.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.SelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.modals.Modal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.message.componentsV2.ComponentPanel;
import ti4.service.fow.FOWPlusService;
import ti4.service.fow.FogPreset;
import ti4.service.fow.setup.FowSetupGameOptionsService.Category;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.service.option.FOWOptionService.FOWOptionCategory;
import ti4.testUtils.BaseTi4Test;

/**
 * The setup panel is a single Components V2 message. Discord rejects the whole edit when it holds more than 40
 * components, more than 4000 characters, an id over 100 characters or a select menu over 25 options, so every
 * step and sub-view is built here on a crowded game to fail the build instead of the GM's panel.
 */
class FowSetupPanelLimitsTest extends BaseTi4Test {

    private static final int PLAYER_COUNT = 12;
    private static final String LONG_NOTICE = "x".repeat(2000);

    private Game game;
    private FowSetupWizardState state;
    private final List<String> userIds = new ArrayList<>();

    @BeforeEach
    void setUpCrowdedGame() {
        game = new Game();
        game.setName("fow-setup-panel-limits");
        game.setFowMode(true);
        game.setAllianceMode(true);
        game.setFowOption(FOWOption.ANONYMOUS_COMMS, true);
        userIds.clear();
        for (int index = 0; index < PLAYER_COUNT; index++) {
            // 19 digits, the length of a real Discord snowflake, so id budgets are tested at full size.
            String userId = "10000000000000000" + String.format("%02d", index);
            userIds.add(userId);
            game.addPlayer(userId, "A player with a long display name " + index);
        }
        for (int index = 0; index < PLAYER_COUNT; index++) {
            game.setTile(new Tile("0g", "30" + (index % 6 + 1)));
        }
        state = FowSetupWizardService.loadState(game);
        state.setPlayerRolesInitialized(true);
        state.setScenarioNote("n".repeat(3000));
        state.getDiceRolls().put(userIds.getFirst(), 7);
        state.setDiceCount(2);
        state.setDiceSides(6);
    }

    @ParameterizedTest
    @EnumSource(FowSetupStep.class)
    void everyStepFits(FowSetupStep step) {
        state.setStep(step);
        assertFits(FowSetupPanel.build(game, state, LONG_NOTICE));
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void everyGameOptionsTabFits(Category tab) {
        state.setStep(FowSetupStep.GAME_OPTIONS);
        state.setStepTab(tab.name());
        assertFits(FowSetupPanel.build(game, state, LONG_NOTICE));
    }

    @ParameterizedTest
    @EnumSource(FOWOptionCategory.class)
    void everyEmbeddedFogTabFitsWithAndWithoutFowPlus(FOWOptionCategory tab) {
        state.setStep(FowSetupStep.FOG_TYPE);
        state.setSubView("fog:options:" + tab.name());
        assertFits(FowSetupPanel.build(game, state, LONG_NOTICE));
        FOWPlusService.enable(game);
        assertFits(FowSetupPanel.build(game, state, LONG_NOTICE));
    }

    @ParameterizedTest
    @EnumSource(FogPreset.class)
    void everyPresetPreviewFits(FogPreset preset) {
        state.setStep(FowSetupStep.FOG_TYPE);
        state.setSubView("fog:preset:" + preset.getId());
        assertFits(FowSetupPanel.build(game, state, LONG_NOTICE));
    }

    @Test
    void otherSubViewsFit() {
        assertSubViewFits(FowSetupStep.FOG_TYPE, "fog:plus");
        assertSubViewFits(FowSetupStep.GAME_TYPE, "type:base");
        assertSubViewFits(FowSetupStep.DEAL_SO, "start:confirm");
        assertSubViewFits(FowSetupStep.TABLE_ORDER, "order:manual");
        for (int page = 0; page < 6; page++) {
            assertSubViewFits(FowSetupStep.FACTIONS, "factions:ban:" + page);
        }
        for (FowSetupDecksService.DeckSlot slot : FowSetupDecksService.DECK_SLOTS) {
            for (int page = 0; page < 3; page++) {
                assertSubViewFits(FowSetupStep.DECKS, "decks:slot:" + slot.constantKey() + ":" + page);
            }
        }
        assertSubViewFits(FowSetupStep.DECKS, "decks:slot:sc:0");
        for (int page = 0; page < 6; page++) {
            state.setStep(FowSetupStep.NEUTRAL_PLAYER);
            state.setStepTab(String.valueOf(page));
            assertFits(FowSetupPanel.build(game, state, LONG_NOTICE));
        }
    }

    @Test
    void everyStageOfThePlayerSubViewFits() {
        String userId = userIds.getFirst();
        for (int page = 0; page < 6; page++) {
            assertSubViewFits(FowSetupStep.FACTIONS, FowSetupPanelFactions.playerSubView(userId, "auto", page));
        }

        state.getPendingFactionByUserId().put(userId, "arborec");
        assertSubViewFits(FowSetupStep.FACTIONS, FowSetupPanelFactions.playerSubView(userId, "auto", 0));
        for (int page = 0; page < 6; page++) {
            assertSubViewFits(FowSetupStep.FACTIONS, FowSetupPanelFactions.playerSubView(userId, "faction", page));
        }

        state.getPendingPositionByUserId().put(userId, "301");
        for (int page = 0; page < 6; page++) {
            assertSubViewFits(FowSetupStep.FACTIONS, FowSetupPanelFactions.playerSubView(userId, "auto", page));
        }

        Player gatePlayer = game.getPlayer(userIds.get(1));
        gatePlayer.setFaction(game, "ghost");
        gatePlayer.setColor("blue");
        assertSubViewFits(
                FowSetupStep.FACTIONS, FowSetupPanelFactions.playerSubView(gatePlayer.getUserID(), "auto", 0));
    }

    @Test
    void everyModalBuilds() {
        Player first = game.getPlayer(userIds.get(0));
        first.setFaction(game, "arborec");
        first.setColor("red");
        Player second = game.getPlayer(userIds.get(1));
        second.setFaction(game, "winnu");
        second.setColor("blue");

        assertBuilds(FowSetupPanelBasics::mapJsonModal);
        assertBuilds(FowSetupPanelDecks::importModal);
        assertBuilds(FowSetupPanelFactions::dealModal);
        assertBuilds(() -> FowSetupPanelFactions.allianceModal(game));
        assertBuilds(() -> FowSetupPanelOptions.scoringModal(game));
        assertBuilds(() -> FowSetupPanelPlayers.diceModal(state));
        assertBuilds(() ->
                FowSetupPanelFactions.positionModal("fowWizPosResolve_" + first.getUserID(), "Home Position", first));
    }

    private void assertSubViewFits(FowSetupStep step, String subView) {
        state.setStep(step);
        state.setSubView(subView);
        assertFits(FowSetupPanel.build(game, state, LONG_NOTICE));
        state.setSubView(null);
    }

    private static void assertBuilds(Supplier<Modal> modal) {
        assertThatCode(() -> assertThat(modal.get().getComponents()).hasSizeLessThanOrEqualTo(Modal.MAX_COMPONENTS))
                .doesNotThrowAnyException();
    }

    private static void assertFits(List<MessageTopLevelComponent> components) {
        assertThat(ComponentPanel.componentCount(components))
                .isLessThanOrEqualTo(Message.MAX_COMPONENT_COUNT_IN_COMPONENT_TREE);
        assertThat(ComponentPanel.characterCount(components))
                .isLessThanOrEqualTo(Message.MAX_CONTENT_LENGTH_COMPONENT_V2);
        for (Component component : components) {
            assertComponent(component);
        }
    }

    private static void assertComponent(Component component) {
        switch (component) {
            case Container container -> container.getComponents().forEach(FowSetupPanelLimitsTest::assertComponent);
            case Section section -> assertComponent(section.getAccessory());
            case ActionRow row -> row.getComponents().forEach(FowSetupPanelLimitsTest::assertComponent);
            case Button button -> assertThat(button.getCustomId()).hasSizeLessThanOrEqualTo(100);
            case StringSelectMenu menu -> {
                assertThat(menu.getCustomId()).hasSizeLessThanOrEqualTo(SelectMenu.ID_MAX_LENGTH);
                assertThat(menu.getOptions()).hasSizeLessThanOrEqualTo(SelectMenu.OPTIONS_MAX_AMOUNT);
            }
            default -> {}
        }
    }
}
