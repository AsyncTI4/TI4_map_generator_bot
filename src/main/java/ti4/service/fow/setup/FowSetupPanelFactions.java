package ti4.service.fow.setup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.radiogroup.RadioGroup;
import net.dv8tion.jda.api.components.selections.SelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
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
import ti4.logging.BotLogger;
import ti4.message.componentsV2.ComponentPanel;
import ti4.model.FactionModel;
import ti4.model.Mapper;
import ti4.service.emoji.FactionEmojis;

final class FowSetupPanelFactions {

    private static final String PLAYER_SELECT = "fowWizFacPlayer";
    private static final String VIEW_PREFIX = "fowWizFacView_";
    private static final String FACTION_PREFIX = "fowWizFac_";
    private static final String POSITION_PREFIX = "fowWizPos_";
    private static final String POSITION_CUSTOM_PREFIX = "fowWizPosCustom_";
    private static final String POSITION_RESOLVE_PREFIX = "fowWizPosResolve_";
    private static final String POSITION_CLEAR_PREFIX = "fowWizPosClear_";
    private static final String COLOUR_PREFIX = "fowWizColor_";
    private static final String GATE_PREFIX = "fowWizGate_";
    private static final String GATE_CUSTOM_PREFIX = "fowWizGateCustom_";
    private static final String GATE_RESOLVE_PREFIX = "fowWizGateResolve_";
    private static final String BAN_PAGE_PREFIX = "fowWizBanPage_";
    private static final String BAN_SELECT_PREFIX = "fowWizBanSel_";
    private static final String DEAL_MODAL = "fowWizDeal~MDL";
    private static final String DEAL_RESOLVE = "fowWizDealResolve";
    private static final String ALLIANCE_MODAL = "fowWizAlliance~MDL";
    private static final String ALLIANCE_RESOLVE = "fowWizAllianceResolve";
    private static final String RANDOMIZE = "fowWizRandomize";
    private static final String PLAYER_SUBVIEW = "factions:player:";
    private static final String BAN_SUBVIEW = "factions:ban:";
    private static final String AUTO = "auto";
    private static final String FACTION_STAGE = "faction";
    private static final int MENUS_PER_PAGE = 3;
    private static final int BAN_MENUS_PER_PAGE = 4;
    private static final String MDL = "~MDL";

    private FowSetupPanelFactions() {}

    private record PlayerView(String userId, String stage, int page) {

        static PlayerView parse(String token) {
            String[] parts = token.split(":");
            String stage = parts.length > 1 ? parts[1] : AUTO;
            Integer page = parts.length > 2 ? FowSetupPanel.parseInt(parts[2]) : null;
            return new PlayerView(parts[0], stage, page == null || page < 0 ? 0 : page);
        }

        String token() {
            return PLAYER_SUBVIEW + userId + ":" + stage + ":" + page;
        }
    }

    static String playerSubView(String userId, String stage, int page) {
        return new PlayerView(userId, stage, page).token();
    }

    static FowSetupPanel.View view(Game game, FowSetupWizardState state) {
        String subView = StringUtils.defaultString(state.getSubView());
        if (subView.startsWith(PLAYER_SUBVIEW)) {
            PlayerView view = PlayerView.parse(subView.substring(PLAYER_SUBVIEW.length()));
            Player player = game.getPlayer(view.userId());
            if (player != null) return playerView(game, state, player, view);
        }
        if (subView.startsWith(BAN_SUBVIEW)) {
            Integer page = FowSetupPanel.parseInt(subView.substring(BAN_SUBVIEW.length()));
            return banView(game, page == null ? 0 : page);
        }
        return overview(game, state);
    }

