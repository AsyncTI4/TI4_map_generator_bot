package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.DSHelperBreakthroughs;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.UnitModel;
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.TechEmojis;

@UtilityClass
public class OblivionTechHandler {
    private static final String MM = "thobliviong";
    private static final String PLAY_DISCARD_COMPONENT_AC = "playMirroredMemoriesAC_";
    private static final String OBLIVION_CANNON = "thoblivionr";
    private static final String USE_OBLIVION_CANNON = "useOblivionCannon_";
    private static final String PURGE_OBLIVION_CANNON_SHIP = "purgeOblivionCannonShip_";

    public static void addOblivionCannonButton(
            List<Button> buttons, Game game, Player player, Player opponent, Tile tile, boolean spaceCombat) {
        if (!spaceCombat
                || game == null
                || player == null
                || opponent == null
                || tile == null
                || !player.hasTech(OBLIVION_CANNON)
                || getOblivionCannonShips(player, tile).isEmpty()) {
            return;
        }

        buttons.add(Buttons.red(
                player.factionButtonChecker() + USE_OBLIVION_CANNON + tile.getPosition() + "|" + opponent.getFaction(),
                "Use Oblivion Cannon",
                TechEmojis.WarfareTech));
    }

    @ButtonHandler(USE_OBLIVION_CANNON)
    public static void chooseOblivionCannonShip(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(USE_OBLIVION_CANNON.length()).split("\\|", 2);
        Tile tile = payload.length == 2 && game != null ? game.getTileByPosition(payload[0]) : null;
        Player opponent = payload.length == 2 && game != null ? game.getPlayerFromColorOrFaction(payload[1]) : null;
        if (player == null
                || tile == null
                || opponent == null
                || opponent == player
                || !player.hasTech(OBLIVION_CANNON)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (UnitKey unitKey : getOblivionCannonShips(player, tile)) {
            UnitModel unit = player.getUnitFromUnitKey(unitKey);
            int hits = (int) Math.ceil(unit.getCost() / 2.0);
            for (UnitState state : tile.getSpaceUnitHolder().getNonZeroUnitStates(unitKey)) {
                String stateText = state == UnitState.none ? "" : state.humanDescr() + " ";
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + PURGE_OBLIVION_CANNON_SHIP + tile.getPosition() + "|"
                                + opponent.getFaction() + "|" + unitKey.asyncID() + "|" + state,
                        "Purge 1 " + stateText + unitKey.humanReadableName() + " (" + hits + " Hit"
                                + (hits == 1 ? "" : "s") + ")",
                        unitKey.unitEmoji()));
            }
        }

