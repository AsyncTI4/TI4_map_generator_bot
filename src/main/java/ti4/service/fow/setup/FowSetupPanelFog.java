package ti4.service.fow.setup;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.service.fow.AnonymousCommsService;
import ti4.service.fow.FOWPlusService;
import ti4.service.fow.FogPreset;
import ti4.service.fow.FogPresetService;
import ti4.service.option.FOWOptionService;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.service.option.FOWOptionService.FOWOptionCategory;

final class FowSetupPanelFog {

    private static final String PRESET_PREFIX = "fowWizFogPreset_";
    private static final String APPLY_PREFIX = "fowWizFogApply_";
    private static final String TAB_PREFIX = "fowWizFogTab_";
    private static final String TOGGLE_PREFIX = "fowWizFogOpt_";
    private static final String FOWPLUS = "fowWizFogPlusMenu";
    private static final String FOWPLUS_SET_PREFIX = "fowWizFowPlusTurn_";
    private static final String PRESET_SUBVIEW = "fog:preset:";
    private static final String OPTIONS_SUBVIEW = "fog:options:";
    private static final String FOWPLUS_SUBVIEW = "fog:plus";

    private FowSetupPanelFog() {}

    static FowSetupPanel.View view(Game game, FowSetupWizardState state) {
        String subView = StringUtils.defaultString(state.getSubView());
        if (subView.startsWith(PRESET_SUBVIEW)) {
            return presetPreview(game, FogPreset.fromId(subView.substring(PRESET_SUBVIEW.length())));
        }
        if (subView.startsWith(OPTIONS_SUBVIEW)) {
            return options(game, FOWOptionCategory.valueOf(subView.substring(OPTIONS_SUBVIEW.length())));
        }
        if (FOWPLUS_SUBVIEW.equals(subView)) {
            return fowPlus(game);
        }
        return overview(game);
    }

    private static FowSetupPanel.View overview(Game game) {
        StringBuilder text = new StringBuilder("**")
                .append(FOWOptionService.status(game))
                .append("**\nPick a preset to see what it changes before you apply it, then fine-tune single")
                .append(" options if you want. **Show Game As...** in `/fow gm` shows what a player would see.\n\n")
                .append(FogPresetService.presetList())
                .append("\n\n");
        for (FOWOptionCategory category : FOWOptionCategory.values()) {
            List<String> on = FOWOptionService.optionsIn(category).stream()
                    .filter(option -> FOWOptionService.isEnabled(game, option))
                    .map(FOWOption::getTitle)
                    .toList();
            text.append("**")
                    .append(category.getTitle())
                    .append(":** ")
                    .append(on.isEmpty() ? "_all off_" : String.join(", ", on))
                    .append('\n');
        }
        FogPreset current = FogPresetService.matching(game).orElse(null);
        List<Button> presets = new ArrayList<>();
        for (FogPreset preset : FogPreset.values()) {
            presets.add(
                    preset == current
                            ? Buttons.gray(PRESET_PREFIX + preset.getId(), preset.getTitle())
                            : Buttons.blue(PRESET_PREFIX + preset.getId(), preset.getTitle()));
        }
        List<Button> tools = List.of(
                Buttons.blue(TAB_PREFIX + FOWOptionCategory.COMMS, "Fine-tune options"),
                Buttons.blue(FOWPLUS, "FoW+ ..."));
        List<ContainerChildComponent> body = List.of(TextDisplay.of(text.toString()));
        return FowSetupPanel.View.of(body, List.of(ActionRow.of(presets), ActionRow.of(tools)));
    }

    private static FowSetupPanel.View presetPreview(Game game, FogPreset preset) {
        List<ContainerChildComponent> body = List.of(TextDisplay.of(FogPresetService.preview(game, preset)));
        List<Button> buttons = List.of(
                Buttons.green(APPLY_PREFIX + preset.getId(), "Apply " + preset.getTitle()), FowSetupPanel.backButton());
        return FowSetupPanel.View.sub(body, List.of(ActionRow.of(buttons)), false);
    }

    private static FowSetupPanel.View options(Game game, FOWOptionCategory tab) {
        List<ContainerChildComponent> body = new ArrayList<>();
        body.add(TextDisplay.of("### Fog options · " + tab.getTitle() + "\n-# Same switches as `/fow fow_options`."));
        body.addAll(FOWOptionService.optionSections(game, tab, TOGGLE_PREFIX));
        List<Button> tools = new ArrayList<>();
        tools.add(Buttons.blue(FOWPLUS, "FoW+ ..."));
        if (tab == FOWOptionCategory.COMMS && game.getFowOption(FOWOption.ANONYMOUS_COMMS)) {
            tools.add(Buttons.blue(AnonymousCommsService.SETTINGS_BUTTON, "Anonymous comms settings..."));
        }
        if (tab == FOWOptionCategory.FLOW) {
            tools.add(Buttons.blue("gmQolSettings~MDL", "Fog QoL 01 settings..."));
        }
        tools.add(FowSetupPanel.backButton());
        return FowSetupPanel.View.sub(
                body, List.of(ActionRow.of(FOWOptionService.tabButtons(tab, TAB_PREFIX)), ActionRow.of(tools)), false);
    }

    private static FowSetupPanel.View fowPlus(Game game) {
        boolean active = FOWPlusService.isActive(game);
        Button toggle = active
                ? Buttons.red(FOWPLUS_SET_PREFIX + false, "Turn FoW+ off")
                : Buttons.green(FOWPLUS_SET_PREFIX + true, "Turn FoW+ on");
        List<ContainerChildComponent> body = List.of(TextDisplay.of(FOWOptionService.fowPlusText(game)));
        return FowSetupPanel.View.sub(body, List.of(ActionRow.of(toggle, FowSetupPanel.backButton())), false);
    }

    @ButtonHandler(value = PRESET_PREFIX)
    static void previewPreset(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FogPreset preset = FogPreset.fromId(buttonID.substring(PRESET_PREFIX.length()));
        FowSetupPanel.openSubView(event, game, PRESET_SUBVIEW + preset.getId(), null);
    }

    @ButtonHandler(APPLY_PREFIX)
    static void applyPreset(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FogPreset preset = FogPreset.fromId(buttonID.substring(APPLY_PREFIX.length()));
        FogPresetService.applyAndAnnounce(game, preset);
        FowSetupPanel.openSubView(event, game, null, "Applied the " + preset.getTitle() + " preset.");
    }

    @ButtonHandler(TAB_PREFIX)
    static void openOptionsTab(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FOWOptionCategory tab = FOWOptionCategory.valueOf(buttonID.substring(TAB_PREFIX.length()));
        FowSetupPanel.openSubView(event, game, OPTIONS_SUBVIEW + tab.name(), null);
    }

    @ButtonHandler(TOGGLE_PREFIX)
    static void toggleOption(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FOWOption option = FOWOptionService.applyToggle(game, buttonID.substring(TOGGLE_PREFIX.length()));
        FowSetupPanel.openSubView(
                event, game, OPTIONS_SUBVIEW + option.getCategory().name(), null);
    }

    @ButtonHandler(FOWPLUS)
    static void openFowPlus(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupPanel.openSubView(event, game, FOWPLUS_SUBVIEW, null);
    }

    @ButtonHandler(FOWPLUS_SET_PREFIX)
    static void setFowPlus(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        boolean active = Boolean.parseBoolean(buttonID.substring(FOWPLUS_SET_PREFIX.length()));
        FOWPlusService.setActive(game, active);
        FowSetupPanel.openSubView(event, game, null, "FoW+ turned " + (active ? "on" : "off") + ".");
    }
}