    private static FowSetupPanel.View overview(Game game, FowSetupWizardState state) {
        boolean noMap = FowSetupFactionService.mapIsEmpty(game);
        boolean draftRunning = game.getActiveBagDraft() != null;
        StringBuilder text = new StringBuilder("Pick a player to give them a faction, a home position and a colour,")
                .append(" or deal a mini draft (ban factions first if you want).\n");
        if (noMap) {
            text.append("⚠ No map yet. Pick factions freely, but positions need the map (**Load the Map** step).\n");
        }
        if (draftRunning) {
            text.append("⚠ A draft is running. Assign positions **after** it finishes; starting or restarting a")
                    .append(" draft moves every player back to a temporary off-map position.\n");
        }
        List<Player> candidates = FowSetupFactionService.setupCandidates(game, state);
        text.append('\n').append(StringUtils.abbreviate(FowSetupFactionService.playerStatusLines(game, state), 2000));
        if (candidates.isEmpty()) {
            text.append("_Nobody to set up. Check the **Confirm Players** step._");
        }
        if (game.isAllianceMode()) {
            text.append("\n**Alliance pairs**\n").append(FowSetupFactionService.alliancePairLines(game));
        }

        List<ActionRow> rows = new ArrayList<>();
        if (!candidates.isEmpty()) {
            List<SelectOption> options = candidates.stream()
                    .limit(SelectMenu.OPTIONS_MAX_AMOUNT)
                    .map(player -> SelectOption.of(StringUtils.left(player.getUserName(), 100), player.getUserID())
                            .withDescription(StringUtils.abbreviate(playerSummary(game, state, player), 100)))
                    .toList();
            rows.add(ActionRow.of(StringSelectMenu.create(PLAYER_SELECT)
                    .setPlaceholder("Set up a player...")
                    .addOptions(options)
                    .setRequiredRange(1, 1)
                    .build()));
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue(DEAL_MODAL, "Deal factions..."));
        buttons.add(Buttons.gray(BAN_PAGE_PREFIX + 0, "Ban factions"));
        buttons.add(Buttons.green(RANDOMIZE, "Randomize positions"));
        buttons.add(Buttons.gray("fowSetupShowBoards", "Show player boards"));
        if (game.isAllianceMode() && game.getRealPlayers().size() >= 2) {
            buttons.add(Buttons.blue(ALLIANCE_MODAL, "Pair alliance..."));
        }
        rows.add(ActionRow.of(buttons));
        return FowSetupPanel.View.main(List.of(TextDisplay.of(text.toString())), rows, noMap || draftRunning);
    }

    private static String playerSummary(Game game, FowSetupWizardState state, Player player) {
        String faction = FowSetupFactionService.effectiveFaction(state, player);
        String position = FowSetupFactionService.effectivePosition(player);
        if (faction == null) return "No faction yet";
        String summary = factionName(faction);
        if (position != null && FowSetupFactionService.isPlacedOnMap(game, player)) {
            summary += " @ " + position;
        } else if (state.getPendingPositionByUserId().containsKey(player.getUserID())) {
            summary += " · needs a colour";
        } else {
            summary += " · needs a position";
        }
        return summary;
    }

    private static String factionName(String alias) {
        FactionModel faction = Mapper.getFaction(alias);
        return faction == null ? alias : faction.getFactionName();
    }

