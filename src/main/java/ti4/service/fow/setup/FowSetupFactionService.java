package ti4.service.fow.setup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.collections4.ListUtils;
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
import ti4.service.emoji.ColorEmojis;
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
     * Reachable whenever a GM sets up the neutral player and then steps back to Factions.
     */
    private static List<Player> setupCandidates(Game game) {
        return game.getPlayers().values().stream()
                .filter(player -> !player.isDummy())
                .toList();
    }

    static void render(Game game, FowSetupWizardState state, StringBuilder sb, List<Button> buttons) {
        sb.append("Assign each player a faction manually, or deal a mini faction draft (ban factions first if ")
                .append("you want), then assign home positions.\n\n");
        for (Player player : setupCandidates(game)) {
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
        postBanFactionsMenu(event, game, false, null);
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
        postBanFactionsMenu(event, game, true, leadNote);
    }

    /** {@code editInPlace} updates the message the clicked toggle button lives on instead of reposting
     * a fresh ban list on every single ban/unban click - {@code leadNote} folds the confirmation into
     * that same message rather than sending a separate one. */
    private static void postBanFactionsMenu(
            ButtonInteractionEvent event, Game game, boolean editInPlace, String leadNote) {
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
        factionButtons.add(Buttons.CANCEL);
        String message =
                (leadNote == null ? "" : leadNote + "\n\n") + "Click a faction to ban/unban it from the Franken pool:";
        // A single message caps out at Modal.MAX_COMPONENTS (5) rows of 5 buttons = 25. The full Franken-
        // legal faction pool (base+PoK, plus DS/BR/Absol/homebrew when enabled) routinely exceeds that, so
        // editing in place would silently fail to update once it does - fall back to a fresh multi-message
        // send (sendMessageToChannelWithButtons auto-paginates) rather than eating the edit.
        if (editInPlace && factionButtons.size() <= 25) {
            MessageHelper.editMessageWithButtons(event, message, factionButtons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, factionButtons);
        }
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
        for (Player player : setupCandidates(game)) {
            if (StringUtils.isBlank(effectiveFaction(state, player))) {
                targets.add(player);
            }
        }
        if (targets.isEmpty()) {
            StringBuilder sb = new StringBuilder("Every player already has a faction:\n");
            for (Player player : setupCandidates(game)) {
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
        return position != null && game.getTileByPosition(position) != null;
    }

    // --- Manual faction assignment: pick player, then a real dropdown of eligible factions ---

    @ButtonHandler("fowSetupFactionManual")
    static void pickPlayerForFaction(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        List<Button> playerButtons = new ArrayList<>();
        // Players don't count as "real" until they have both a faction and a color, so before any faction is
        // assigned everyone is still "not real" - same list `/game info` shows as "Other Players". Dummies
        // (the neutral player) are never real either, hence setupCandidates rather than getNotRealPlayers.
        for (Player player : setupCandidates(game)) {
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
        for (List<FactionModel> page : ListUtils.partition(factions, 25)) {
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("fowSetupFactionSelect_" + userId);
            for (FactionModel faction : page) {
                menuBuilder.addOptions(SelectOption.of(faction.getFactionName(), faction.getAlias())
                        .withEmoji(
                                FactionEmojis.getFactionIcon(faction.getAlias()).asEmoji()));
            }
            menuBuilder.setRequiredRange(1, 1);
            String range = pageRangeLabel(
                    page.stream().map(FactionModel::getFactionName).toList());
            event.getMessageChannel()
                    .sendMessage("Pick a faction for " + player.getUserName() + range + ":")
                    .addComponents(ActionRow.of(menuBuilder.build()))
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    /** " (A - M)" style suffix describing the alphabetical range of names on one select-menu page. */
    static String pageRangeLabel(List<String> namesInPageOrder) {
        if (namesInPageOrder.isEmpty()) return "";
        String first = namesInPageOrder.getFirst();
        String last = namesInPageOrder.getLast();
        return first.equals(last) ? " (" + first + ")" : " (" + first + " - " + last + ")";
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
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        List<Button> playerButtons = new ArrayList<>();
        for (Player player : setupCandidates(game)) {
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
        // Discord select menus cap out at 25 options - this codebase's color palette is well over that.
        for (List<ColorModel> page : ListUtils.partition(unusedColors, 25)) {
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("fowSetupColorSelect_" + userId);
            for (ColorModel color : page) {
                menuBuilder.addOptions(SelectOption.of(StringUtils.capitalize(color.getName()), color.getName())
                        .withEmoji(ColorEmojis.getColorEmoji(color.getName()).asEmoji()));
            }
            menuBuilder.setRequiredRange(1, 1);
            String range = pageRangeLabel(page.stream()
                    .map(color -> StringUtils.capitalize(color.getName()))
                    .toList());
            event.getMessageChannel()
                    .sendMessage("Pick a color for " + player.getUserName() + range + ":")
                    .addComponents(ActionRow.of(menuBuilder.build()))
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
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
    }

    @ButtonHandler("fowSetupPositionsRandomize")
    static void randomizePositions(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupWizardState state = FowSetupWizardService.loadState(game);

        List<Player> needsPosition = new ArrayList<>();
        for (Player player : setupCandidates(game)) {
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
