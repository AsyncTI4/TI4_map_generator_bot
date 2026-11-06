package ti4.service.fow.setup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.game.Game;
import ti4.logging.BotLogger;
import ti4.message.componentsV2.ComponentPanel;
import ti4.service.fow.setup.FowSetupGameOptionsService.Category;
import ti4.service.game.HomebrewService;
import ti4.service.game.HomebrewService.Homebrew;

final class FowSetupPanelOptions {

    private static final String TAB_PREFIX = "fowWizOptTab_";
    private static final String HOMEBREW_PREFIX = "fowWizHb_";
    private static final String TIGL = "fowWizTigl";
    private static final String ALLIANCE = "fowWizAllianceMode";
    private static final String REMOVE_ALL = "fowWizHbRemoveAll";
    private static final String SCORING_PRESET_PREFIX = "fowWizScore_";
    private static final String SCORING_MODAL = "fowWizScoreCustom~MDL";
    private static final String SCORING_RESOLVE = "fowWizScoreResolve";

    private FowSetupPanelOptions() {}

    static FowSetupPanel.View view(Game game, FowSetupWizardState state) {
        Category tab = selectedTab(state);
        List<ContainerChildComponent> body = new ArrayList<>();
        List<ActionRow> controls = new ArrayList<>();
        body.add(TextDisplay.of("### " + tab.title + "\n-# Same options as `/game weird_game_setup`. Turning an"
                + " option off only flips its flag; deck changes it made stay, and the note says which."));

        if (tab == Category.SCORING) {
            body.add(TextDisplay.of("**Currently:** " + game.getVp() + " VP · " + game.getMaxSOCountPerPlayer()
                    + " secret objectives per player · "
                    + game.getPublicObjectives1Peekable().size()
                    + " peekable stage 1 · "
                    + game.getPublicObjectives2Peekable().size() + " peekable stage 2"));
            controls.add(ActionRow.of(
                    Buttons.green(SCORING_PRESET_PREFIX + Homebrew.HB444, "Apply 4/4/4"),
                    Buttons.green(SCORING_PRESET_PREFIX + Homebrew.HB456, "Apply 4/5/6"),
                    Buttons.blue(SCORING_MODAL, "Custom scoring...")));
        } else {
            if (absolDrift(game, state, tab)) {
                body.add(TextDisplay.of("⚠ Absol Mode is on, but this wizard didn't turn it on (likely the old"
                        + " Homebrew button or a slash command), so the two Absol switches may not match the game."));
            }
            for (Homebrew hb : tab.items) {
                boolean on = FowSetupGameOptionsService.isOptionOn(game, state, hb);
                body.add(ComponentPanel.toggleSection(
                        hb.name, hb.description, on, ComponentPanel.toggleButton(HOMEBREW_PREFIX + hb, on)));
            }
        }
        if (tab == Category.HOMEBREW) {
            boolean tiglOn = FowSetupGameOptionsService.isTiglOn(game);
            body.add(ComponentPanel.toggleSection(
                    "Fractured TIGL",
                    "Standard TIGL can't combine with fog; this flags the game as Fractured-ladder TIGL instead",
                    tiglOn,
                    ComponentPanel.toggleButton(TIGL, tiglOn)));
            boolean allianceOn = game.isAllianceMode();
            body.add(ComponentPanel.toggleSection(
                    "Alliance Mode",
                    "Team variant; turns TIGL off. Pair players on the Factions step",
                    allianceOn,
                    ComponentPanel.toggleButton(ALLIANCE, allianceOn)));
            controls.add(ActionRow.of(Buttons.red(REMOVE_ALL, "Remove all homebrews")));
        }
        controls.addFirst(ActionRow.of(ComponentPanel.tabButtons(
                Arrays.asList(Category.values()), tab, category -> TAB_PREFIX + category, category -> category.title)));
        return FowSetupPanel.View.of(body, controls);
    }

    private static Category selectedTab(FowSetupWizardState state) {
        if (state.getStepTab() == null) return Category.HOMEBREW;
        try {
            return Category.valueOf(state.getStepTab());
        } catch (IllegalArgumentException e) {
            return Category.HOMEBREW;
        }
    }

    private static boolean absolDrift(Game game, FowSetupWizardState state, Category tab) {
        return tab == Category.DECKS
                && game.isAbsolMode()
                && !state.getEnabledGameOptions().contains(Homebrew.HBABSOLRELICSAGENDAS.name())
                && !state.getEnabledGameOptions().contains(Homebrew.HBABSOLTECHSMECHS.name());
    }

    @ButtonHandler(TAB_PREFIX)
    static void openTab(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        state.setStepTab(buttonID.substring(TAB_PREFIX.length()));
        FowSetupWizardService.saveState(game, state);
        FowSetupPanel.show(event, game);
    }

    @ButtonHandler(HOMEBREW_PREFIX)
    static void toggleHomebrew(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        Homebrew hb = Homebrew.valueOf(buttonID.substring(HOMEBREW_PREFIX.length()));
        FowSetupPanel.show(event, game, FowSetupGameOptionsService.toggle(event, game, hb));
    }

    @ButtonHandler(TIGL)
    static void toggleTigl(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupPanel.show(event, game, FowSetupGameOptionsService.toggleTigl(game));
    }

    @ButtonHandler(ALLIANCE)
    static void toggleAlliance(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupPanel.show(event, game, FowSetupGameOptionsService.toggleAllianceMode(game));
    }

    @ButtonHandler(REMOVE_ALL)
    static void removeAll(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupGameOptionsService.removeAllHomebrews(event, game);
        FowSetupPanel.show(event, game);
    }

    @ButtonHandler(SCORING_PRESET_PREFIX)
    static void applyScoringPreset(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        Homebrew hb = Homebrew.valueOf(buttonID.substring(SCORING_PRESET_PREFIX.length()));
        HomebrewService.applyHomebrew(game, event, hb);
        FowSetupPanel.show(event, game);
    }

    static Modal scoringModal(Game game) {
        return Modal.create(SCORING_RESOLVE, "Custom Scoring")
                .addComponents(
                        TextDisplay.of("Leave a field blank to keep its current value."),
                        Label.of("Victory points", scoringInput("vp", game.getVp())),
                        Label.of("Max secret objectives per player", scoringInput("so", game.getMaxSOCountPerPlayer())),
                        Label.of(
                                "Peekable stage 1 objectives",
                                scoringInput(
                                        "stage1",
                                        game.getPublicObjectives1Peekable().size())),
                        Label.of(
                                "Peekable stage 2 objectives",
                                scoringInput(
                                        "stage2",
                                        game.getPublicObjectives2Peekable().size())))
                .build();
    }

    private static TextInput scoringInput(String id, int current) {
        return TextInput.create(id, TextInputStyle.SHORT)
                .setRequired(false)
                .setPlaceholder("Current: " + current)
                .build();
    }

    @ButtonHandler(SCORING_MODAL)
    static void openScoringModal(ButtonInteractionEvent event, Game game) {
        event.replyModal(scoringModal(game)).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(SCORING_RESOLVE)
    static void resolveScoringModal(ModalInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupPanel.show(event, game, FowSetupGameOptionsService.applyCustomScoring(event, game));
    }
}
