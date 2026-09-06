package ti4.service.fow.setup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.player.AddAllianceMember;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.discord.interactions.routing.SelectionHandler;
import ti4.draft.FrankenDraft;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AliasHandler;
import ti4.helpers.PatternHelper;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ColorModel;
import ti4.model.FactionModel;
import ti4.service.ShowGameService;
import ti4.service.draft.PlayerSetupService;
import ti4.service.draft.PlayerSetupState;
import ti4.service.emoji.FactionEmojis;
import ti4.service.fow.GMService;
import ti4.service.fow.UserOverridenGenericInteractionCreateEvent;
import ti4.service.game.GameColorsService;

/** FACTIONS step of the FoW setup wizard: faction assignment and home-position assignment. */
final class FowSetupFactionService {

    private FowSetupFactionService() {}

    private static final String BANNED_FACTIONS_KEY = "bannedFactions";

    /**
     * Human players only, in a state where they still need faction/position setup. Excludes dummies -
     * the neutral (Dicecord) player is a dummy with faction "neutral" and no position, so without this
     * it qualifies as "has a faction but needs a position" and shows up in the assign/randomize flows.
     * Reachable whenever a GM sets up the neutral player and then steps back to Factions. Also excludes
     * whoever the PLAYER_ROLES step marked GM/observer (see {@link FowSetupPlayerRolesService}).
     */
    private static List<Player> setupCandidates(Game game, FowSetupWizardState state) {
        return game.getPlayers().values().stream()
                .filter(player -> FowSetupPlayerRolesService.isPlayerCandidate(player, state))
                .toList();
    }

    static void render(Game game, FowSetupWizardState state, StringBuilder sb, List<Button> buttons) {
        sb.append("Assign each player a faction manually, or deal a mini faction draft (ban factions first if ")
                .append("you want), then assign home positions.\n\n");
        // Starting a Franken/Twilight's Fall draft re-parks every player at a temporary off-map anchor, so
        // any position assigned before the draft finishes is thrown away. Non-fog games get placed
        // automatically by `/franken build_map` from a map template; FoW has no template and drafts no
        // tiles, so the GM placing players here is the only thing that ever puts them on the map.
        if (mapIsEmpty(game)) {
            sb.append("⚠ No map yet - pick factions and run drafts freely, but positions need the map built ")
                    .append("first (**Load the Map** step). Faction picks are held here and applied to the ")
                    .append("player (along with their colour) when you place them.\n\n");
        }
        if (game.getActiveBagDraft() != null) {
            sb.append("⚠ A draft is currently running. Assign positions **after** it finishes - starting or ")
                    .append("restarting a draft resets every player to a temporary off-map position.\n\n");
        }
        for (Player player : setupCandidates(game, state)) {
            String effective = effectiveFaction(state, player);
            String pos = effectivePosition(player);
            List<String> dealt = state.getDealtFactionChoices().get(player.getUserID());
            sb.append("> ")
                    .append(player.getUserName())
                    .append(": ")
                    .append(StringUtils.isBlank(effective) ? "_no faction yet_" : effective)
                    .append(
                            pos == null
                                    ? ""
                                    : " @ " + pos + (isPlacedOnMap(game, player) ? "" : " _(temp, not on map)_"))
                    .append(
                            dealt == null || StringUtils.isNotBlank(effective)
                                    ? ""
                                    : " _(picking from " + dealt.size() + " dealt)_")
                    .append('\n');
        }
        buttons.add(Buttons.blue("fowSetupFactionManual", "Assign Faction Manually"));
        buttons.add(Buttons.blue("fowSetupDealFactions~MDL", "Deal Factions (Mini Draft)"));
        buttons.add(Buttons.gray("fowSetupBanFactionsMenu", "Ban Factions"));
        buttons.add(Buttons.green("fowSetupPositionsManual", "Assign Position (one player)"));
        buttons.add(Buttons.green("fowSetupPositionsRandomize", "Randomize All Positions"));
        buttons.add(Buttons.gray("fowSetupShowBoards", "Show All Player Boards"));

        // Alliance Mode is set on the Game Options step - pairing needs faction+color, so it only
        // makes sense here once players are real. Hidden entirely for non-Alliance games.
        if (game.isAllianceMode()) {
            sb.append("\n### Alliance pairs\n");
            List<Player> realPlayers = game.getRealPlayers();
            boolean anyPairs = false;
            for (int i = 0; i < realPlayers.size(); i++) {
                for (int j = i + 1; j < realPlayers.size(); j++) {
                    Player a = realPlayers.get(i);
                    Player b = realPlayers.get(j);
                    if (a.isPlayerMemberOfAlliance(b)) {
                        sb.append("> ")
                                .append(a.getUserName())
                                .append(" <-> ")
                                .append(b.getUserName())
                                .append('\n');
                        anyPairs = true;
                    }
                }
            }
            if (!anyPairs) {
                sb.append("> _no pairs yet_\n");
            }
            buttons.add(Buttons.blue("fowSetupAlliancePick1", "Pair Alliance Members"));
        }
    }