    private static FowSetupPanel.View playerView(Game game, FowSetupWizardState state, Player player, PlayerView view) {
        String userId = player.getUserID();
        String faction = FowSetupFactionService.effectiveFaction(state, player);
        String pendingPosition = state.getPendingPositionByUserId().get(userId);
        boolean placed = FowSetupFactionService.isPlacedOnMap(game, player);
        boolean real = player.isRealPlayer();

        StringBuilder text = new StringBuilder("### ")
                .append(player.getUserName())
                .append("\n**Faction:** ")
                .append(faction == null ? "_none yet_" : factionName(faction))
                .append(" · **Position:** ")
                .append(
                        placed
                                ? FowSetupFactionService.effectivePosition(player)
                                : pendingPosition == null ? "_none_" : pendingPosition + " _(pending)_")
                .append(" · **Colour:** ")
                .append(
                        StringUtils.isBlank(player.getColor()) || "null".equals(player.getColor())
                                ? "_none_"
                                : player.getColor())
                .append('\n');

        List<ActionRow> rows = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();
        boolean chooseFaction = !real && (faction == null || FACTION_STAGE.equals(view.stage()));
        boolean warning = false;
        if (chooseFaction) {
            text.append("Pick a faction. Banned factions are left out.");
            List<SelectOption> options = FowSetupFactionService.assignableFactions(game).stream()
                    .map(FowSetupFactionService::factionOption)
                    .toList();
            int pages = ComponentPanel.pageCount(options.size(), MENUS_PER_PAGE);
            int page = Math.min(view.page(), pages - 1);
            rows.addAll(ComponentPanel.selectPages(
                    FACTION_PREFIX + userId + "_", options, page, MENUS_PER_PAGE, "Faction"));
            addPaging(buttons, userId, FACTION_STAGE, page, pages);
        } else if (!placed && pendingPosition == null) {
            if (FowSetupFactionService.mapIsEmpty(game)) {
                text.append("⚠ ").append(FowSetupFactionService.NO_MAP_MESSAGE);
                warning = true;
            } else {
                text.append("Pick a home position, or type one.");
                List<String> positions = FowSetupFactionService.availableHomeSystemPositions(game);
                if (!positions.isEmpty()) {
                    rows.add(ActionRow.of(positionSelect(POSITION_PREFIX + userId, positions, "Home position")));
                } else {
                    text.append(" No open home-system tiles are on the map, so type one.");
                }
                buttons.add(Buttons.blue(POSITION_CUSTOM_PREFIX + userId + MDL, "Type a position..."));
            }
            if (!real) {
                buttons.add(Buttons.gray(VIEW_PREFIX + userId + "_" + FACTION_STAGE + "_0", "Change faction"));
            }
        } else if (!placed) {
            text.append("Pick a colour to finish placing ")
                    .append(player.getUserName())
                    .append('.');
            List<SelectOption> options =
                    FowSetupNeutralPlayerService.colorSelectOptions(FowSetupFactionService.colorsFor(game, player));
            int pages = ComponentPanel.pageCount(options.size(), MENUS_PER_PAGE);
            int page = Math.min(view.page(), pages - 1);
            rows.addAll(
                    ComponentPanel.selectPages(COLOUR_PREFIX + userId + "_", options, page, MENUS_PER_PAGE, "Colour"));
            addPaging(buttons, userId, AUTO, page, pages);
            buttons.add(Buttons.gray(POSITION_CLEAR_PREFIX + userId, "Change position"));
        } else {
            text.append(placed ? "Placed on the map." : "Set up, but not on a home-system tile of this map.");
            if (FowSetupFactionService.gateFactionHomeTile(player) != null) {
                text.append("\nGate faction: the real home system is a separate tile. Choose where it goes")
                        .append(" (now `")
                        .append(StringUtils.defaultString(player.getHomeSystemPosition(), "none"))
                        .append("`, the bot's guess).");
                List<String> positions = FowSetupFactionService.availableHomeSystemPositions(game);
                if (!positions.isEmpty()) {
                    rows.add(ActionRow.of(positionSelect(GATE_PREFIX + userId, positions, "Real home system")));
                }
                buttons.add(Buttons.blue(GATE_CUSTOM_PREFIX + userId + MDL, "Type a position..."));
            }
        }
        buttons.add(FowSetupPanel.backButton());
        rows.add(ActionRow.of(buttons));
        return FowSetupPanel.View.sub(List.of(TextDisplay.of(text.toString())), rows, warning);
    }

    private static void addPaging(List<Button> buttons, String userId, String stage, int page, int pages) {
        if (page > 0) {
            buttons.add(Buttons.gray(VIEW_PREFIX + userId + "_" + stage + "_" + (page - 1), "◀ More"));
        }
        if (page < pages - 1) {
            buttons.add(Buttons.gray(VIEW_PREFIX + userId + "_" + stage + "_" + (page + 1), "More ▶"));
        }
    }

    private static StringSelectMenu positionSelect(String id, List<String> positions, String placeholder) {
        List<SelectOption> options = positions.stream()
                .limit(SelectMenu.OPTIONS_MAX_AMOUNT)
                .map(position -> SelectOption.of(position, position))
                .toList();
        return StringSelectMenu.create(id)
                .setPlaceholder(placeholder)
                .addOptions(options)
                .setRequiredRange(1, 1)
                .build();
    }

    private static String userIdBeforeIndex(String idTail) {
        return idTail.substring(0, idTail.lastIndexOf('_'));
    }

    @SelectionHandler(PLAYER_SELECT)
    static void pickPlayer(StringSelectInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupPanel.openSubView(event, game, playerSubView(event.getValues().getFirst(), AUTO, 0), null);
    }

    @ButtonHandler(VIEW_PREFIX)
    static void changePlayerView(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String[] parts = buttonID.substring(VIEW_PREFIX.length()).split("_");
        FowSetupPanel.openSubView(event, game, PLAYER_SUBVIEW + String.join(":", parts), null);
    }

