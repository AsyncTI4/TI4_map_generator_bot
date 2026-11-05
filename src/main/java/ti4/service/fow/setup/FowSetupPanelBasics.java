package ti4.service.fow.setup;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.URLReaderHelper;
import ti4.helpers.async.JimboHandlers;
import ti4.logging.BotLogger;
import ti4.service.game.StartPhaseService;
import ti4.service.map.AddTileListService;
import ti4.service.map.MapJsonIOService;
import ti4.service.objectives.DrawSecretService;
import ti4.service.option.TEOptionService;

final class FowSetupPanelBasics {

    private static final String EXPANSION_PREFIX = "fowWizExp_";
    private static final String BASE_CONFIRM = "fowWizBaseConfirm";
    private static final String SCENARIO_MODAL = "fowWizScenario~MDL";
    private static final String SCENARIO_RESOLVE = "fowWizScenarioResolve";
    private static final String MAP_JSON_MODAL = "fowWizMapJson~MDL";
    private static final String MAP_JSON_RESOLVE = "fowWizMapJsonResolve";
    private static final String MAP_STRING_MODAL = "fowWizMapString~MDL";
    private static final String MAP_STRING_RESOLVE = "fowWizMapStringResolve";
    private static final String MAP_DEV_TILES = "fowWizMapDevTiles";
    private static final String MAP_BUILDER = "fowWizMapBuilder";
    private static final String DEAL_SO = "fowWizDealSo";
    private static final String START = "fowWizStartAsk";
    private static final String START_CONFIRM = "fowWizStartConfirm";
    private static final String BASE_SUBVIEW = "type:base";
    private static final String START_SUBVIEW = "start:confirm";

    private FowSetupPanelBasics() {}

    static FowSetupPanel.View gameType(Game game, FowSetupWizardState state) {
        if (BASE_SUBVIEW.equals(state.getSubView())) {
            return baseGameConfirm();
        }
        List<ContainerChildComponent> body = new ArrayList<>();
        body.add(TextDisplay.of("**Expansion:** " + FowSetupPanel.expansionLabel(game)
                + "\n-# New PoK and Old PoK pick the card art and errata. Thunder's Edge also adds the Thunder's Edge"
                + " action cards and the Silver Flame and Quantumcore relics."));
        String note = state.getScenarioNote();
        body.add(Section.of(
                Buttons.blue(SCENARIO_MODAL, "Edit note"),
                TextDisplay.of("**Scenario note**\n"
                        + (StringUtils.isBlank(note)
                                ? "-# None. Free text for house rules or the scenario name; no game logic reads it."
                                : StringUtils.abbreviate(note, 1000)))));
        body.add(TextDisplay.of("**Alternate modes** hand off to their own setup flows. Twilight's Fall and Franken"
                + " post their later messages by their own rules, not only in this GM room."));

        String current = currentExpansion(game);
        List<Button> expansions = List.of(
                expansionButton("newPoK", "New PoK", current),
                expansionButton("oldPoK", "Old PoK", current),
                expansionButton("te", "Thunder's Edge + New PoK", current),
                Buttons.red(EXPANSION_PREFIX + "base", "Base game only"));
        List<Button> modes = List.of(
                Buttons.green("getHomebrewButtons", "Supported Homebrew"),
                Buttons.green("offerTEOptionButtons", "Galactic Events"),
                Buttons.green("startTFGame", "Start Twilight's Fall"),
                Buttons.green("frankenSetup", "Start Franken"));
        return FowSetupPanel.View.of(body, List.of(ActionRow.of(expansions), ActionRow.of(modes)));
    }

    private static String currentExpansion(Game game) {
        if (game.isBaseGameMode()) return "base";
        if (game.isThundersEdge()) return "te";
        if ("true".equals(game.getStoredValue("useOldPok"))) return "oldPoK";
        return "newPoK";
    }

    private static Button expansionButton(String choice, String label, String current) {
        return choice.equals(current)
                ? Buttons.gray(EXPANSION_PREFIX + choice, label)
                : Buttons.blue(EXPANSION_PREFIX + choice, label);
    }

    private static FowSetupPanel.View baseGameConfirm() {
        List<ContainerChildComponent> body = List.of(TextDisplay.of("""
                ### Switch to base game only?
                This removes all PoK expansion content from the game.
                - Don't continue if you plan to use PoK, Discordant Stars, Blue Reverie, Thunder's Edge or other homebrew content.
                - This only sets the base game flag. It does **not** open the Milty draft settings, since fog games don't use Milty."""));
        List<Button> buttons = List.of(Buttons.red(BASE_CONFIRM, "Switch to base game"), FowSetupPanel.backButton());
        return FowSetupPanel.View.sub(body, List.of(ActionRow.of(buttons)), true);
    }

    @ButtonHandler(EXPANSION_PREFIX)
    static void chooseExpansion(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String choice = buttonID.substring(EXPANSION_PREFIX.length());
        switch (choice) {
            case "base" -> FowSetupPanel.openSubView(event, game, BASE_SUBVIEW, null);
            case "te" -> {
                game.setBaseGameMode(false);
                FowSetupWizardService.applyThundersEdge(event, game);
                FowSetupPanel.show(event, game, "Set game to use Thunder's Edge + New PoK components.");
            }
            default -> {
                game.setBaseGameMode(false);
                TEOptionService.chooseExp(event, game, "chooseExp_" + choice);
                FowSetupPanel.show(event, game);
            }
        }
    }

