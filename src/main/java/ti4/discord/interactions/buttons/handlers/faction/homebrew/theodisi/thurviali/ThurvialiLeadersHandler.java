package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.thurviali;

import java.util.ArrayList;
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
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class ThurvialiLeadersHandler {
    private static final String USE_MENDING_LIGHT = "useMendingLight_";
    private static final String THURVIALI_HERO_TARGET = "thurvialiHeroTarget_";
    private static final String THURVIALI_HERO_PRODUCER = "thurvialiHeroProducer_";
    private static final String THURVIALI_HERO_BEGIN_PLACEMENT = "thurvialiHeroBeginPlacement";
    private static final String THURVIALI_HERO_PLACEMENT_SYSTEM = "thurvialiHeroPlacementSystem_";
    private static final String THURVIALI_HERO_FINISH = "thurvialiHeroFinish";
    private static final String HERO_SELECTED_TARGETS = "thurvialiHeroSelectedTargets_";
    private static final String HERO_PENDING_OWNER = "thurvialiHeroPendingOwner_";
    private static final String HERO_OTHER_PRODUCTION = "thurvialiHeroOtherProduction_";
    private static final String HERO_PLACEMENT_BUDGET = "thurvialiHeroPlacementBudget_";
    private static final String HERO_PLACEMENT_SYSTEM = "thurvialiHeroPlacementSystem_";

    public static void startThurvialiHero(GenericInteractionCreateEvent event, Game game, Player player) {
        clearThurvialiHeroState(game, player);

        List<Button> buttons = getThurvialiHeroTargetButtons(game, player);
        buttons.add(Buttons.red(player.factionButtonChecker() + THURVIALI_HERO_BEGIN_PLACEMENT, "Place Units"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", choose each player who will use the PRODUCTION value of one of their units. "
                        + "Their combined production cost is reduced by 4. When they have finished producing, press "
                        + "**Place Units** to place your units.",
                buttons);
    }

    @ButtonHandler(THURVIALI_HERO_TARGET)
    public static void selectThurvialiHeroTarget(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getThurvialiHeroTargetButtons(game, player);
        String message = player.getRepresentation()
                + ", choose a player who will use the PRODUCTION value of one of their units.";

        String prefix = player.factionButtonChecker() + THURVIALI_HERO_TARGET;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }

        String targetFaction = buttonID.substring(THURVIALI_HERO_TARGET.length());
        Player target = game.getPlayerFromColorOrFaction(targetFaction);
        if (target == null
                || target == player
                || game.getStoredValue(HERO_SELECTED_TARGETS + player.getFaction())
                        .contains("|" + target.getFaction() + "|")
                || getThurvialiHeroProducerButtons(game, player, target).isEmpty()) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        game.setStoredValue(
                HERO_SELECTED_TARGETS + player.getFaction(),
                game.getStoredValue(HERO_SELECTED_TARGETS + player.getFaction()) + target.getFaction() + "|");
        game.setStoredValue(HERO_PENDING_OWNER + target.getFaction(), player.getFaction());

        sendThurvialiHeroProducerButtons(event, game, player, target);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(THURVIALI_HERO_PRODUCER)
    public static void selectThurvialiHeroProducer(
            ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String ownerFaction = game.getStoredValue(HERO_PENDING_OWNER + target.getFaction());
        Player owner = game.getPlayerFromColorOrFaction(ownerFaction);
        String[] payload = buttonID.substring(THURVIALI_HERO_PRODUCER.length()).split("\\|", -1);

        if (owner == null || payload.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(payload[0]);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(payload[1]);
        UnitKey unitKey = holder == null
                ? null
                : holder.getUnitKeysForPlayer(target).stream()
                        .filter(key -> key.asyncID().equals(payload[2]))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = unitKey == null ? null : target.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);

        if (tile == null || holder == null || unitKey == null || unit == null || unit.getProductionValue() < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(
                HERO_OTHER_PRODUCTION + target.getFaction(),
                owner.getFaction() + "|" + tile.getPosition() + "|" + unit.getProductionValue());
        game.removeStoredValue(HERO_PENDING_OWNER + target.getFaction());

        List<Button> productionButtons =
                Helper.getPlaceUnitButtons(event, target, game, tile, "thurvialiHeroProduction", "place");

        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentation()
                        + ", use PRODUCTION " + Helper.getProductionValueOfUnitHolder(target, game, tile, holder, true)
                        + " from "
                        + unit.getName()
                        + " in "
                        + tile.getRepresentationForButtons(game, target)
                        + ". Your combined cost is reduced by 4 due to _Synergistic Radiance_.",
                productionButtons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(THURVIALI_HERO_BEGIN_PLACEMENT)
    public static void beginThurvialiHeroPlacement(ButtonInteractionEvent event, Game game, Player player) {
        if (getThurvialiHeroPlacementBudget(game, player) < 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "No units have finished producing with _Synergistic Radiance_ yet.");
            return;
        }

        sendThurvialiHeroPlacementSystemButtons(event, game, player);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(THURVIALI_HERO_PLACEMENT_SYSTEM)
    public static void selectThurvialiHeroPlacementSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getThurvialiHeroPlacementSystemButtons(game, player);
        String message = player.getRepresentation()
                + ", choose a system containing your ships in which to place units. Remaining cost: "
                + getThurvialiHeroPlacementBudget(game, player)
                + ".";

        String prefix = player.factionButtonChecker() + THURVIALI_HERO_PLACEMENT_SYSTEM;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }

        String position = buttonID.substring(THURVIALI_HERO_PLACEMENT_SYSTEM.length());
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !FoWHelper.playerHasShipsInSystem(player, tile)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        game.setStoredValue(HERO_PLACEMENT_SYSTEM + player.getFaction(), position);

        List<Button> placementButtons =
                Helper.getPlaceUnitButtons(event, player, game, tile, "thurvialiHeroPlacement", "place");

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", place units in "
                        + tile.getRepresentationForButtons(game, player)
                        + ". Remaining combined cost: "
                        + getThurvialiHeroPlacementBudget(game, player)
                        + ".",
                placementButtons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(THURVIALI_HERO_FINISH)
    public static void finishThurvialiHero(ButtonInteractionEvent event, Game game, Player player) {
        clearThurvialiHeroState(game, player);
        ButtonHelper.deleteMessage(event);
    }

    public static int applyThurvialiHeroProductionDiscount(Game game, Player player, int cost) {
        String context = game.getStoredValue(HERO_OTHER_PRODUCTION + player.getFaction());
        if (context.isBlank()) {
            return cost;
        }

        String[] payload = context.split("\\|", -1);
        Player owner = payload.length < 1 ? null : game.getPlayerFromColorOrFaction(payload[0]);
        if (owner == null) {
            game.removeStoredValue(HERO_OTHER_PRODUCTION + player.getFaction());
            return cost;
        }

        int placementBudget = getThurvialiHeroPlacementBudget(game, owner);
        game.setStoredValue(HERO_PLACEMENT_BUDGET + owner.getFaction(), Integer.toString(placementBudget + cost));
        game.removeStoredValue(HERO_OTHER_PRODUCTION + player.getFaction());

        return Math.max(0, cost - 4);
    }

    public static boolean hasThurvialiHeroProductionDiscount(Game game, Player player) {
        String context = game.getStoredValue(HERO_OTHER_PRODUCTION + player.getFaction());
        if (context.isBlank()) {
            return false;
        }
        String[] payload = context.split("\\|", -1);
        return payload.length > 0 && game.getPlayerFromColorOrFaction(payload[0]) != null;
    }

    public static void finishThurvialiHeroPlacement(ButtonInteractionEvent event, Game game, Player player) {
        int spent = Helper.calculateCostOfProducedUnits(player, game, true);
        int remaining = getThurvialiHeroPlacementBudget(game, player) - spent;

        player.resetProducedUnits();
        game.removeStoredValue(HERO_PLACEMENT_SYSTEM + player.getFaction());

        if (remaining < 0) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Those units exceed the remaining _Synergistic Radiance_ placement cost.");
            return;
        }

        if (remaining == 0) {
            clearThurvialiHeroState(game, player);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " has finished placing units with _Synergistic Radiance_.");
            return;
        }

        game.setStoredValue(HERO_PLACEMENT_BUDGET + player.getFaction(), Integer.toString(remaining));
        sendThurvialiHeroPlacementSystemButtons(event, game, player);
    }

    private static List<Button> getThurvialiHeroTargetButtons(Game game, Player owner) {
        List<Button> buttons = new ArrayList<>();
        String selectedTargets = game.getStoredValue(HERO_SELECTED_TARGETS + owner.getFaction());

        for (Player target : game.getRealPlayersExcludingThis(owner)) {
            if (selectedTargets.contains("|" + target.getFaction() + "|")
                    || getThurvialiHeroProducerButtons(game, owner, target).isEmpty()) {
                continue;
            }

            buttons.add(Buttons.green(
                    owner.factionButtonChecker() + THURVIALI_HERO_TARGET + target.getFaction(),
                    "Choose " + target.getColor(),
                    target.getFactionEmojiOrColor()));
        }

        return buttons;
    }

    private static void sendThurvialiHeroProducerButtons(
            ButtonInteractionEvent event, Game game, Player owner, Player target) {
        List<Button> buttons = getThurvialiHeroProducerButtons(game, owner, target);
        String message = target.getRepresentation()
                + ", choose one of your units whose PRODUCTION value you will use for _Synergistic Radiance_.";

        String prefix = target.factionButtonChecker() + THURVIALI_HERO_PRODUCER;
        List<Button> paginatedButtons = NewStuffHelper.buttonPagination(buttons, prefix, 0);
        if (buttons.size() <= 24) {
            paginatedButtons = new ArrayList<>(buttons);
        }

        MessageHelper.sendMessageToChannelWithButtons(target.getCorrectChannel(), message, paginatedButtons);
    }

    private static List<Button> getThurvialiHeroProducerButtons(Game game, Player owner, Player target) {
        List<Button> buttons = new ArrayList<>();

        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                for (UnitKey unitKey : holder.getUnitKeysForPlayer(target)) {
                    UnitModel unit = target.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
                    if (unit == null || unit.getProductionValue() < 1) {
                        continue;
                    }

                    buttons.add(Buttons.green(
                            target.factionButtonChecker()
                                    + THURVIALI_HERO_PRODUCER
                                    + tile.getPosition()
                                    + "|"
                                    + holder.getName()
                                    + "|"
                                    + unitKey.asyncID(),
                            "Use " + unit.getName() + " in " + tile.getRepresentationForButtons(game, target)));
                }
            }
        }

        return buttons;
    }

    private static void sendThurvialiHeroPlacementSystemButtons(
            ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getThurvialiHeroPlacementSystemButtons(game, player);
        buttons.add(Buttons.red(player.factionButtonChecker() + THURVIALI_HERO_FINISH, "Done"));

        String message = player.getRepresentation()
                + ", choose a system containing your ships in which to place units. Remaining combined cost: "
                + getThurvialiHeroPlacementBudget(game, player)
                + ".";

        String prefix = player.factionButtonChecker() + THURVIALI_HERO_PLACEMENT_SYSTEM;
        List<Button> paginatedButtons = NewStuffHelper.buttonPagination(buttons, prefix, 0);
        if (buttons.size() <= 24) {
            paginatedButtons = new ArrayList<>(buttons);
        }

        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, paginatedButtons);
    }

    private static List<Button> getThurvialiHeroPlacementSystemButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();

        for (Tile tile : game.getTileMap().values()) {
            if (!FoWHelper.playerHasShipsInSystem(player, tile)) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + THURVIALI_HERO_PLACEMENT_SYSTEM + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }

        return buttons;
    }

    private static int getThurvialiHeroPlacementBudget(Game game, Player player) {
        try {
            return Integer.parseInt(game.getStoredValue(HERO_PLACEMENT_BUDGET + player.getFaction()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void clearThurvialiHeroState(Game game, Player owner) {
        game.removeStoredValue(HERO_SELECTED_TARGETS + owner.getFaction());
        game.removeStoredValue(HERO_PLACEMENT_BUDGET + owner.getFaction());
        game.removeStoredValue(HERO_PLACEMENT_SYSTEM + owner.getFaction());

        for (Player player : game.getRealPlayers()) {
            if (game.getStoredValue(HERO_PENDING_OWNER + player.getFaction()).equals(owner.getFaction())) {
                game.removeStoredValue(HERO_PENDING_OWNER + player.getFaction());
            }
            if (game.getStoredValue(HERO_OTHER_PRODUCTION + player.getFaction()).startsWith(owner.getFaction() + "|")) {
                game.removeStoredValue(HERO_OTHER_PRODUCTION + player.getFaction());
            }
        }
    }

    public static void offerMendingLightButtons(Game game, Tile tile) {
        for (Player owner : game.getRealPlayers()) {
            int structures = tile.getUnitHolders().values().stream()
                    .mapToInt(holder -> holder.countPlayersUnitsWithModelCondition(owner, UnitModel::getIsStructure))
                    .sum();

            if (!game.playerHasLeaderUnlockedOrAlliance(owner, "thurvialicommander")
                    || structures < 1
                    || FoWHelper.otherPlayersHaveShipsInSystem(owner, tile, game)) {
                continue;
            }

            Button button = Buttons.red(
                    owner.factionButtonChecker() + USE_MENDING_LIGHT + tile.getPosition(), "Use Mending Light");

            MessageHelper.sendMessageToChannelWithButton(
                    owner.getCorrectChannel(),
                    owner.getRepresentationNoPing()
                            + ", you may place "
                            + structures
                            + " fighter"
                            + (structures == 1 ? "" : "s")
                            + " with _Mending Light_.",
                    button);
        }
    }

    @ButtonHandler(USE_MENDING_LIGHT)
    public static void useMendingLight(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(USE_MENDING_LIGHT.length()));
        int structures = tile == null
                ? 0
                : tile.getUnitHolders().values().stream()
                        .mapToInt(
                                holder -> holder.countPlayersUnitsWithModelCondition(player, UnitModel::getIsStructure))
                        .sum();

        if (tile == null
                || !game.playerHasLeaderUnlockedOrAlliance(player, "thurvialicommander")
                || structures < 1
                || FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), structures + " ff");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " placed "
                        + structures
                        + " fighter"
                        + (structures == 1 ? "" : "s")
                        + " with _Mending Light_.");
        ButtonHelper.deleteMessage(event);
    }
}