    @SelectionHandler(FACTION_PREFIX)
    static void pickFaction(StringSelectInteractionEvent event, Game game, String menuID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String userId = userIdBeforeIndex(menuID.substring(FACTION_PREFIX.length()));
        Player player = game.getPlayer(userId);
        if (player == null) return;
        String faction = event.getValues().getFirst();
        String error = FowSetupFactionService.assignPendingFaction(game, player, faction);
        String notice =
                error != null ? error : player.getUserName() + " will be set up as **" + factionName(faction) + "**.";
        FowSetupPanel.openSubView(event, game, playerSubView(userId, AUTO, 0), notice);
    }

    @SelectionHandler(POSITION_PREFIX)
    static void pickPosition(StringSelectInteractionEvent event, Game game, String menuID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        pendPosition(
                event,
                game,
                menuID.substring(POSITION_PREFIX.length()),
                event.getValues().getFirst());
    }

    @ButtonHandler(POSITION_CUSTOM_PREFIX)
    static void openPositionModal(ButtonInteractionEvent event, Game game, String buttonID) {
        String userId = StringUtils.removeEnd(buttonID.substring(POSITION_CUSTOM_PREFIX.length()), MDL);
        event.replyModal(positionModal(POSITION_RESOLVE_PREFIX + userId, "Home Position", game.getPlayer(userId)))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    static Modal positionModal(String modalId, String title, Player player) {
        TextInput position = TextInput.create("position", TextInputStyle.SHORT)
                .setPlaceholder("e.g. 305")
                .setRequired(true)
                .build();
        String name = player == null ? "" : " for " + player.getUserName();
        return Modal.create(modalId, StringUtils.left(title + name, Modal.MAX_TITLE_LENGTH))
                .addComponents(Label.of("Tile position", position))
                .build();
    }

    @ModalHandler(POSITION_RESOLVE_PREFIX)
    static void resolvePositionModal(ModalInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String userId = event.getModalId().substring(POSITION_RESOLVE_PREFIX.length());
        pendPosition(event, game, userId, FowSetupPanel.value(event, "position"));
    }

    private static void pendPosition(GenericInteractionCreateEvent event, Game game, String userId, String position) {
        Player player = game.getPlayer(userId);
        if (player == null) return;
        String error = FowSetupFactionService.pendPosition(game, player, position);
        if (error != null) {
            FowSetupPanel.openSubView(event, game, playerSubView(userId, AUTO, 0), error);
            return;
        }
        if (FowSetupFactionService.colorsFor(game, player).isEmpty()) {
            finishSetup(event, game, player, null);
            return;
        }
        FowSetupPanel.openSubView(event, game, playerSubView(userId, AUTO, 0), null);
    }

    @ButtonHandler(POSITION_CLEAR_PREFIX)
    static void clearPosition(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String userId = buttonID.substring(POSITION_CLEAR_PREFIX.length());
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        state.getPendingPositionByUserId().remove(userId);
        state.setSubView(playerSubView(userId, AUTO, 0));
        FowSetupWizardService.saveState(game, state);
        FowSetupPanel.show(event, game);
    }

    @SelectionHandler(COLOUR_PREFIX)
    static void pickColour(StringSelectInteractionEvent event, Game game, String menuID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        Player player = game.getPlayer(userIdBeforeIndex(menuID.substring(COLOUR_PREFIX.length())));
        if (player == null) return;
        finishSetup(event, game, player, event.getValues().getFirst());
    }

    private static void finishSetup(GenericInteractionCreateEvent event, Game game, Player player, String colour) {
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        String userId = player.getUserID();
        String faction = FowSetupFactionService.effectiveFaction(state, player);
        String position = state.getPendingPositionByUserId().get(userId);
        if (StringUtils.isBlank(faction) || StringUtils.isBlank(position)) {
            FowSetupPanel.openSubView(
                    event, game, playerSubView(userId, AUTO, 0), "Missing faction or position. Pick them again.");
            return;
        }
        FowSetupFactionService.completeSetup(event, game, player, faction, position, colour);
        String notice = player.getUserName() + " is set up as **" + factionName(faction) + "** at `" + position + "`"
                + (colour == null ? "." : " in " + colour + ".");
        if (FowSetupFactionService.gateFactionHomeTile(player) != null) {
            FowSetupPanel.openSubView(
                    event,
                    game,
                    playerSubView(userId, AUTO, 0),
                    notice + " Gate faction: now choose where the real home system goes.");
            return;
        }
        FowSetupPanel.openSubView(event, game, null, notice);
    }

    @SelectionHandler(GATE_PREFIX)
    static void pickGateHome(StringSelectInteractionEvent event, Game game, String menuID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        placeGateHome(
                event,
                game,
                menuID.substring(GATE_PREFIX.length()),
                event.getValues().getFirst());
    }

    @ButtonHandler(GATE_CUSTOM_PREFIX)
    static void openGateModal(ButtonInteractionEvent event, Game game, String buttonID) {
        String userId = StringUtils.removeEnd(buttonID.substring(GATE_CUSTOM_PREFIX.length()), MDL);
        event.replyModal(positionModal(GATE_RESOLVE_PREFIX + userId, "Home System", game.getPlayer(userId)))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(GATE_RESOLVE_PREFIX)
    static void resolveGateModal(ModalInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String userId = event.getModalId().substring(GATE_RESOLVE_PREFIX.length());
        placeGateHome(event, game, userId, FowSetupPanel.value(event, "position"));
    }

    private static void placeGateHome(GenericInteractionCreateEvent event, Game game, String userId, String position) {
        Player player = game.getPlayer(userId);
        if (player == null) return;
        String error = FowSetupFactionService.placeGateHome(game, player, position);
        if (error != null) {
            FowSetupPanel.openSubView(event, game, playerSubView(userId, AUTO, 0), error);
            return;
        }
        FowSetupPanel.openSubView(
                event, game, null, player.getUserName() + "'s home system placed at `" + position + "`.");
    }

    private static FowSetupPanel.View banView(Game game, int requestedPage) {
        List<FactionModel> factions = FrankenDraft.getAllFrankenLegalFactions(game).stream()
                .sorted(Comparator.comparing(FactionModel::getFactionName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<String> banned = FowSetupFactionService.bannedFactionAliases(game);
        int pages = ComponentPanel.pageCount(factions.size(), BAN_MENUS_PER_PAGE);
        int page = Math.clamp(requestedPage, 0, pages - 1);

        List<String> bannedNames =
                banned.stream().map(FowSetupPanelFactions::factionName).toList();
        String text = "### Ban factions\nPicked factions are banned: they leave the pool for manual picks, the mini"
                + " draft and `/franken`. Nothing is banned by default.\n**Banned (" + banned.size() + "):** "
                + (banned.isEmpty() ? "_none_" : StringUtils.abbreviate(String.join(", ", bannedNames), 1500));

        List<List<FactionModel>> menus = ListUtils.partition(factions, SelectMenu.OPTIONS_MAX_AMOUNT);
        List<ActionRow> rows = new ArrayList<>();
        int first = page * BAN_MENUS_PER_PAGE;
        for (int index = first; index < menus.size() && index < first + BAN_MENUS_PER_PAGE; index++) {
            List<FactionModel> menu = menus.get(index);
            List<SelectOption> options = menu.stream()
                    .map(faction -> SelectOption.of(StringUtils.left(faction.getFactionName(), 100), faction.getAlias())
                            .withEmoji(FactionEmojis.getFactionIcon(faction.getAlias())
                                    .asEmoji())
                            .withDefault(banned.contains(faction.getAlias())))
                    .toList();
            String range =
                    menu.getFirst().getFactionName() + " - " + menu.getLast().getFactionName();
            rows.add(ActionRow.of(StringSelectMenu.create(BAN_SELECT_PREFIX + index)
                    .setPlaceholder(
                            StringUtils.left("Nothing banned (" + range + ")", SelectMenu.PLACEHOLDER_MAX_LENGTH))
                    .addOptions(options)
                    .setRequiredRange(0, options.size())
                    .build()));
        }
        List<Button> buttons = new ArrayList<>();
        if (page > 0) buttons.add(Buttons.gray(BAN_PAGE_PREFIX + (page - 1), "◀ Previous factions"));
        if (page < pages - 1) buttons.add(Buttons.gray(BAN_PAGE_PREFIX + (page + 1), "More factions ▶"));
        buttons.add(Buttons.green(FowSetupPanel.BACK, "Done"));
        rows.add(ActionRow.of(buttons));
        return FowSetupPanel.View.sub(List.of(TextDisplay.of(text)), rows, false);
    }

    @ButtonHandler(BAN_PAGE_PREFIX)
    static void openBanPage(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupPanel.openSubView(event, game, BAN_SUBVIEW + buttonID.substring(BAN_PAGE_PREFIX.length()), null);
    }

    @SelectionHandler(BAN_SELECT_PREFIX)
    static void setBans(StringSelectInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        List<String> banned = applyBans(
                FowSetupFactionService.bannedFactionAliases(game),
                FowSetupPanelPlayers.optionValues(event),
                event.getValues());
        FowSetupFactionService.saveBannedFactions(game, banned);
        FowSetupPanel.show(event, game);
    }

    static List<String> applyBans(List<String> banned, List<String> menuAliases, List<String> picked) {
        List<String> updated = new ArrayList<>(banned);
        updated.removeAll(menuAliases);
        updated.addAll(picked);
        return updated;
    }

    static Modal dealModal() {
        TextInput count = TextInput.create("count", TextInputStyle.SHORT)
                .setPlaceholder("e.g. 3")
                .setRequired(true)
                .build();
        RadioGroup duplicates = RadioGroup.create("duplicates")
                .addOption("No faction on two lists", "nodup", "Every player gets different factions", true)
                .addOption("Duplicates are fine", "dup", "Lists may share factions")
                .setRequired(true)
                .build();
        return Modal.create(DEAL_RESOLVE, "Deal Factions")
                .addComponents(
                        TextDisplay.of("Every player without a faction gets their own list in their private channel"
                                + " and picks one there. Banned factions are left out."),
                        Label.of("Factions offered per player", count),
                        Label.of("Can lists share factions?", duplicates))
                .build();
    }

    @ButtonHandler(DEAL_MODAL)
    static void openDealModal(ButtonInteractionEvent event) {
        event.replyModal(dealModal()).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(DEAL_RESOLVE)
    static void resolveDealModal(ModalInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        Integer count = FowSetupPanel.parseInt(FowSetupPanel.value(event, "count"));
        if (count == null || count < 1) {
            FowSetupPanel.show(event, game, "The count must be a whole number of at least 1. Nothing was dealt.");
            return;
        }
        boolean allowDuplicates = "dup".equals(FowSetupPanel.value(event, "duplicates"));
        FowSetupPanel.show(event, game, FowSetupFactionService.dealFactions(game, count, allowDuplicates));
    }

    static Modal allianceModal(Game game) {
        List<SelectOption> options = game.getRealPlayers().stream()
                .limit(SelectMenu.OPTIONS_MAX_AMOUNT)
                .map(player -> SelectOption.of(StringUtils.left(player.getUserName(), 100), player.getUserID())
                        .withDescription(StringUtils.abbreviate(factionName(player.getFaction()), 100)))
                .toList();
        return Modal.create(ALLIANCE_RESOLVE, "Pair Alliance Members")
                .addComponents(
                        TextDisplay.of("Same as `/player add_alliance_member`: raises VP to 14 if still default,"
                                + " shares eligible commanders and removes both Alliance promissory notes."),
                        Label.of("First player", allianceSelect("first", options)),
                        Label.of("Second player", allianceSelect("second", options)))
                .build();
    }

    private static StringSelectMenu allianceSelect(String id, List<SelectOption> options) {
        return StringSelectMenu.create(id)
                .addOptions(options)
                .setRequiredRange(1, 1)
                .build();
    }

    @ButtonHandler(ALLIANCE_MODAL)
    static void openAllianceModal(ButtonInteractionEvent event, Game game) {
        event.replyModal(allianceModal(game)).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(ALLIANCE_RESOLVE)
    static void resolveAllianceModal(ModalInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        Player first = game.getPlayer(firstValue(event.getValue("first")));
        Player second = game.getPlayer(firstValue(event.getValue("second")));
        if (first == null || second == null || first == second) {
            FowSetupPanel.show(event, game, "Pick two different players. Nobody was paired.");
            return;
        }
        AddAllianceMember.makeAlliancePartners(first, second, event, game);
        FowSetupPanel.show(event, game, first.getUserName() + " and " + second.getUserName() + " are now allies.");
    }

    private static String firstValue(ModalMapping mapping) {
        if (mapping == null || mapping.getAsStringList().isEmpty()) return "";
        return mapping.getAsStringList().getFirst();
    }

    @ButtonHandler(RANDOMIZE)
    static void randomizePositions(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupPanel.show(event, game, FowSetupFactionService.placeAtRandomPositions(event, game));
    }
}