    // --- Alliance Mode pairing: pick player 1, then player 2, then apply ---

    @ButtonHandler("fowSetupAlliancePick1")
    static void pickAlliancePlayer1(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        List<Button> playerButtons = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            playerButtons.add(Buttons.gray("fowSetupAlliancePick2_" + player.getUserID(), player.getUserName()));
        }
        if (playerButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "No players have a faction and color assigned yet.");
            return;
        }
        playerButtons.add(Buttons.CANCEL);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), "Pick the first player of the pair:", playerButtons);
    }

    @ButtonHandler("fowSetupAlliancePick2_")
    static void pickAlliancePlayer2(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String player1Id = buttonID.replace("fowSetupAlliancePick2_", "");
        Player player1 = game.getPlayer(player1Id);
        if (player1 == null) return;

        List<Button> playerButtons = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            if (player.getUserID().equals(player1Id)) continue;
            playerButtons.add(Buttons.gray(
                    "fowSetupAllianceConfirm_" + player1Id + "_" + player.getUserID(), player.getUserName()));
        }
        if (playerButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No other players to pair with.");
            return;
        }
        playerButtons.add(Buttons.CANCEL);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), "Pair " + player1.getUserName() + " with:", playerButtons);
    }

    @ButtonHandler("fowSetupAllianceConfirm_")
    static void confirmAlliancePair(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        // Discord user IDs are purely numeric snowflakes, so a single "_" unambiguously separates the two.
        String[] parts = buttonID.replace("fowSetupAllianceConfirm_", "").split("_", 2);
        Player player1 = game.getPlayer(parts[0]);
        Player player2 = game.getPlayer(parts[1]);
        if (player1 == null || player2 == null) return;
        AddAllianceMember.makeAlliancePartners(player1, player2, event, game);
        FowSetupWizardService.openOrRefresh(game);
    }

    // --- Ban factions from the Franken-eligible pool (shared by manual entry and the mini draft) ---

    @ButtonHandler("fowSetupBanFactionsMenu")
    static void banFactionsMenu(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        postBanFactionsMenu(event, game, null);
    }

    @ButtonHandler("fowSetupBanFactionToggle_")
    static void toggleBanFaction(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String alias = buttonID.replace("fowSetupBanFactionToggle_", "");
        List<String> banned = new ArrayList<>(bannedFactionAliases(game));
        boolean nowBanned = !banned.remove(alias);
        if (nowBanned) {
            banned.add(alias);
        }
        game.setStoredValue(BANNED_FACTIONS_KEY, String.join("finSep", banned));
        String leadNote = Mapper.getFaction(alias).getFactionName() + (nowBanned ? " banned." : " unbanned.");
        postBanFactionsMenu(event, game, leadNote);
    }

    @ButtonHandler("fowSetupBanFactionsDone")
    static void closeBanFactionsMenu(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        clearBanMenuMessages(event.getMessageChannel(), game);
    }

    /**
     * Deletes every page of the ban list. The Franken-legal pool doesn't fit one message, so a toggle has to
     * redraw all the pages (editing one would leave the others showing stale labels) and Done has to remove all
     * of them - the generic Cancel button only ever deleted the page it happened to sit on, leaving the rest
     * behind. The page ids are tracked on the wizard state because they outlive the interaction that made them,
     * which also lets closing the wizard sweep up a list the GM walked away from.
     */
    static void clearBanMenuMessages(MessageChannel channel, Game game) {
        if (channel == null) return;
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        for (Long messageId : state.getBanMenuMessageIds()) {
            channel.deleteMessageById(messageId).queue(Consumers.nop(), BotLogger::catchRestError);
        }
        state.getBanMenuMessageIds().clear();
        FowSetupWizardService.saveState(game, state);
    }

    /** {@code leadNote} folds the ban/unban confirmation into the redrawn list instead of sending it separately. */
    private static void postBanFactionsMenu(ButtonInteractionEvent event, Game game, String leadNote) {
        clearBanMenuMessages(event.getMessageChannel(), game);
        List<String> banned = bannedFactionAliases(game);
        List<Button> factionButtons = new ArrayList<>();
        for (FactionModel faction : FrankenDraft.getAllFrankenLegalFactions(game)) {
            boolean isBanned = banned.contains(faction.getAlias());
            factionButtons.add(
                    isBanned
                            ? Buttons.red(
                                    "fowSetupBanFactionToggle_" + faction.getAlias(),
                                    "Unban " + faction.getFactionName())
                            : Buttons.gray(
                                    "fowSetupBanFactionToggle_" + faction.getAlias(),
                                    "Ban " + faction.getFactionName()));
        }
        factionButtons.add(Buttons.gray("fowSetupBanFactionsDone", "Done Banning"));
        String message =
                (leadNote == null ? "" : leadNote + "\n\n") + "Click a faction to ban/unban it from the Franken pool:";
        MessageHelper.splitAndSentWithAction(
                message, event.getMessageChannel(), factionButtons, msg -> trackBanMenuMessage(game, msg));
    }

    /** Runs on JDA's callback thread once per posted page, so the read-modify-write has to be atomic. */
    private static synchronized void trackBanMenuMessage(Game game, Message message) {
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        state.getBanMenuMessageIds().add(message.getIdLong());
        FowSetupWizardService.saveState(game, state);
    }

    private static List<String> bannedFactionAliases(Game game) {
        String stored = game.getStoredValue(BANNED_FACTIONS_KEY);
        if (StringUtils.isBlank(stored)) return new ArrayList<>();
        List<String> aliases = new ArrayList<>(Arrays.asList(PatternHelper.FIN_SEPERATOR_PATTERN.split(stored)));
        aliases.removeIf(StringUtils::isBlank);
        return aliases;
    }

    // --- Mini faction draft: GM sets a per-player count, each player privately picks from their own dealt list ---

    @ButtonHandler("fowSetupDealFactions~MDL")
    static void openDealFactionsCountModal(ButtonInteractionEvent event) {
        TextInput count = TextInput.create("count", TextInputStyle.SHORT)
                .setPlaceholder("e.g. 3")
                .setRequired(true)
                .build();
        Modal modal = Modal.create("fowSetupDealFactionsCountResolve", "Deal Factions - How Many Each?")
                .addComponents(Label.of("Factions offered per player", count))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler("fowSetupDealFactionsCountResolve")
    static void resolveDealFactionsCountModal(ModalInteractionEvent event) {
        int count;
        try {
            count = Integer.parseInt(event.getValue("count").getAsString().trim());
        } catch (NumberFormatException e) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Count must be a whole number.");
            return;
        }
        if (count < 1) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Count must be at least 1.");
            return;
        }
        List<Button> buttons = List.of(
                Buttons.green("fowSetupDealFactionsGo_nodup_" + count, "Deal (No Duplicates Across Players)"),
                Buttons.blue("fowSetupDealFactionsGo_dup_" + count, "Deal (Duplicates OK)"),
                Buttons.CANCEL);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Can the same faction show up on more than one player's dealt list?",
                buttons);
    }

    @ButtonHandler("fowSetupDealFactionsGo_")
    static void dealFactions(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String[] parts = buttonID.replace("fowSetupDealFactionsGo_", "").split("_", 2);
        boolean allowDuplicates = "dup".equals(parts[0]);
        int count = Integer.parseInt(parts[1]);

        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        List<Player> targets = new ArrayList<>();
        for (Player player : setupCandidates(game, state)) {
            if (StringUtils.isBlank(effectiveFaction(state, player))) {
                targets.add(player);
            }
        }
        if (targets.isEmpty()) {
            StringBuilder sb = new StringBuilder("Every player already has a faction:\n");
            for (Player player : setupCandidates(game, state)) {
                sb.append("> ")
                        .append(player.getUserName())
                        .append(": ")
                        .append(effectiveFaction(state, player))
                        .append('\n');
            }
            if (game.getPlayers().isEmpty()) {
                sb.append("> (no players are in this game at all - has anyone joined yet?)\n");
            }
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
            return;
        }

        List<FactionModel> pool = new ArrayList<>(FrankenDraft.getDraftableFactionsForGame(game));
        Collections.shuffle(pool);
        if (pool.size() < count) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Only " + pool.size() + " factions are available after bans, but you asked for " + count
                            + " per player. Ban fewer factions or lower the count.");
            return;
        }
        if (!allowDuplicates && pool.size() < targets.size() * count) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Need " + (targets.size() * count) + " distinct factions for " + targets.size()
                            + " players with no duplicates, but only " + pool.size()
                            + " are available. Ban fewer factions, lower the count, or allow duplicates.");
            return;
        }

        int cursor = 0;
        for (Player player : targets) {
            List<String> offered;
            if (allowDuplicates) {
                List<FactionModel> shuffledCopy = new ArrayList<>(pool);
                Collections.shuffle(shuffledCopy);
                offered = shuffledCopy.subList(0, count).stream()
                        .map(FactionModel::getAlias)
                        .toList();
            } else {
                offered = pool.subList(cursor, cursor + count).stream()
                        .map(FactionModel::getAlias)
                        .toList();
                cursor += count;
            }
            state.getDealtFactionChoices().put(player.getUserID(), new ArrayList<>(offered));

            List<Button> factionButtons = new ArrayList<>();
            for (String alias : offered) {
                FactionModel faction = Mapper.getFaction(alias);
                factionButtons.add(Buttons.green(
                        "fowSetupFactionDealtPick_" + alias, faction.getFactionName(), faction.getFactionEmoji()));
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", pick your faction from these options:",
                    factionButtons);
        }
        FowSetupWizardService.saveState(game, state);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Dealt " + count + " factions each to " + targets.size()
                        + " players. They'll pick in their own channel.");
        FowSetupWizardService.openOrRefresh(game);
    }

    @ButtonHandler("fowSetupFactionDealtPick_")
    static void pickDealtFaction(ButtonInteractionEvent event, Game game, String buttonID) {
        String alias = buttonID.replace("fowSetupFactionDealtPick_", "");
        String userId = event.getUser().getId();
        Player player = game.getPlayer(userId);
        if (player == null) return;

        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        List<String> offered = state.getDealtFactionChoices().get(userId);
        if (offered == null || !offered.contains(alias)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "That's not one of your options (maybe you already picked?).");
            return;
        }
        for (Player other : game.getPlayers().values()) {
            if (other != player && alias.equals(effectiveFaction(state, other))) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        Mapper.getFaction(alias).getFactionName()
                                + " was just taken by someone else. Pick a different one.");
                return;
            }
        }

        state.getPendingFactionByUserId().put(userId, alias);
        state.getDealtFactionChoices().remove(userId);
        FowSetupWizardService.saveState(game, state);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " chose **"
                        + Mapper.getFaction(alias).getFactionName() + "**!");
        GMService.sendMessageToGMChannel(
                game,
                player.getUserName() + " picked " + Mapper.getFaction(alias).getFactionName()
                        + " from their dealt options.",
                false);
    }

    /** Treats blank and the literal string "null" (a text-save-format quirk on unset fields) as unset. */
    private static boolean isSetValue(String value) {
        return StringUtils.isNotBlank(value) && !"null".equals(value);
    }

    private static String effectiveFaction(FowSetupWizardState state, Player player) {
        String pending = state.getPendingFactionByUserId().get(player.getUserID());
        if (isSetValue(pending)) return pending;
        String faction = player.getFaction();
        return isSetValue(faction) ? faction : null;
    }

    /**
     * player.getHomeSystemPosition() is only set for Ghost/Crimson's extra tile (or Franken temp spots) -
     * for a normal PlayerSetupService.setupPlayer() call the real assigned position lives in
     * getPlayerStatsAnchorPosition() instead (mirrors the precedence Player.getHomeSystemTile() itself
     * uses). Both fields return the literal string "null" when unset rather than real null.
     */
    private static String effectivePosition(Player player) {
        String override = player.getHomeSystemPosition();
        if (isSetValue(override)) return override;
        String anchor = player.getPlayerStatsAnchorPosition();
        return isSetValue(anchor) ? anchor : null;
    }

    /**
     * A recorded position only counts as a real placement if a tile actually exists there on the GM's map.
     * Franken and Twilight's Fall setup park every player at a temporary off-map anchor (ring-5 "50x", see
     * {@code FrankenDraftBagService.setUpFrankenFactions}) and franken factions have a blank home system, so
     * {@code PlayerSetupService.setupPlayer} records the anchor without ever placing a tile. Treating that
     * as "already placed" is what made "Assign Position" report everyone as done in a Twilight's Fall game.
     */
    private static boolean isPlacedOnMap(Game game, Player player) {
        String position = effectivePosition(player);
        if (position == null) return false;
        Tile tile = game.getTileByPosition(position);
        if (tile == null) return false;
        // Requiring a home-system tile (a 0g slot, or a green-backed faction HS) rather than just any tile
        // also catches stale anchors: loading a map string or JSON wipes the whole tile map
        // (AddTileListService.clearTileMap / MapJsonIOService.removeAllTiles), so a player placed before the
        // load keeps their recorded position while the tile that made it real is gone or replaced.
        if (tile.isHomeSystem()) return true;
        // isHomeSystem() classifies by tile back and returns early, so it says "no" for the handful of
        // factions whose home system is a blue-backed tile - Ignis Aurora's Raven, the Admins of Asyncia and
        // the PBD2000 factions all sit on 62/s14. Accept the tile if it is literally this player's own home
        // system, which is the question actually being asked here.
        FactionModel faction = player.getFactionSetupInfo();
        if (faction == null) return false;
        String homeSystemTileId = AliasHandler.resolveTile(faction.getHomeSystem());
        return StringUtils.isNotBlank(homeSystemTileId) && homeSystemTileId.equalsIgnoreCase(tile.getTileID());
    }

    // --- Manual faction assignment: pick player, then a real dropdown of eligible factions ---

    @ButtonHandler("fowSetupFactionManual")
    static void pickPlayerForFaction(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        List<Button> playerButtons = new ArrayList<>();
        // Players don't count as "real" until they have both a faction and a color, so before any faction is
        // assigned everyone is still "not real" - same list `/game info` shows as "Other Players". Dummies
        // (the neutral player) are never real either, hence setupCandidates rather than getNotRealPlayers.
        for (Player player : setupCandidates(game, state)) {
            if (player.isRealPlayer()) continue;
            playerButtons.add(Buttons.gray("fowSetupFactionPlayer_" + player.getUserID(), player.getUserName()));
        }
        if (playerButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No unassigned players left.");
            return;
        }
        playerButtons.add(Buttons.CANCEL);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), "Which player's faction do you want to set?", playerButtons);
    }

    @ButtonHandler("fowSetupFactionPlayer_")
    static void openFactionSelectMenu(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String userId = buttonID.replace("fowSetupFactionPlayer_", "");
        Player player = game.getPlayer(userId);
        if (player == null) return;

        List<FactionModel> factions = FrankenDraft.getDraftableFactionsForGame(game).stream()
                .sorted(Comparator.comparing(FactionModel::getFactionName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<SelectOption> options = factions.stream()
                .map(faction -> SelectOption.of(faction.getFactionName(), faction.getAlias())
                        .withEmoji(
                                FactionEmojis.getFactionIcon(faction.getAlias()).asEmoji()))
                .toList();
        MessageHelper.sendPagedSelectMenus(
                event.getMessageChannel(),
                "fowSetupFactionSelect_" + userId,
                options,
                "Pick a faction for " + player.getUserName() + ":");
    }

    @SelectionHandler("fowSetupFactionSelect_")
    static void resolveFactionSelect(StringSelectInteractionEvent event, Game game, String menuID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String userId = menuID.replace("fowSetupFactionSelect_", "");
        Player player = game.getPlayer(userId);
        if (player == null) return;

        String faction = event.getValues().getFirst();
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        for (Player other : game.getPlayers().values()) {
            if (other != player && faction.equals(effectiveFaction(state, other))) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "Player " + other.getUserName() + " already has faction " + faction + ". Not saved.");
                return;
            }
        }

        state.getPendingFactionByUserId().put(userId, faction);
        FowSetupWizardService.saveState(game, state);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getUserName() + " will be set up as **"
                        + Mapper.getFaction(faction).getFactionName() + "**.");
        FowSetupWizardService.openOrRefresh(game);
    }

    // --- Position assignment: pick player, then modal for position ---

    @ButtonHandler("fowSetupPositionsManual")
    static void pickPlayerForPosition(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        if (mapIsEmpty(game)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), NO_MAP_MESSAGE);
            return;
        }
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        List<Button> playerButtons = new ArrayList<>();
        for (Player player : setupCandidates(game, state)) {
            if (StringUtils.isBlank(effectiveFaction(state, player)) || isPlacedOnMap(game, player)) {
                continue;
            }
            playerButtons.add(Buttons.gray("fowSetupPositionPlayer_" + player.getUserID(), player.getUserName()));
        }
        if (playerButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Every player with a faction already has a position (or nobody has a faction yet).");
            return;
        }
        playerButtons.add(Buttons.CANCEL);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), "Which player do you want to place?", playerButtons);
    }

    @ButtonHandler("fowSetupPositionPlayer_")
    static void pickPositionForPlayer(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String userId = buttonID.replace("fowSetupPositionPlayer_", "");
        Player player = game.getPlayer(userId);
        if (player == null) return;

        List<Button> positionButtons = new ArrayList<>();
        for (String position : availableHomeSystemPositions(game)) {
            positionButtons.add(Buttons.gray("fowSetupPositionPick_" + userId + "_" + position, position));
        }
        positionButtons.add(Buttons.blue("fowSetupPositionTypeCustom_" + userId + "~MDL", "Type a Custom Position"));
        positionButtons.add(Buttons.CANCEL);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), "Which position for " + player.getUserName() + "?", positionButtons);
    }

    @ButtonHandler("fowSetupPositionPick_")
    static void resolvePositionPick(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String[] parts = buttonID.replace("fowSetupPositionPick_", "").split("_", 2);
        Player player = game.getPlayer(parts[0]);
        if (player == null) return;
        startColorPick(event, game, player, parts[1]);
    }

    @ButtonHandler("fowSetupPositionTypeCustom_")
    static void openPositionModal(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String userId = buttonID.replace("fowSetupPositionTypeCustom_", "").replace("~MDL", "");
        Player player = game.getPlayer(userId);
        if (player == null) return;

        TextInput position = TextInput.create("position", TextInputStyle.SHORT)
                .setPlaceholder("e.g. 305")
                .setRequired(true)
                .build();
        // Discord caps modal titles at Modal.MAX_TITLE_LENGTH (45) - usernames/nicknames can run up to 32
        // chars, so "Home Position for " (19 chars) plus a long one can overflow and throw synchronously.
        String title = StringUtils.left("Home Position for " + player.getUserName(), 45);
        Modal modal = Modal.create("fowSetupPositionResolve_" + userId, title)
                .addComponents(Label.of("Tile Position", position))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler("fowSetupPositionResolve_")
    static void resolvePositionModal(ModalInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String userId = event.getModalId().replace("fowSetupPositionResolve_", "");
        Player player = game.getPlayer(userId);
        if (player == null) return;
        String position = event.getValue("position").getAsString().trim();
        // Validate up front - PlayerSetupService.setupPlayer already assigns faction/color and clears the
        // player's planets/techs/leaders before it checks position validity, so an invalid position here would
        // leave the player half-configured instead of just failing cleanly.
        if (!PositionMapper.isTilePositionValid(position)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Tile position `" + position + "` is not valid. Nothing was changed.");
            return;
        }
        startColorPick(event, game, player, position);
    }

    /**
     * Positions can only meaningfully be assigned once the map exists. The custom-position modal only checks
     * that a coordinate is legal on the grid, not that a tile is there, so without this a GM could drop a
     * home system into empty space on an unbuilt map - and {@code availableHomeSystemPositions} would have
     * had no suggestions to offer anyway. The wizard's step order already puts Load the Map first; this stops
     * the out-of-order case rather than relying on the GM following it.
     */
    private static boolean mapIsEmpty(Game game) {
        return game.getTileMap().isEmpty();
    }

    private static final String NO_MAP_MESSAGE =
            "The map is empty - build or import it on the **Load the Map** step first. Home positions have to "
                    + "point at tiles that already exist, otherwise you'd strand a home system in empty space.";

    private static List<String> availableHomeSystemPositions(Game game) {
        List<String> takenPositions = new ArrayList<>();
        for (Player player : game.getPlayers().values()) {
            String pos = effectivePosition(player);
            if (pos != null) {
                takenPositions.add(pos);
            }
        }
        List<String> availablePositions = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.isHomeSystem() && !takenPositions.contains(tile.getPosition())) {
                availablePositions.add(tile.getPosition());
            }
        }
        return availablePositions;
    }

    private static void startColorPick(GenericInteractionCreateEvent event, Game game, Player player, String position) {
        String userId = player.getUserID();
        // Common funnel for both the position-button and the typed-custom-position paths, so the empty-map
        // guard here covers the modal route that bypasses pickPlayerForPosition's own check.
        if (mapIsEmpty(game)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), NO_MAP_MESSAGE);
            return;
        }
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        String faction = effectiveFaction(state, player);
        if (StringUtils.isBlank(faction)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), player.getUserName() + " has no faction yet.");
            return;
        }
        state.getPendingPositionByUserId().put(userId, position);
        FowSetupWizardService.saveState(game, state);

        List<ColorModel> unusedColors = GameColorsService.getUnusedColors(game).stream()
                .sorted(Comparator.comparing(ColorModel::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (unusedColors.isEmpty()) {
            // No free colors somehow - fall back to auto-pick rather than block setup entirely.
            finishPositionAssignment(event, game, player, faction, position, null);
            return;
        }
        // The colour palette is well over Discord's per-menu option cap, so this spans several menus.
        MessageHelper.sendPagedSelectMenus(
                event.getMessageChannel(),
                "fowSetupColorSelect_" + userId,
                FowSetupNeutralPlayerService.colorSelectOptions(unusedColors),
                "Pick a color for " + player.getUserName() + ":");
    }

    @SelectionHandler("fowSetupColorSelect_")
    static void resolveColorSelect(StringSelectInteractionEvent event, Game game, String menuID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String userId = menuID.replace("fowSetupColorSelect_", "");
        Player player = game.getPlayer(userId);
        if (player == null) return;

        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        String faction = effectiveFaction(state, player);
        String position = state.getPendingPositionByUserId().get(userId);
        if (StringUtils.isBlank(faction) || StringUtils.isBlank(position)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Missing faction or position for " + player.getUserName() + ". Start the position pick over.");
            return;
        }
        finishPositionAssignment(
                event, game, player, faction, position, event.getValues().getFirst());
    }

    private static void finishPositionAssignment(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            String faction,
            String position,
            String color) {
        String userId = player.getUserID();
        PlayerSetupState setupState = new PlayerSetupState(color, faction, position, false);
        PlayerSetupService.setupPlayer(setupState, player, game, event);

        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        state.getPendingFactionByUserId().remove(userId);
        state.getPendingPositionByUserId().remove(userId);
        FowSetupWizardService.saveState(game, state);
        FowSetupWizardService.openOrRefresh(game);

        // Gate factions get a second tile. setupPlayer either drops it on an arbitrary corner (ghost,
        // crimson) or - because its check uses the non-existent alias "miltymod_ghost" - not at all
        // (miltymodghost, pi_ghost). Neither is right for FoW, where the GM built the map, so let them
        // choose where it goes.
        String realHomeTile = gateFactionHomeTile(player);
        if (realHomeTile != null) {
            promptGateHomeSystem(event, game, player, realHomeTile);
        }
    }

    // --- Gate factions (Ghosts of Creuss variants, Crimson): a second, GM-placed home-system tile ---

    /**
     * Gate tile (the faction's declared {@code homeSystem}) mapped to the actual home-system tile that goes
     * with it. Mirrors the hardcoding already in {@code PlayerSetupService} - the faction data has no field
     * for a second home system, so there's nothing to derive this from. Keyed on the gate tile rather than
     * the faction alias so it covers every Creuss variant (ghost, miltymodghost, pi_ghost) at once.
     */
    private static final Map<String, String> GATE_TILE_TO_HOME_TILE = Map.of("17", "51", "94", "118");

    /** The real home-system tile id for a gate faction, or null if this faction isn't one. */
    private static String gateFactionHomeTile(Player player) {
        FactionModel faction = player.getFactionSetupInfo();
        if (faction == null) return null;
        String gateTile = AliasHandler.resolveTile(faction.getHomeSystem());
        return gateTile == null ? null : GATE_TILE_TO_HOME_TILE.get(gateTile);
    }

    private static void promptGateHomeSystem(
            GenericInteractionCreateEvent event, Game game, Player player, String realHomeTile) {
        List<Button> positionButtons = new ArrayList<>();
        for (String position : availableHomeSystemPositions(game)) {
            positionButtons.add(Buttons.gray("fowSetupGateHomePick_" + player.getUserID() + "_" + position, position));
        }
        positionButtons.add(
                Buttons.blue("fowSetupGateHomeCustom_" + player.getUserID() + "~MDL", "Type a Custom Position"));
        positionButtons.add(Buttons.CANCEL);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getUserName() + " plays a gate faction, so their actual home system is a separate tile. "
                        + "Where should it go? (Currently at `" + effectivePosition(player)
                        + "` - the bot's automatic guess, which you can override here.)",
                positionButtons);
    }

    @ButtonHandler("fowSetupGateHomePick_")
    static void resolveGateHomePick(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String[] parts = buttonID.replace("fowSetupGateHomePick_", "").split("_", 2);
        placeGateHomeSystem(event, game, game.getPlayer(parts[0]), parts[1]);
    }

    @ButtonHandler("fowSetupGateHomeCustom_")
    static void openGateHomeModal(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String userId = buttonID.replace("fowSetupGateHomeCustom_", "").replace("~MDL", "");
        Player player = game.getPlayer(userId);
        if (player == null) return;
        TextInput position = TextInput.create("position", TextInputStyle.SHORT)
                .setPlaceholder("e.g. tr")
                .setRequired(true)
                .build();
        Modal modal = Modal.create(
                        "fowSetupGateHomeResolve_" + userId,
                        StringUtils.left("Home System for " + player.getUserName(), 45))
                .addComponents(Label.of("Tile Position", position))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler("fowSetupGateHomeResolve_")
    static void resolveGateHomeModal(ModalInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String userId = event.getModalId().replace("fowSetupGateHomeResolve_", "");
        placeGateHomeSystem(
                event,
                game,
                game.getPlayer(userId),
                event.getValue("position").getAsString().trim());
    }

    private static void placeGateHomeSystem(
            GenericInteractionCreateEvent event, Game game, Player player, String position) {
        if (player == null) return;
        String realHomeTile = gateFactionHomeTile(player);
        if (realHomeTile == null) return;
        if (!PositionMapper.isTilePositionValid(position)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Tile position `" + position + "` is not valid. Nothing was changed.");
            return;
        }
        // Clear whatever setupPlayer guessed, so overriding doesn't leave a duplicate home system behind.
        String previous = player.getHomeSystemPosition();
        if (isSetValue(previous) && !previous.equals(position)) {
            Tile stale = game.getTileByPosition(previous);
            if (stale != null && realHomeTile.equalsIgnoreCase(stale.getTileID())) {
                game.removeTile(previous);
            }
        }
        game.setTile(new Tile(realHomeTile, position));
        player.setHomeSystemPosition(position);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), player.getUserName() + "'s home system placed at `" + position + "`.");
        FowSetupWizardService.openOrRefresh(game);
    }

    @ButtonHandler("fowSetupPositionsRandomize")
    static void randomizePositions(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        if (mapIsEmpty(game)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), NO_MAP_MESSAGE);
            return;
        }
        FowSetupWizardState state = FowSetupWizardService.loadState(game);

        List<Player> needsPosition = new ArrayList<>();
        for (Player player : setupCandidates(game, state)) {
            if (StringUtils.isNotBlank(effectiveFaction(state, player)) && !isPlacedOnMap(game, player)) {
                needsPosition.add(player);
            }
        }
        if (needsPosition.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No players need a position right now.");
            return;
        }

        List<String> availablePositions = new ArrayList<>(availableHomeSystemPositions(game));
        Collections.shuffle(availablePositions);

        if (availablePositions.size() < needsPosition.size()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Only " + availablePositions.size() + " open home positions for " + needsPosition.size()
                            + " players needing one. Placing as many as possible.");
        }

        for (int i = 0; i < needsPosition.size() && i < availablePositions.size(); i++) {
            Player player = needsPosition.get(i);
            String faction = effectiveFaction(state, player);
            PlayerSetupState setupState = new PlayerSetupState(faction, availablePositions.get(i), false);
            PlayerSetupService.setupPlayer(setupState, player, game, event);
            state.getPendingFactionByUserId().remove(player.getUserID());
        }
        FowSetupWizardService.saveState(game, state);
        FowSetupWizardService.openOrRefresh(game);
    }

    @ButtonHandler("fowSetupShowBoards")
    static void showAllBoards(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        for (Player player : game.getRealPlayers()) {
            if (player.getMember() == null) continue;
            ShowGameService.simpleShowGame(
                    game, new UserOverridenGenericInteractionCreateEvent(event, player.getMember()));
        }
    }
}
