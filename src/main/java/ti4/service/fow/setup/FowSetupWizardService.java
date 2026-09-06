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
import ti4.discord.interactions.commands.game.WeirdGameSetup;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
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

    /** The wizard's own state has no use once the game it set up is over - called from
     * {@code EndGameService.gameEndStuff} so it doesn't linger in the save file forever. Other
     * stored-value keys this wizard writes to (e.g. "bannedFactions") are shared with non-wizard
     * features and aren't this method's to clear. */
    public static void clearStateOnGameEnd(Game game) {
        game.removeStoredValue(STORE_KEY);
    }

    public static void saveState(Game game, FowSetupWizardState state) {
        game.setStoredValue(STORE_KEY, JsonMapperManager.basic().writeValueAsString(state));
    }

    /** Every call to `/fow setup` (or the Refresh button) deletes the old panel and reposts the current step. */
    public static void openOrRefresh(Game game) {
        FowSetupWizardState state = loadState(game);
        TextChannel gmChannel = GMService.getGMChannel(game);
        postIntroOnce(game, gmChannel, state);
        if (state.getPanelMessageId() != null) {
            gmChannel.deleteMessageById(state.getPanelMessageId()).queue(Consumers.nop(), BotLogger::catchRestError);
        }
        renderStep(game, gmChannel, state);
    }

    /**
     * Numbered list of the wizard's steps. DONE is a landing page rather than a step, so it's excluded
     * here and from the panel's "Step x/y" count. Shared with the blurb posted next to the wizard button
     * at game creation ({@code CreateFoWGameService}) so the two can't drift apart.
     */
    public static String stepOverview() {
        StringBuilder sb = new StringBuilder();
        for (FowSetupStep step : FowSetupStep.values()) {
            if (step == FowSetupStep.DONE) continue;
            sb.append(step.ordinal() + 1).append(". ").append(stepTitle(step)).append('\n');
        }
        return sb.toString();
    }

    /** Posted once ever per game, the first time the wizard opens - a step list plus prep advice. */
    private static void postIntroOnce(Game game, TextChannel gmChannel, FowSetupWizardState state) {
        if (state.isIntroShown()) return;
        String intro = "## Welcome to the FoW Game Setup Wizard\n"
                + "This walks you through setting up a Fog of War game step by step, entirely in this GM "
                + "room. Before you start, it helps to have a plan - roughly what **map** you want, how "
                + "many **players** and who they are, what **factions**/homebrew you're using, and your "
                + "**scoring** target, etc. You can revisit any step with **Previous Step**/**Next Step**, "
                + "so nothing here is final until you hit Start Game.\n\n### Steps\n"
                + stepOverview();
        MessageHelper.sendMessageToChannel(gmChannel, intro);
        state.setIntroShown(true);
        saveState(game, state);
    }

    public static boolean requireGM(GenericInteractionCreateEvent event, Game game) {
        // FoWHelper.isGameMaster is the codebase's existing GM predicate - same getPlayersWithGMRole check,
        // so don't re-derive it here.
        if (!FoWHelper.isGameMaster(event.getUser().getId(), game)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Only a GM can use this wizard.");
            return false;
        }
        return true;
    }

    /** Posted in the GM room right after a FoW game is created (both `/fow create_fow_game_button` and the
     * `/bothelper` variant funnel through {@code CreateFoWGameService.executeCreateFoWGame}), so a GM never
     * has to know the `/fow setup` slash command exists to find the wizard. */
    @ButtonHandler("fowSetupOpenWizard")
    public static void openWizardButton(ButtonInteractionEvent event, Game game) {
        if (!requireGM(event, game)) return;
        openOrRefresh(game);
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
        // DONE is a landing page rather than a step, so it's excluded from the count here and from the
        // intro's step list - otherwise the header would read "Step 10/10" for a step that does nothing.
        int totalSteps = FowSetupStep.values().length - 1;
        StringBuilder sb = new StringBuilder(
                state.getStep() == FowSetupStep.DONE
                        ? "# FoW Game Setup - " + stepTitle(FowSetupStep.DONE) + "\n\n"
                        : "# FoW Game Setup - Step " + (state.getStep().ordinal() + 1) + "/" + totalSteps + ": "
                                + stepTitle(state.getStep()) + "\n\n");
        List<Button> buttons = new ArrayList<>();
        switch (state.getStep()) {
            case GAME_TYPE -> renderGameType(game, state, sb, buttons);
            case GAME_OPTIONS -> FowSetupGameOptionsService.render(game, state, sb, buttons);
            case MAP_LOAD -> renderMapLoad(game, state, sb, buttons);
            case PLAYER_ROLES -> FowSetupPlayerRolesService.render(game, state, sb, buttons);
            case FACTIONS -> FowSetupFactionService.render(game, state, sb, buttons);
            case TABLE_ORDER -> FowSetupTableOrderService.render(game, state, sb, buttons);
            case FOG_TYPE -> renderFogType(game, state, sb, buttons);
            case DECKS -> FowSetupDecksService.render(game, state, sb, buttons);
            case NEUTRAL_PLAYER -> FowSetupNeutralPlayerService.render(game, state, sb, buttons);
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
            case GAME_OPTIONS -> "Game Options";
            case MAP_LOAD -> "Load the Map";
            case PLAYER_ROLES -> "Confirm Players";
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
        if (stepInfo(state.getStep()) != null) {
            navButtons.add(Buttons.gray("fowSetupShowInfo", "ℹ Show Info Thread"));
        }
        return navButtons;
    }

    /**
     * Info threads only auto-post the first time a GM reaches a step ({@code infoThreadsPosted} never
     * clears), so a GM who passed a step before its info text was written or changed would otherwise
     * never see it. The "Show Info Thread" nav button (always available) force-reposts on demand -
     * {@code getThreadInChannel} finds the existing thread by name rather than duplicating it.
     */
    @ButtonHandler("fowSetupShowInfo")
    public static void showInfoThread(ButtonInteractionEvent event, Game game) {
        if (!requireGM(event, game)) return;
        FowSetupWizardState state = loadState(game);
        String info = stepInfo(state.getStep());
        if (info == null) return;
        postInfoToThread(game, state.getStep(), info);
        state.getInfoThreadsPosted().add(state.getStep());
        saveState(game, state);
    }

    private static void postStepInfoThreadOnce(Game game, FowSetupWizardState state) {
        String info = stepInfo(state.getStep());
        if (info == null || state.getInfoThreadsPosted().contains(state.getStep())) return;
        postInfoToThread(game, state.getStep(), info);
        state.getInfoThreadsPosted().add(state.getStep());
        saveState(game, state);
    }

    private static void postInfoToThread(Game game, FowSetupStep step, String info) {
        String pinged = GMService.gmPing(game);
        String message = (pinged.isEmpty() ? "" : pinged + "\n") + info;
        ThreadGetter.getThreadInChannel(
                GMService.getGMChannel(game),
                game.getName() + "-setup-" + step.name().toLowerCase(),
                true,
                false,
                thread -> MessageHelper.sendMessageToChannel(thread, message));
    }

    private static String stepInfo(FowSetupStep step) {
        return switch (step) {
            case GAME_TYPE -> """
                ### Picking an expansion / game mode
                **New PoK / Old PoK** - which card art/errata revision to use, same choice as the non-fog \
                setup flow. **Thunder's Edge + New PoK** does that plus the full Thunder's Edge homebrew \
                setup (adds the TE action-card deck, restores the Silver Flame/Quantumcore relics) - more \
                complete than the identically-named button in non-fog `/game setup`, which only flips the \
                flag. **Start Base Game Only Setup** switches to base-game content only (no PoK) - unlike \
                the non-fog flow this only flips the flag, it does **not** continue into a Milty draft \
                settings menu, since this wizard doesn't run Milty at all.
                **Supported Homebrew** is the older flat button list - prefer the categorized toggles on the \
                Game Options step instead, this one's kept for parity with the non-fog flow.
                **Galactic Events** opens the same Thunder's Edge event-toggle menu as the non-fog flow.
                **Start Twilight's Fall Game** and **Start Franken Setup** hand off to those systems' own \
                existing draft/setup flows, which are not FoW-wizard-integrated - anything they post follows \
                their own channel-routing rules, not this wizard's GM-room-only guarantee.
                **Set Scenario Note** is a free-text field for your own reference only (house rules, scenario \
                name, etc.) - it isn't wired into any game logic.
                """;
            case GAME_OPTIONS -> """
                ### Notes on these options
                - **Absol Relics/Agendas** and **Absol Techs/Mechs** both flip the same underlying "Absol \
                Mode" flag but change different decks - click both if you want the full Absol set.
                - **No Supports** only removes Support for the Throne from players already in the game at \
                the moment you click it. Players added later still need it removed manually.
                - **Remove All Homebrews** turns the flags back off but does **not** undo deck/VP/objective \
                changes already made - check those manually afterward.
                - **Fractured TIGL** is the only TIGL variant that can combine with FoW mode - standard TIGL \
                is unconditionally blocked in FoW games, so this button uses the full Fractured TIGL setup \
                (tags the game, posts the TIGL rules text, snapshots player ranks) rather than a plain flag.
                - **Alliance Mode** just flips the flag (and turns off TIGL, since they're mutually \
                exclusive) - it does not pair anyone up. Once you have factions/colors assigned, go to the \
                Factions step and use "Pair Alliance Members" to actually team players together (this bumps \
                VP to 14 if still default, shares eligible commanders, and removes each paired player's \
                Alliance promissory note - same as `/player add_alliance_member`).
                - The scoring presets (4/4/4, 4/5/6) and the custom scoring modal both just set VP, max \
                secret objectives per player, and peekable Stage 1/2 objective counts - use whichever is \
                more convenient, or run the modal after a preset to fine-tune one value.
                """;
            case FOG_TYPE -> """
                ### Choosing a preset vs. fine-tuning
                The three preset buttons (Normal/Fog Lite/Fog+) set a curated bundle of individual FoW \
                options in one click - "Fine-tune Options" opens `/fow fow_options`, the full per-option \
                menu, so you can start from a preset and then flip specific options afterward instead of \
                configuring everything from scratch.
                Once you've picked something, use `/fow show_game_as` (targeting one of the real players) \
                to sanity-check what that player would actually see on the map/board - it's the fastest way \
                to catch a fog setting that's more (or less) restrictive than you intended before players \
                start looking at things themselves.
                """;
            case MAP_LOAD -> """
                ### Load the map before you place anyone
                Both import paths wipe the board and rebuild it: the map-string import calls \
                `clearTileMap`, and the JSON import calls `removeAllTiles` (and also clears adjacency \
                overrides, border anomalies, custom hyperlanes and lore). Anything already on the map goes, \
                **including the home-system tiles that placed your players**. Their recorded position \
                survives on the player, so re-importing a map mid-setup leaves them pointing at a tile that \
                no longer exists - the Factions step detects this and lists them as needing a position \
                again, but it's far less work to just load the map first.

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
            case PLAYER_ROLES -> """
                ### Why is the GM in this list at all?
                Creating a FoW game adds the GM as a full player entry alongside everyone else - their \
                Discord GM role is the only thing that marks them different internally. This step just \
                tells the wizard's own Factions/Table Order steps who to skip; it doesn't touch the \
                underlying player list or affect anything outside this wizard. If your GM is also playing \
                a faction, mark them "Playing" here like anyone else.
                """;
            case FACTIONS -> """
                ### Faction assignment options
                **Manual** - use the buttons on the panel above.
                **Ban Factions** - toggles factions in/out of the shared Franken-eligible pool (same pool \
                used by Manual, Deal Factions, and `/franken`). Nothing is banned by default - if you want a \
                faction out of a game, ban it here.
                **Gate factions** (Ghosts of Creuss variants, Crimson) work in fog: they have two home-system \
                tiles, so after you place their gate the wizard asks where their actual home system goes, \
                rather than dropping it on an arbitrary map corner.
                **Deal Factions (Mini Draft)** - set how many factions to offer each unassigned player, and \
                whether the same faction can appear on more than one player's list. Each player privately \
                picks one from their own dealt list in their own channel (no bag-passing - this is a \
                lightweight one-shot deal, not a full Franken draft). Once everyone has a faction, come \
                back here and use "Assign Position" / "Randomize All Positions" to place them.
                **CSV roster upload** - not built yet. The idea: GM uploads a comma-separated list of \
                possible factions per player (`player1a, player1b, player1c; player2a, ...`) generated by \
                a companion website, and the wizard deals accordingly. Documented here for later.

                ### Assign positions LAST if you're running a draft
                In a non-fog game `/franken build_map` places everyone automatically from a map template and \
                the tiles they drafted. Fog games have no template and don't draft tiles, so **you placing \
                players here is the only thing that ever puts them on the map** - and starting (or \
                restarting) a Franken / Twilight's Fall draft parks every player back at a temporary off-map \
                anchor, silently discarding positions you'd already assigned. So: build the map, run the \
                draft, let it finish, *then* assign positions. Players sitting on a temp anchor are shown \
                above as `@ 50x (temp, not on map)`, and stay eligible for "Assign Position" until they're \
                really placed.
                **Pair Alliance Members** (only shown if Alliance Mode is on, from the Game Options step) - \
                pick two players who already have a faction and color to team them up. This is the same \
                logic as `/player add_alliance_member`: it bumps VP to 14 if still default, shares eligible \
                commanders between the pair, and removes each player's Alliance promissory note. Note: \
                re-pairing an already-paired player may not cleanly replace their old partner in the \
                underlying data (a pre-existing quirk in that command, not specific to this wizard) - if \
                you need to change a pairing, ask a dev rather than assuming a simple re-pair fixes it.
                """;
            case TABLE_ORDER -> """
                ### Before using the dice-roll button
                Each player gets their own "Roll for Table Order" button posted to their own private \
                channel, so nobody sees anyone else's roll - only you (and the GM, via this panel) see \
                the result. If a player's private channel isn't linked, they won't get a roll button at \
                all - it silently falls back to posting in this GM room instead of their channel, and any \
                button they click anywhere in this FoW game will fail with "Private channels are not set \
                up for this game", even if the channel looks completely normal in Discord (the bot checks \
                its own stored channel ID, not whether a same-named channel exists). Run \
                `/fow check_channels` first (in the main channel) to see who's missing one, use its \
                "Create Channel for X" buttons to fix it, then re-open Configure Dice to resend the roll \
                buttons.
                """;
            case DECKS -> """
                ### Removing individual cards from a deck already in the game
                The buttons above pick which whole deck is in play for each slot; these commands pull one \
                (or more) specific card(s) back out of a deck that's already set:
                - `/custom remove_ac_from_game` - remove an Action Card.
                - `/custom remove_agenda_from_game` - remove an Agenda.
                - `/custom remove_relic_from_game` - remove a Relic.
                - `/custom remove_so_from_game` - remove a Secret Objective.
                - `/custom remove_po_from_game` - remove a Public Objective (Stage 1 or 2).
                - `/custom remove_sc_from_game` - remove a Strategy Card by its initiative number (not a \
                deck card, but grouped here since it's the same family of command).
                - `/explore remove` - remove one or more Exploration cards (comma-separated ids).
                - **Technology** and **Event** decks have no per-card removal command - technology decks \
                are rebuilt on demand and events aren't removable individually.

                ### Importing from the Deck-editor tool
                The "Import Deck-editor Config (URL)" button above applies a deck-set config exported from \
                the companion Ti-Async-Deckcard-tool Deck-editor: \
                https://stabar-ti.github.io/Ti-Async-Deckcard-tool/ - it can set multiple deck slots at \
                once and apply per-card exclusions in one go (same underlying command as \
                `/special2 import_deck_config`, which also accepts a `file` attachment instead of a URL).
                """;
            case NEUTRAL_PLAYER -> """
                ### Adding units for the neutral player
                Once the neutral player has a color (button above), add their units the normal way, \
                targeting that color, e.g. `/tokens add_token` or the unit-add commands with the neutral \
                color selected.
                """;
            case DEAL_SO -> """
                ### Last checks before starting
                "Deal 2 Secret Objectives to All" runs the same logic as `/cardsso deal_so_to_all` for every \
                real player at once - re-clicking it won't re-deal players who already have secrets.
                "Start Game" runs `StartPhaseService`'s normal phase-start logic (same as `/game start_phase`), \
                moving the game into the strategy phase - players can act immediately after this. This is \
                your last chance to jump back (Previous Step) and fix anything before that happens.
                """;
            case DONE -> """
                ### What "Done" means here
                This step is just a landing page - it doesn't do anything itself. Use **Previous Step** to \
                revisit any earlier step at any time, even after starting the game (the wizard doesn't lock \
                once you reach here). **Close Wizard** deletes this panel and resets which step the wizard \
                *opens to* next time (back to Game Type) - it does not undo or clear any answers you've \
                already given (factions, map, dice rolls, toggles, etc.), those all stay exactly as set.
                """;
            default -> null;
        };
    }

    // ---------------------------------------------------------------------------------------
    // GAME_TYPE step: game type & scenario
    // ---------------------------------------------------------------------------------------

    private static void renderGameType(Game game, FowSetupWizardState state, StringBuilder sb, List<Button> buttons) {
        sb.append("These mirror the normal (non-fog) game setup buttons - clicking one replies here in the GM ")
                .append("room, not the public channel. Note the Twilight's Fall and Franken buttons then hand ")
                .append("off to their own draft systems, which route their later messages their own way - see ")
                .append("the info thread.\n\n")
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
        buttons.add(Buttons.blue("fowSetupChooseExpTE", "Thunder's Edge + New PoK"));

        sb.append("**Alternate game modes**\n");
        buttons.add(Buttons.green("getHomebrewButtons", "Supported Homebrew"));
        buttons.add(Buttons.green("offerTEOptionButtons", "Galactic Events"));
        buttons.add(Buttons.green("startTFGame", "Start Twilight's Fall Game"));
        buttons.add(Buttons.green("frankenSetup", "Start Franken Setup"));

        buttons.add(Buttons.gray("fowSetupScenario~MDL", "Set Scenario Note"));
    }

    /**
     * "New PoK"/"Old PoK" reuse the shared {@code chooseExp_} handler (also used by non-fog `/game
     * setup`), which just flips {@code thundersEdge}/{@code useOldPok}. The TE option gets its own
     * wizard-specific button instead of reusing {@code chooseExp_te}, so it can also call
     * {@code WeirdGameSetup.applyThundersEdgeMode} - the fuller path that adds the Thunder's Edge
     * action-card deck and restores the Silver Flame/Quantumcore relics, which the shared handler
     * doesn't do. That method already replies via {@code event.getMessageChannel()} (GM-room-safe).
     */
    @ButtonHandler("fowSetupChooseExpTE")
    public static void chooseExpThundersEdge(ButtonInteractionEvent event, Game game) {
        if (!requireGM(event, game)) return;
        game.removeStoredValue("useOldPok");
        WeirdGameSetup.applyThundersEdgeMode(event, game, true);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), "Set game to use Thunder's Edge + New PoK components.");
        openOrRefresh(game);
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
        if (!requireGM(event, game)) return;
        FowSetupWizardState state = loadState(game);
        state.setScenarioNote(event.getValue("scenarioNote").getAsString());
        saveState(game, state);
        openOrRefresh(game);
    }

    // ---------------------------------------------------------------------------------------
    // MAP_LOAD step: load the map
    // ---------------------------------------------------------------------------------------

    private static void renderMapLoad(Game game, FowSetupWizardState state, StringBuilder sb, List<Button> buttons) {
        sb.append("Choose how to load the map. See the info thread below for follow-up commands and ")
                .append("troubleshooting once tiles are in.\n\n")
                .append("⚠ Both import options **replace the entire map**, so load the map before placing ")
                .append("players - anyone already placed loses the home-system tile that put them there and ")
                .append("will need their position assigned again.\n\n")
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
    // FOG_TYPE step: fog of war type
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
    // DEAL_SO step: deal secret objectives & start
    // ---------------------------------------------------------------------------------------

    private static void renderDealSO(Game game, FowSetupWizardState state, StringBuilder sb, List<Button> buttons) {
        sb.append("Last step: deal secret objectives, then start the game.\n");
        buttons.add(Buttons.green("fowSetupDealSO", "Deal 2 Secret Objectives to All"));
        buttons.add(Buttons.green("fowSetupStartGame", "Start Game"));
    }

    @ButtonHandler("fowSetupDealSO")
    public static void dealSO(ButtonInteractionEvent event, Game game) {
        if (!requireGM(event, game)) return;
        DrawSecretService.dealSOToAll(event, 2, game);
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
        sb.append("Setup wizard complete. Use **Previous Step** if you need to revisit anything, or ")
                .append("**Close Wizard** to remove this panel and archive its info threads.\n");
        buttons.add(Buttons.red("fowSetupCloseWizard", "Close Wizard"));
    }

    @ButtonHandler("fowSetupCloseWizard")
    public static void closeWizard(ButtonInteractionEvent event, Game game) {
        if (!requireGM(event, game)) return;
        // Do this before loading the state below - it saves a state of its own, so reading first would
        // hand us a copy whose (now stale) ban-page ids we'd write straight back.
        FowSetupFactionService.clearBanMenuMessages(GMService.getGMChannel(game), game);
        FowSetupWizardState state = loadState(game);
        archiveInfoThreads(game, state);
        state.setPanelMessageId(null);
        // Closing means "fully done" - the next /fow setup starts a fresh review from step 1. This only
        // resets which step is displayed; every other saved answer (factions, map, dice rolls, etc.) stays.
        state.setStep(FowSetupStep.GAME_TYPE);
        saveState(game, state);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    /**
     * Nothing else ever cleans up the per-step info threads this wizard creates - at game end they get
     * swept up for free (deleting a Discord channel cascade-deletes its threads), but setup usually
     * finishes long before the game does, so without this they'd sit in the GM room's thread list for
     * the entire rest of the game. Archive (not delete) so the content is still there if anyone needs to
     * check back, matching the same archive-don't-delete pattern EndGameService already uses for threads
     * in channels it keeps.
     */
    private static void archiveInfoThreads(Game game, FowSetupWizardState state) {
        TextChannel gmChannel = GMService.getGMChannel(game);
        for (FowSetupStep step : state.getInfoThreadsPosted()) {
            ThreadGetter.getThreadInChannel(
                    gmChannel,
                    game.getName() + "-setup-" + step.name().toLowerCase(),
                    false,
                    false,
                    thread -> thread.getManager().setArchived(true).queue(Consumers.nop(), BotLogger::catchRestError));
        }
    }
}