    @ButtonHandler(BASE_CONFIRM)
    static void confirmBaseGame(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        game.setBaseGameMode(true);
        FowSetupPanel.openSubView(event, game, null, "Game switched to base-game-only mode.");
    }

    @ButtonHandler(SCENARIO_MODAL)
    static void openScenarioModal(ButtonInteractionEvent event, Game game) {
        String note = FowSetupWizardService.loadState(game).getScenarioNote();
        TextInput.Builder input = TextInput.create("scenarioNote", TextInputStyle.PARAGRAPH)
                .setPlaceholder("House rules or scenario for this game")
                .setRequired(false);
        if (StringUtils.isNotBlank(note)) {
            input.setValue(StringUtils.left(note, TextInput.MAX_VALUE_LENGTH));
        }
        Modal modal = Modal.create(SCENARIO_RESOLVE, "Scenario Note")
                .addComponents(Label.of("Scenario / House Rules", input.build()))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(SCENARIO_RESOLVE)
    static void resolveScenarioModal(ModalInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        state.setScenarioNote(FowSetupPanel.value(event, "scenarioNote"));
        FowSetupWizardService.saveState(game, state);
        FowSetupPanel.show(event, game, "Scenario note saved.");
    }

    static FowSetupPanel.View mapLoad(Game game) {
        int tiles = game.getTileMap().size();
        long homeSystems =
                game.getTileMap().values().stream().filter(Tile::isHomeSystem).count();
        List<ContainerChildComponent> body = List.of(TextDisplay.of((tiles == 0
                        ? "**No map yet.**"
                        : "**Map:** " + tiles + " tiles, " + homeSystems + " home-system tiles.")
                + "\n⚠ Both imports **replace the entire map**, including home systems that placed players. Load the map"
                + " before you place anyone; players placed earlier need a new position.\n"
                + "-# The info thread lists the map-editing commands for tokens, lore and border anomalies."));
        List<Button> buttons = List.of(
                Buttons.blue(MAP_JSON_MODAL, "Import JSON (URL or file)"),
                Buttons.blue(MAP_STRING_MODAL, "Import map string"),
                Buttons.green(MAP_BUILDER, "Interactive builder"),
                Buttons.gray(MAP_DEV_TILES, "Dev: test tiles"));
        return FowSetupPanel.View.of(body, List.of(ActionRow.of(buttons)));
    }

    static Modal mapJsonModal() {
        return FowSetupPanel.urlOrFileModal(
                MAP_JSON_RESOLVE,
                "Import Map from JSON",
                "This **replaces the entire map**. Paste the URL of an exported map or upload the `.json` file,"
                        + " e.g. from https://stabar-ti.github.io/hex-Custom-async-ti-hyperlink/",
                "https://your.url/fow123_map.json");
    }

    @ButtonHandler(MAP_JSON_MODAL)
    static void openMapJsonModal(ButtonInteractionEvent event) {
        event.replyModal(mapJsonModal()).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(MAP_JSON_RESOLVE)
    static void resolveMapJsonModal(ModalInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String source = FowSetupPanel.urlOrFile(event);
        if (source.isEmpty()) {
            FowSetupPanel.show(event, game, "Give a URL or upload a file. Nothing was imported.");
            return;
        }
        String json = URLReaderHelper.readFromURL(source, event.getMessageChannel());
        if (json == null) {
            FowSetupPanel.show(event, game, "Could not read the map JSON. Nothing was imported.");
            return;
        }
        boolean imported = MapJsonIOService.importMapFromJson(game, json, event.getMessageChannel());
        FowSetupPanel.show(
                event,
                game,
                imported ? "Map imported." : "The map import failed. The error is posted in this channel.");
    }

    @ButtonHandler(MAP_STRING_MODAL)
    static void openMapStringModal(ButtonInteractionEvent event, Game game) {
        event.replyModal(AddTileListService.buildMapStringModal(game, MAP_STRING_RESOLVE))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(MAP_STRING_RESOLVE)
    static void resolveMapStringModal(ModalInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String mapString = FowSetupPanel.value(event, "mapString");
        if (mapString.isEmpty()) {
            FowSetupPanel.show(event, game, "The map string was empty. Nothing was imported.");
            return;
        }
        boolean imported = AddTileListService.addTileListToMap(game, mapString, event);
        FowSetupPanel.show(
                event,
                game,
                imported
                        ? "Map string imported."
                        : "The map string import failed. The error is posted in this channel.");
    }

    @ButtonHandler(MAP_BUILDER)
    static void openMapBuilder(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        JimboHandlers.sendMainMenu(event.getMessageChannel(), game);
        FowSetupPanel.show(event, game, "Interactive builder posted below.");
    }

    @ButtonHandler(MAP_DEV_TILES)
    static void placeDevTestTiles(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        game.setTile(new Tile("18", "000"));
        game.setTile(new Tile("0g", "101"));
        game.setTile(new Tile("0g", "104"));
        FowSetupPanel.show(event, game, "Placed test tiles: 18@000, 0g@101, 0g@104.");
    }

    static FowSetupPanel.View start(Game game, FowSetupWizardState state) {
        Checklist checklist = checklist(game, state);
        if (START_SUBVIEW.equals(state.getSubView())) {
            List<ContainerChildComponent> body = List.of(TextDisplay.of(checklist.text()
                    + "\n**Start the game?** This moves it into the strategy phase, the same as `/game start_phase`."
                    + " Players can act right away."));
            List<Button> buttons = List.of(Buttons.green(START_CONFIRM, "Start the game"), FowSetupPanel.backButton());
            return FowSetupPanel.View.sub(body, List.of(ActionRow.of(buttons)), checklist.warning());
        }
        List<ContainerChildComponent> body = List.of(TextDisplay.of(checklist.text()
                + "\n-# Dealing gives every real player 2 secret objectives. It only works while nobody has any"
                + " yet; give a player who is missing them some with `/so draw`."));
        Button deal = Buttons.green(DEAL_SO, "Deal 2 secret objectives to all");
        List<Button> buttons =
                List.of(anyoneHasSecrets(game) ? deal.asDisabled() : deal, Buttons.green(START, "Start game"));
        return FowSetupPanel.View.main(body, List.of(ActionRow.of(buttons)), checklist.warning());
    }

    private record Checklist(String text, boolean warning) {}

    private static Checklist checklist(Game game, FowSetupWizardState state) {
        List<Player> candidates = FowSetupFactionService.setupCandidates(game, state);
        long placed = candidates.stream()
                .filter(player ->
                        FowSetupFactionService.isPlacedOnMap(game, player) && StringUtils.isNotBlank(player.getColor()))
                .count();
        long withSecrets = game.getRealPlayers().stream()
                .filter(FowSetupPanelBasics::hasSecrets)
                .count();
        int tiles = game.getTileMap().size();
        Player speaker = game.getSpeaker();
        Player neutral = game.getPlayer(Constants.dicecordId);
        boolean neutralHasColor = neutral != null && StringUtils.isNotBlank(neutral.getColor());
        boolean allPlaced = !candidates.isEmpty() && placed == candidates.size();

        String text = "### Ready to start?\n"
                + check(tiles > 0, tiles > 0 ? "Map loaded (" + tiles + " tiles)" : "No map loaded") + "\n"
                + check(allPlaced, placed + "/" + candidates.size() + " players placed with faction and colour")
                + "\n"
                + check(speaker != null, speaker == null ? "No speaker set" : "Speaker: " + speaker.getUserName())
                + "\n"
                + (neutralHasColor
                        ? "✅ Neutral player: " + neutral.getColor()
                        : "▫️ Neutral player: optional, no colour")
                + "\n"
                + check(
                        withSecrets > 0 && withSecrets == game.getRealPlayers().size(),
                        "Secret objectives dealt to " + withSecrets + "/"
                                + game.getRealPlayers().size() + " players")
                + "\n";
        boolean warning = tiles == 0 || !allPlaced || speaker == null;
        return new Checklist(text, warning);
    }

    private static String check(boolean ok, String text) {
        return (ok ? "✅ " : "⚠️ ") + text;
    }

    static boolean anyoneHasSecrets(Game game) {
        return game.getRealPlayers().stream().anyMatch(FowSetupPanelBasics::hasSecrets);
    }

    private static boolean hasSecrets(Player player) {
        return !player.getSecretsUnscored().isEmpty()
                || !player.getSecretsScored().isEmpty();
    }

    @ButtonHandler(DEAL_SO)
    static void dealSecrets(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        if (anyoneHasSecrets(game)) {
            FowSetupPanel.show(
                    event,
                    game,
                    "Secret objectives were already dealt, so nothing was dealt again. Use `/so draw` for a"
                            + " player who is missing them.");
            return;
        }
        DrawSecretService.dealSOToAll(event, 2, game);
        FowSetupPanel.show(event, game, "Dealt 2 secret objectives to every player.");
    }

    @ButtonHandler(START)
    static void askToStart(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupPanel.openSubView(event, game, START_SUBVIEW, null);
    }

    @ButtonHandler(START_CONFIRM)
    static void startGame(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        StartPhaseService.startPhase(event, game, "strategy");
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        state.setStep(FowSetupStep.DONE);
        state.setSubView(null);
        FowSetupWizardService.saveState(game, state);
        FowSetupPanel.show(event, game, "Game started.");
    }

    static FowSetupPanel.View done() {
        List<ContainerChildComponent> body = List.of(
                TextDisplay.of(
                        "Setup is complete. Use the step list to revisit anything; the wizard does not lock.\n"
                                + "**Close Wizard** removes this panel, archives its info threads and makes the next"
                                + " `/fow setup_beta` start at step 1. Your answers (factions, map, rolls, options) stay as set."));
        return FowSetupPanel.View.of(body, List.of());
    }
}