        if (buttons.isEmpty()) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose the non-fighter ship to purge using _Oblivion Cannon_.",
                buttons);
    }

    @ButtonHandler(PURGE_OBLIVION_CANNON_SHIP)
    public static void resolveOblivionCannon(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(PURGE_OBLIVION_CANNON_SHIP.length()).split("\\|", 4);
        Tile tile = payload.length == 4 && game != null ? game.getTileByPosition(payload[0]) : null;
        Player opponent = payload.length == 4 && game != null ? game.getPlayerFromColorOrFaction(payload[1]) : null;
        UnitKey unitKey =
                payload.length == 4 && player != null ? Mapper.getUnitKey(payload[2], player.getColor()) : null;
        UnitState state = payload.length == 4 ? Units.findUnitState(payload[3]) : null;
        UnitHolder space = tile == null ? null : tile.getSpaceUnitHolder();
        UnitModel unit = unitKey == null ? null : player.getUnitFromUnitKey(unitKey);
        if (player == null
                || opponent == null
                || opponent == player
                || space == null
                || unit == null
                || state == null
                || !player.hasTech(OBLIVION_CANNON)
                || !unit.getIsShip()
                || unitKey.unitType() == UnitType.Fighter
                || unit.getCost() > 4
                || space.getUnitCountForState(unitKey, state) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int hits = (int) Math.ceil(unit.getCost() / 2.0);
        space.removeUnit(unitKey, 1, state);
        player.setUnitCap(unitKey.asyncID(), Math.max(0, player.getUnitCap(unitKey.asyncID()) - 1));
        DSHelperBreakthroughs.doLanefirBtCheck(game, player);
        OblivionUnitHandler.doOblivionMechCheck(game, player);

        List<Button> hitButtons = List.of(
                Buttons.green(
                        opponent.factionButtonChecker() + "autoAssignSpaceHits_" + tile.getPosition() + "_" + hits,
                        "Auto-assign " + hits + " Hit" + (hits == 1 ? "" : "s")),
                Buttons.red(
                        opponent.factionButtonChecker() + "getDamageButtons_" + tile.getPosition()
                                + "deleteThis_spacecombat",
                        "Manually Assign " + hits + " Hit" + (hits == 1 ? "" : "s")));

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " purged 1 " + unitKey.humanReadableName()
                        + " using _Oblivion Cannon_ to produce " + hits + " hit" + (hits == 1 ? "" : "s") + ".\n"
                        + opponent.getRepresentation() + ", please assign the produced hits.",
                hitButtons);
    }

    private static List<UnitKey> getOblivionCannonShips(Player player, Tile tile) {
        if (player == null || tile == null) {
            return List.of();
        }
        return tile.getSpaceUnitHolder().getUnitKeys().stream()
                .filter(player::unitBelongsToPlayer)
                .filter(unitKey -> unitKey.unitType() != UnitType.Fighter)
                .filter(unitKey -> {
                    UnitModel unit = player.getUnitFromUnitKey(unitKey);
                    return unit != null && unit.getIsShip() && unit.getCost() <= 4;
                })
                .toList();
    }

    public static boolean canUseMirroredMemories(Game game, Player player) {
        return game != null
                && player != null
                && player.hasTechReady(MM)
                && !isCensured(game, player)
                && hasEligibleComponentAction(game, player);
    }

    public static void offerACPlayFromDiscardButtons(GenericInteractionCreateEvent event, Player player, Game game) {
        if (event == null
                || player == null
                || !player.hasTech(MM)
                || isCensured(game, player)
                || !hasEligibleComponentAction(game, player)) {
            return;
        }

        List<Button> buttons = getDiscardComponentActionButtons(game, player);
        String buttonPrefix = player.factionButtonChecker() + PLAY_DISCARD_COMPONENT_AC;
        String message = player.getRepresentation()
                + ", please choose an action card with a component action to play and purge using _Mirrored Memories_.";
        List<Button> displayedButtons = buttons.size() <= 25
                ? buttons
                : NewStuffHelper.buttonPagination(buttons, null, buttonPrefix, 24, 0, true);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, displayedButtons);
    }

    @ButtonHandler(PLAY_DISCARD_COMPONENT_AC)
    public static void playDiscardComponentAction(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null
                || player == null
                || player != game.getActivePlayer()
                || !player.hasTech(MM)
                || isCensured(game, player)
                || !player.getExhaustedTechs().contains(MM)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = getDiscardComponentActionButtons(game, player);
        String buttonPrefix = player.factionButtonChecker() + PLAY_DISCARD_COMPONENT_AC;
        String message = player.getRepresentation()
                + ", please choose an action card with a component action to play and purge using _Mirrored Memories_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }

        int acIndex;
        try {
            acIndex = Integer.parseInt(buttonID.substring(PLAY_DISCARD_COMPONENT_AC.length()));
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String acId = ActionCardHelper.getDiscardedAcID(game, acIndex);
        if (!isEligibleComponentAction(game, player, acId) || !game.pickActionCard(player.getUserID(), acIndex)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "That action card is no longer available.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Integer handIndex = player.getActionCards().get(acId);
        if (handIndex == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not prepare that action card to play.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        // Mark it before the normal resolver offers response windows, including Reverse Engineer.
        game.getDiscardACStatus().put(acId, ActionCardHelper.ACStatus.purged);
        String error =
                ActionCardHelper.playAC(event, game, player, String.valueOf(handIndex), event.getMessageChannel());
        if (error != null) {
            player.removeActionCard(handIndex);
            game.getDiscardActionCards().put(acId, acIndex);
            game.getDiscardACStatus().remove(acId);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), error);
        } else {
            DSHelperBreakthroughs.doLanefirBtCheck(game, player);
            OblivionUnitHandler.doOblivionMechCheck(game, player);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getDiscardComponentActionButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        game.getDiscardActionCards().entrySet().stream()
                .filter(entry -> isEligibleComponentAction(game, player, entry.getKey()))
                .sorted(Comparator.comparing(
                        entry -> Mapper.getActionCard(entry.getKey()).getName()))
                .forEach(entry -> {
                    ActionCardModel actionCard = Mapper.getActionCard(entry.getKey());
                    buttons.add(Buttons.red(
                            player.factionButtonChecker() + PLAY_DISCARD_COMPONENT_AC + entry.getValue(),
                            "(" + entry.getValue() + ") " + actionCard.getName(),
                            CardEmojis.getACEmoji(game)));
                });
        return buttons;
    }

    private static boolean isEligibleComponentAction(Game game, Player player, String acId) {
        if (game == null
                || acId == null
                || game.getDiscardACStatus().get(acId) != null
                || !ActionCardHelper.isDiscardVisible(game, player, acId)) {
            return false;
        }
        ActionCardModel actionCard = Mapper.getActionCard(acId);
        return actionCard != null && "action".equalsIgnoreCase(actionCard.getWindow());
    }

    private static boolean hasEligibleComponentAction(Game game, Player player) {
        return game != null
                && game.getDiscardActionCards().keySet().stream()
                        .anyMatch(acId -> isEligibleComponentAction(game, player, acId));
    }

    private static boolean isCensured(Game game, Player player) {
        return IsPlayerElectedService.isPlayerElected(game, player, "censure")
                || IsPlayerElectedService.isPlayerElected(game, player, "absol_censure");
    }
}
