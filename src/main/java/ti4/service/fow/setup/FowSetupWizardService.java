package ti4.service.fow.setup;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.special.SetupNeutralPlayer;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ThreadGetter;
import ti4.helpers.async.JimboHandlers;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.fow.FOWPlusService;
import ti4.service.fow.GMService;
import ti4.service.game.StartPhaseService;
import ti4.service.map.AddTileListService;
import ti4.service.objectives.DrawSecretService;
import ti4.service.option.FOWOptionService;
import ti4.service.option.FOWOptionService.FOWOption;

/**
 * Orchestrates the GM-only `/fow setup` wizard: a redrawable panel of buttons in the GM room that
 * walks a new or experienced GM through setting up a Fog of War game, step by step, without ever
 * posting privileged content to the public game channel. See docs/fow-setup-wizard plan for design
 * background.
 */
public final class FowSetupWizardService {

    private static final String STORE_KEY = "fowSetupWizard";

    private FowSetupWizardService() {}

    public static FowSetupWizardState loadState(Game game) {
        String json = game.getStoredValue(STORE_KEY);
        if (StringUtils.isBlank(json)) {
            return new FowSetupWizardState();
        }
        try {
            return JsonMapperManager.basic().readValue(json, FowSetupWizardState.class);
        } catch (Exception e) {
            BotLogger.error("Failed to parse FoW setup wizard state for game " + game.getName(), e);
            return new FowSetupWizardState();
        }
    }

    public static void saveState(Game game, FowSetupWizardState state) {
        game.setStoredValue(STORE_KEY, JsonMapperManager.basic().writeValueAsString(state));
    }

    /** Every call to `/fow setup` (or the Refresh button) deletes the old panel and reposts the current step. */
    public static void openOrRefresh(Game game) {
        FowSetupWizardState state = loadState(game);
        TextChannel gmChannel = GMService.getGMChannel(game);
        if (state.getPanelMessageId() != null) {
            gmChannel.deleteMessageById(state.getPanelMessageId()).queue(Consumers.nop(), BotLogger::catchRestError);
        }
        renderStep(game, gmChannel, state);
    }

    public static boolean requireGM(GenericInteractionCreateEvent event, Game game) {
        Player player = game.getPlayer(event.getUser().getId());
        if (player == null || !game.getPlayersWithGMRole().contains(player)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Only a GM can use this wizard.");
            return false;
        }
        return true;
    }

    @ButtonHandler("fowSetupRefresh")
    public static void refreshButton(ButtonInteractionEvent event, Game game) {
        if (!requireGM(event, game)) return;
        openOrRefresh(game);
    }

    @ButtonHandler("fowSetupGoto_")
    public static void gotoStep(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!requireGM(event, game)) return;
        FowSetupWizardState state = loadState(game);
        state.setStep(FowSetupStep.valueOf(buttonID.replace("fowSetupGoto_", "")));
        saveState(game, state);
        openOrRefresh(game);
    }

    // ---------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------

    private static void renderStep(Game game, TextChannel gmChannel, FowSetupWizardState state) {
        StringBuilder sb =
                new StringBuilder("# FoW Game Setup - Step " + (state.getStep().ordinal() + 1) + "/"
                        + FowSetupStep.values().length + ": " + stepTitle(state.getStep()) + "\n\n");
        List<Button> buttons = new ArrayList<>();
        switch (state.getStep()) {
            case GAME_TYPE -> renderGameType(game, state, sb, buttons);
            case MAP_LOAD -> renderMapLoad(game, state, sb, buttons);
            case FACTIONS -> FowSetupFactionService.render(game, state, sb, buttons);
            case TABLE_ORDER -> FowSetupTableOrderService.render(game, state, sb, buttons);
            case FOG_TYPE -> renderFogType(game, state, sb, buttons);
            case DECKS -> renderDecks(game, state, sb, buttons);
            case NEUTRAL_PLAYER -> renderNeutralPlayer(game, state, sb, buttons);
            case DEAL_SO -> renderDealSO(game, state, sb, buttons);
            case DONE -> renderDone(game, state, sb, buttons);
        }

        // Nav buttons always get their own row, separate from the step's own action buttons.
        List<ActionRow> rows = new ArrayList<>();
        for (List<Button> partition : ListUtils.partition(MessageHelper.sanitizeButtons(buttons, gmChannel), 5)) {
            rows.add(ActionRow.of(partition));
        }
        rows.add(ActionRow.of(buildNavButtons(state)));

        gmChannel
                .sendMessage(sb.toString())
                .setComponents(rows)
                .queue(
                        message -> {
                            state.setPanelMessageId(message.getIdLong());
                            saveState(game, state);
                        },
                        BotLogger::catchRestError);
        postStepInfoThreadOnce(game, state);
    }

    private static String stepTitle(FowSetupStep step) {
        return switch (step) {
            case GAME_TYPE -> "Game Type & Scenario";
            case MAP_LOAD -> "Load the Map";
            case FACTIONS -> "Assign Factions & Positions";
            case TABLE_ORDER -> "Table / Seat Order";
            case FOG_TYPE -> "Fog of War Type";
            case DECKS -> "Decks";
            case NEUTRAL_PLAYER -> "Neutral Player";
            case DEAL_SO -> "Deal Secret Objectives & Start";
            case DONE -> "Setup Complete";
        };
    }

    private static List<Button> buildNavButtons(FowSetupWizardState state) {
        List<Button> navButtons = new ArrayList<>();
        FowSetupStep[] steps = FowSetupStep.values();
        int idx = state.getStep().ordinal();
        if (idx > 0) {
            navButtons.add(Buttons.gray("fowSetupGoto_" + steps[idx - 1], "⬅ Previous Step"));
        }
        if (idx < steps.length - 1) {
            navButtons.add(Buttons.blue("fowSetupGoto_" + steps[idx + 1], "Next Step ➡"));
        }
        navButtons.add(Buttons.gray("fowSetupRefresh", "Refresh"));
        return navButtons;
    }

    private static void postStepInfoThreadOnce(Game game, FowSetupWizardState state) {
        String info = stepInfo(state.getStep());
        if (info == null || state.getInfoThreadsPosted().contains(state.getStep())) return;
        ThreadGetter.getThreadInChannel(
                GMService.getGMChannel(game),
                game.getName() + "-setup-" + state.getStep().name().toLowerCase(),
                true,
                false,
                thread -> MessageHelper.sendMessageToChannel(thread, info));
        state.getInfoThreadsPosted().add(state.getStep());
        saveState(game, state);
    }

    private static String stepInfo(FowSetupStep step) {
        return switch (step) {
            case MAP_LOAD -> """
                ### Map-editing commands you'll likely need once the base map is in
                - `/map add_tile` / `/map add_tile_list` - place additional tiles or a whole batch at once
                - `/tokens add_token` - add tokens (frontier, anomaly, border tokens, etc.) to a tile
                - `/special2 lore` - manage FoW Lore triggers on tiles
                - `/map add_border_anomaly` / `/map remove_border_anomaly` - manage border anomalies

                ### If something looks wrong after loading a map
                There's no automated map checker yet, so double check manually for: tiles placed on the \
                wrong position (compare against your source), missing home systems for every player \
                slot, and anomalies/wormholes that didn't come through from a pasted map string \
                (re-add them with the commands above). If in doubt, `/map show_map_string` shows the \
                current map as a string you can diff against your source.
                """;
            case FACTIONS -> """
                ### Faction assignment options
                **Manual** - use the buttons on the panel above.
                **Ban Factions** - toggles factions in/out of the shared Franken-eligible pool (same pool \
                used by Manual, Deal Factions, and `/franken`). Ghosts of Creuss is auto-banned in FoW games.
                **Deal Factions (Mini Draft)** - set how many factions to offer each unassigned player, and \
                whether the same faction can appear on more than one player's list. Each player privately \
                picks one from their own dealt list in their own channel (no bag-passing - this is a \
                lightweight one-shot deal, not a full Franken draft). Once everyone has a faction, come \
                back here and use "Assign Position" / "Randomize All Positions" to place them.
                **CSV roster upload** - not built yet. The idea: GM uploads a comma-separated list of \
                possible factions per player (`player1a, player1b, player1c; player2a, ...`) generated by \
                a companion website, and the wizard deals accordingly. Documented here for later.
                """;
            case DECKS -> """
                ### Deck commands
                - `/game set_deck` - pick specific Action Card / Secret Objective / Public Objective / \
                Relic / Agenda / Event / Exploration / Strategy Card / Technology decks for this game.
                - There is currently no command to remove a single card from a deck once chosen - if you \
                need that, ask a dev for now. It's a known gap.
                - `/special2 import_deck_config` (with a `file` attachment or `url`) imports a deck-set \
                config exported from the companion Ti-Async-Deckcard-tool Deck-editor: \
                https://stabar-ti.github.io/Ti-Async-Deckcard-tool/
                """;
            case NEUTRAL_PLAYER -> """
                ### Adding units for the neutral player
                Once the neutral player has a color (button above), add their units the normal way, \
                targeting that color, e.g. `/tokens add_token` or the unit-add commands with the neutral \
                color selected.
                """;
            default -> null;
        };
    }

    // ---------------------------------------------------------------------------------------
    // Step 0: Game type & scenario
    // ---------------------------------------------------------------------------------------

    private static void renderGameType(Game game, FowSetupWizardState state, StringBuilder sb, List<Button> buttons) {
        sb.append("These mirror the normal (non-fog) game setup buttons - clicking them always replies in this ")
                .append("GM room, not the public channel, since they just reply wherever they were clicked.\n\n")
                .append("Currently: Thunder's Edge = ")
                .append(game.isThundersEdge())
                .append(", Twilight's Fall = ")
                .append(game.isTwilightsFallMode())
                .append(", Franken = ")
                .append(game.isFrankenGame())
                .append("\n");
        if (StringUtils.isNotBlank(state.getScenarioNote())) {
            sb.append("**Scenario note:** ").append(state.getScenarioNote()).append("\n");
        }

        sb.append("\n**Which expansion?**\n");
        buttons.add(Buttons.red("fowSetupBaseGameOnly", "Start Base Game Only Setup"));
        buttons.add(Buttons.green("chooseExp_newPoK", "New PoK"));
        buttons.add(Buttons.gray("chooseExp_oldPoK", "Old PoK"));
        buttons.add(Buttons.blue("chooseExp_te", "Thunder's Edge + New PoK"));

        sb.append("**Alternate game modes**\n");
        buttons.add(Buttons.green("getHomebrewButtons", "Supported Homebrew"));
        buttons.add(Buttons.green("offerTEOptionButtons", "Galactic Events"));
        buttons.add(Buttons.green("startTFGame", "Start Twilight's Fall Game"));
        buttons.add(Buttons.green("frankenSetup", "Start Franken Setup"));

        buttons.add(Buttons.gray("fowSetupScenario~MDL", "Set Scenario Note"));
    }

    @ButtonHandler("fowSetupBaseGameOnly")
    public static void promptBaseGameOnly(ButtonInteractionEvent event, Game game) {
        if (!requireGM(event, game)) return;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                """
                ## Base Game Setup Warning
                This will switch the game to **base game only**, with no PoK expansion content.

                - If you were planning to use PoK, Discordant Stars, Blue Reverie, Thunder's Edge, or other homebrew content, do not continue.
                - Unlike the normal (non-fog) setup flow, this only flips the base-game flag - it does **not** continue into the Milty draft settings menu.

                Press **Continue Base Game Setup** ___only___ if that is what you want.""",
                List.of(Buttons.red("fowSetupBaseGameOnlyConfirm", "Continue Base Game Setup"), Buttons.CANCEL));
    }

    @ButtonHandler("fowSetupBaseGameOnlyConfirm")
    public static void confirmBaseGameOnly(ButtonInteractionEvent event, Game game) {
        if (!requireGM(event, game)) return;
        game.setBaseGameMode(true);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Game switched to base-game-only mode.");
        openOrRefresh(game);
    }

    @ButtonHandler("fowSetupScenario~MDL")
    public static void openScenarioModal(ButtonInteractionEvent event) {
        TextInput note = TextInput.create("scenarioNote", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Free-text note about house rules / scenario for this game")
                .setRequired(false)
                .build();
        Modal modal = Modal.create("fowSetupScenarioResolve", "Scenario Note")
                .addComponents(Label.of("Scenario / House Rules", note))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler("fowSetupScenarioResolve")
    public static void resolveScenarioModal(ModalInteractionEvent event, Game game) {
        FowSetupWizardState state = loadState(game);
        state.setScenarioNote(event.getValue("scenarioNote").getAsString());
        saveState(game, state);
        openOrRefresh(game);
    }

    // ---------------------------------------------------------------------------------------
    // Step 1: Load the map
    // ---------------------------------------------------------------------------------------

    private static void renderMapLoad(Game game, FowSetupWizardState state, StringBuilder sb, List<Button> buttons) {
        sb.append("Choose how to load the map. See the info thread below for follow-up commands and ")
                .append("troubleshooting once tiles are in.\n\n")
                .append("a. **Import JSON** - click below to import from a URL, e.g. exported from ")
                .append("https://stabar-ti.github.io/hex-Custom-async-ti-hyperlink/\n")
                .append("b. **Import Map String** - click below to paste a map string.\n")
                .append("c. **Interactive Builder** - click below to open it directly.\n");

        buttons.add(Buttons.blue("fowSetupMapJson~MDL", "Import from JSON"));
        buttons.add(Buttons.blue("fowSetupMapString~MDL", "Import Map String"));
        buttons.add(Buttons.green("fowSetupMapBuilder", "Open Interactive Builder"));
        buttons.add(Buttons.gray("fowSetupMapDevTestTiles", "Dev: Test Tiles (18@000, 0g@101/104)"));
    }

    @ButtonHandler("fowSetupMapBuilder")
    public static void openMapBuilder(ButtonInteractionEvent event, Game game) {
        if (!requireGM(event, game)) return;
        JimboHandlers.postMainMenu(event, game);
    }

    /** Developer convenience for quickly testing later wizard steps without hand-building a map. */
    @ButtonHandler("fowSetupMapDevTestTiles")
    public static void placeDevTestTiles(ButtonInteractionEvent event, Game game) {
        if (!requireGM(event, game)) return;
        game.setTile(new Tile("18", "000"));
        game.setTile(new Tile("0g", "101"));
        game.setTile(new Tile("0g", "104"));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Placed test tiles: 18@000, 0g@101, 0g@104.");
        openOrRefresh(game);
    }

    @ButtonHandler("fowSetupMapString~MDL")
    public static void openMapStringModal(ButtonInteractionEvent event, Game game) {
        event.replyModal(AddTileListService.buildMapStringModal(game, "addMapString"))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("fowSetupMapJson~MDL")
    public static void openMapJsonModal(ButtonInteractionEvent event) {
        TextInput url = TextInput.create("url", TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder("http://your.url/fow123_map.json")
                .build();
        Modal modal = Modal.create("importMapFromJSON", "Import map (WIP)")
                .addComponents(Label.of("URL", url))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    // ---------------------------------------------------------------------------------------
    // Step 4: Fog of War type
    // ---------------------------------------------------------------------------------------

    private static void renderFogType(Game game, FowSetupWizardState state, StringBuilder sb, List<Button> buttons) {
        sb.append("Pick a fog preset, then fine-tune individual options if you want.\n\n")
                .append("- **Normal**: defaults, no extra visibility relaxations.\n")
                .append("- **Fog Lite**: easier for new GMs/players - novas always visible, unexplored map, ")
                .append("explore decks and stats-from-HS-only restrictions relaxed.\n")
                .append("- **")
                .append(FOWOption.FOW_PLUS.getTitle())
                .append("**: see \"Fine-tune Options\" below for details.\n\n")
                .append("Currently active: FoW+ = ")
                .append(FOWPlusService.isActive(game))
                .append("\n");

        buttons.add(Buttons.green("fowSetupFogType_normal", "Normal"));
        buttons.add(Buttons.blue("fowSetupFogType_lite", "Fog Lite"));
        buttons.add(Buttons.blue("fowSetupFogType_plus", "Fog+"));
        buttons.add(Buttons.gray("fowSetupFogOptions", "Fine-tune Options"));
    }

    @ButtonHandler("fowSetupFogType_")
    public static void chooseFogType(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!requireGM(event, game)) return;
        String type = buttonID.replace("fowSetupFogType_", "");
        switch (type) {
            case "normal" -> {
                FOWPlusService.setActive(game, false);
                game.setFowOption(FOWOption.BRIGHT_NOVAS, false);
                game.setFowOption(FOWOption.HIDE_MAP, false);
                game.setFowOption(FOWOption.HIDE_EXPLORES, false);
                game.setFowOption(FOWOption.STATS_FROM_HS_ONLY, false);
            }
            case "lite" -> {
                FOWPlusService.setActive(game, false);
                game.setFowOption(FOWOption.BRIGHT_NOVAS, true);
                game.setFowOption(FOWOption.HIDE_MAP, false);
                game.setFowOption(FOWOption.HIDE_EXPLORES, false);
                game.setFowOption(FOWOption.STATS_FROM_HS_ONLY, false);
            }
            case "plus" -> FOWPlusService.setActive(game, true);
            default -> {
                return;
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Fog type set to **" + type + "**.");
        openOrRefresh(game);
    }

    @ButtonHandler("fowSetupFogOptions")
    public static void openFogOptions(ButtonInteractionEvent event, Game game) {
        if (!requireGM(event, game)) return;
        FOWOptionService.offerFOWOptionButtons(game);
    }

    // ---------------------------------------------------------------------------------------
    // Step 5: Decks
    // ---------------------------------------------------------------------------------------

    private static void renderDecks(Game game, FowSetupWizardState state, StringBuilder sb, List<Button> buttons) {
        sb.append("Pick decks with `/game set_deck`, or import a config from the companion Deck-editor ")
                .append("tool with `/special2 import_deck_config`. See the info thread for details.\n");
    }

    // ---------------------------------------------------------------------------------------
    // Step 6: Neutral player
    // ---------------------------------------------------------------------------------------

    private static void renderNeutralPlayer(
            Game game, FowSetupWizardState state, StringBuilder sb, List<Button> buttons) {
        sb.append("Give the neutral player a color, then add their units (see info thread below).\n");
        buttons.add(Buttons.green("fowSetupNeutralPlayer", "Setup Neutral Player"));
    }

    @ButtonHandler("fowSetupNeutralPlayer")
    public static void setupNeutralPlayer(ButtonInteractionEvent event, Game game) {
        if (!requireGM(event, game)) return;
        String color = SetupNeutralPlayer.pickNeutralColor(game);
        game.setupNeutralPlayer(color);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), "Neutral player has been set as **" + color + "**.");
        openOrRefresh(game);
    }

    // ---------------------------------------------------------------------------------------
    // Step 7: Deal secret objectives & start
    // ---------------------------------------------------------------------------------------

    private static void renderDealSO(Game game, FowSetupWizardState state, StringBuilder sb, List<Button> buttons) {
        sb.append("Last step: deal secret objectives, then start the game.\n");
        buttons.add(Buttons.green("fowSetupDealSO", "Deal 3 Secret Objectives to All"));
        buttons.add(Buttons.green("fowSetupStartGame", "Start Game"));
    }

    @ButtonHandler("fowSetupDealSO")
    public static void dealSO(ButtonInteractionEvent event, Game game) {
        if (!requireGM(event, game)) return;
        DrawSecretService.dealSOToAll(event, 3, game);
    }

    @ButtonHandler("fowSetupStartGame")
    public static void startGame(ButtonInteractionEvent event, Game game) {
        if (!requireGM(event, game)) return;
        StartPhaseService.startPhase(event, game, "strategy");
        FowSetupWizardState state = loadState(game);
        state.setStep(FowSetupStep.DONE);
        saveState(game, state);
        openOrRefresh(game);
    }

    private static void renderDone(Game game, FowSetupWizardState state, StringBuilder sb, List<Button> buttons) {
        sb.append("Setup wizard complete. Use **Previous Step** if you need to revisit anything.\n");
    }
}
