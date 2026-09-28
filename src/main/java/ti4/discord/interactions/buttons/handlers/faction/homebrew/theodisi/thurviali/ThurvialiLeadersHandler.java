package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Thurviali;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.fow.PlanetTargetService;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.MoveUnitService;

@UtilityClass
public class ThurvialiLeadersHandler {
    private static final String USE_MENDING_LIGHT = "useMendingLight_";
    private static final String USE_HOPE_ON_SELF = "useThurvialiAgentOnSelf";
    private static final String USE_HOPE_ON_OTHER = "useThurvialiAgentOnOther";
    private static final String SELECT_HOPE_TARGET = "selectThurvialiAgentTarget_";
    private static final String SELECT_HOPE_SOURCE = "selectThurvialiAgentSource_";
    private static final String SELECT_HOPE_DESTINATION = "selectThurvialiAgentDestination_";
    private static final String HOPE_SELECTION_MODE = "thurvialiHopeSelectionMode_";
    private static final String HOPE_FIRST_TARGET = "thurvialiHopeFirstTarget_";
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

    public static Button getHopeStartTurnButton(Game game, Player player) {
        if (!player.hasUnexhaustedLeader("thurvialiagent")
                || game.getRealPlayersExcludingThis(player).stream()
                        .noneMatch(target -> canUseHopeBetween(game, player, target))) {
            return null;
        }

        return Buttons.gray(
                player.factionButtonChecker() + USE_HOPE_ON_SELF,
                "Use The Hope of the Cosmos",
                FactionEmojis.thurviali);
    }

    public static Button getHopeCardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_HOPE_ON_OTHER,
                "Use Thurviali Agent on Two Players",
                FactionEmojis.thurviali);
    }

    @ButtonHandler(USE_HOPE_ON_SELF)
    public static void useHopeOnSelf(ButtonInteractionEvent event, Game game, Player player) {
        game.setStoredValue(HOPE_SELECTION_MODE + player.getFaction(), "owner");
        sendHopeTargetButtons(event, game, player, null);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(USE_HOPE_ON_OTHER)
    public static void useHopeOnOther(ButtonInteractionEvent event, Game game, Player player) {
        game.setStoredValue(HOPE_SELECTION_MODE + player.getFaction(), "other");
        game.removeStoredValue(HOPE_FIRST_TARGET + player.getFaction());
        sendHopeTargetButtons(event, game, player, null);
        ButtonHelper.deleteTheOneButton(event);
    }

    private static void sendHopeTargetButtons(
            ButtonInteractionEvent event, Game game, Player agentOwner, Player firstTarget) {
        if (!agentOwner.hasUnexhaustedLeader("thurvialiagent")) {
            return;
        }

        List<Button> buttons = getHopeTargetButtons(game, agentOwner, firstTarget);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "No eligible pair of players can use **The Hope of the Cosmos**.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                agentOwner.getCorrectChannel(),
                agentOwner.getRepresentationNoPing()
                        + (firstTarget == null
                                ? ", choose the first player for **The Hope of the Cosmos**."
                                : ", choose the second player for **The Hope of the Cosmos**."),
                buttons);
    }

    private static List<Button> getHopeTargetButtons(Game game, Player agentOwner, Player firstTarget) {
        List<Button> buttons = new ArrayList<>();
        boolean ownerParticipates = "owner".equals(game.getStoredValue(HOPE_SELECTION_MODE + agentOwner.getFaction()));

        for (Player target : game.getRealPlayers()) {
            if ((ownerParticipates && target == agentOwner)
                    || target == firstTarget
                    || (ownerParticipates
                            ? !canUseHopeBetween(game, agentOwner, target)
                            : firstTarget == null
                                    ? game.getRealPlayers().stream()
                                            .noneMatch(
                                                    other -> other != target && canUseHopeBetween(game, target, other))
                                    : !canUseHopeBetween(game, firstTarget, target))) {
                continue;
            }

            buttons.add(Buttons.gray(
                    agentOwner.factionButtonChecker() + SELECT_HOPE_TARGET + target.getFaction(),
                    "Choose " + target.getColor(),
                    target.getFactionEmojiOrColor()));
        }

        return buttons;
    }

    @ButtonHandler(SELECT_HOPE_TARGET)
    public static void selectHopeTarget(ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_HOPE_TARGET.length()));
        Leader agent = agentOwner.getLeader("thurvialiagent").orElse(null);
        String mode = game.getStoredValue(HOPE_SELECTION_MODE + agentOwner.getFaction());
        Player firstTarget =
                game.getPlayerFromColorOrFaction(game.getStoredValue(HOPE_FIRST_TARGET + agentOwner.getFaction()));

        if (target == null
                || ("owner".equals(mode) && target == agentOwner)
                || agent == null
                || !agentOwner.hasUnexhaustedLeader("thurvialiagent")) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if ("other".equals(mode) && firstTarget == null) {
            game.setStoredValue(HOPE_FIRST_TARGET + agentOwner.getFaction(), target.getFaction());
            sendHopeTargetButtons(event, game, agentOwner, target);
            ButtonHelper.deleteMessage(event);
            return;
        }

        Player firstMover = "owner".equals(mode) ? agentOwner : firstTarget;
        if (firstMover == null || !canUseHopeBetween(game, firstMover, target)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ExhaustLeaderService.exhaustLeader(game, agentOwner, agent);
        sendHopeMoveButtons(game, firstMover, target, agentOwner);
        sendHopeMoveButtons(game, target, firstMover, agentOwner);
        game.removeStoredValue(HOPE_SELECTION_MODE + agentOwner.getFaction());
        game.removeStoredValue(HOPE_FIRST_TARGET + agentOwner.getFaction());
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_HOPE_SOURCE)
    public static void selectHopeSource(ButtonInteractionEvent event, Game game, Player mover, String buttonID) {
        String[] payload = buttonID.substring(SELECT_HOPE_SOURCE.length()).split("\\|", 2);
        Player destinationOwner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Planet source = payload.length == 2 ? game.getUnitHolderFromPlanet(payload[1]) : null;

        if (destinationOwner == null
                || source == null
                || !mover.getPlanets().contains(source.getName())
                || source.getUnitCount(UnitType.Infantry, mover) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> destinationButtons = getHopeDestinationButtons(game, destinationOwner, mover, source);
        if (destinationButtons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.editMessageWithButtons(
                event,
                mover.getRepresentationNoPing()
                        + ", choose a non-home planet controlled by "
                        + destinationOwner.getRepresentationNoPing()
                        + " that contains their ground forces.",
                destinationButtons);
    }

    @ButtonHandler(SELECT_HOPE_DESTINATION)
    public static void selectHopeDestination(ButtonInteractionEvent event, Game game, Player mover, String buttonID) {
        String[] payload = buttonID.substring(SELECT_HOPE_DESTINATION.length()).split("\\|", 3);
        Player destinationOwner = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Planet source = payload.length == 3 ? game.getUnitHolderFromPlanet(payload[1]) : null;
        Planet destination = payload.length == 3 ? game.getUnitHolderFromPlanet(payload[2]) : null;
        Tile sourceTile = source == null ? null : game.getTileFromPlanet(source.getName());
        Tile destinationTile = destination == null ? null : game.getTileFromPlanet(destination.getName());

        if (destinationOwner == null
                || source == null
                || destination == null
                || sourceTile == null
                || destinationTile == null
                || !mover.getPlanets().contains(source.getName())
                || source.getUnitCount(UnitType.Infantry, mover) < 1
                || !destinationOwner.getPlanets().contains(destination.getName())
                || destinationTile.isHomeSystem(game)
                || !destination.hasGroundForces(destinationOwner)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String coexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            MoveUnitService.moveUnits(
                    event,
                    sourceTile,
                    game,
                    mover.getColor(),
                    "1 gf " + source.getName(),
                    destinationTile,
                    destination.getName());
        } finally {
            if (coexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", coexistenceFlag);
            }
        }

        ThurvialiAbilityHandler.checkRadiantGrafting(game);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                mover.getRepresentationNoPing()
                        + " moved 1 infantry from "
                        + Helper.getPlanetRepresentation(source.getName(), game)
                        + " into coexistence on "
                        + Helper.getPlanetRepresentation(destination.getName(), game)
                        + " with **The Hope of the Cosmos**.");
        ButtonHelper.deleteMessage(event);
    }

    private static void sendHopeMoveButtons(Game game, Player mover, Player destinationOwner, Player agentOwner) {
        List<Button> buttons = getHopeSourceButtons(game, mover, destinationOwner);
        MessageHelper.sendMessageToChannelWithButtons(
                mover.getCorrectChannel(),
                mover.getRepresentation()
                        + ", "
                        + agentOwner.getRepresentationNoPing()
                        + " exhausted **The Hope of the Cosmos**. Choose 1 infantry to move into coexistence on a planet controlled by "
                        + destinationOwner.getRepresentationNoPing()
                        + ".",
                buttons);
    }

    private static List<Button> getHopeSourceButtons(Game game, Player mover, Player destinationOwner) {
        List<String> planetNames = mover.getPlanets().stream()
                .filter(planetName -> {
                    Planet planet = game.getUnitHolderFromPlanet(planetName);
                    return planet != null && planet.getUnitCount(UnitType.Infantry, mover) >= 1;
                })
                .sorted()
                .toList();
        List<Button> buttons = new ArrayList<>();
        for (String planetName : planetNames) {
            buttons.add(Buttons.green(
                    mover.factionButtonChecker() + SELECT_HOPE_SOURCE + destinationOwner.getFaction() + "|"
                            + planetName,
                    "Move Infantry from " + Helper.getPlanetRepresentation(planetName, game),
                    FactionEmojis.thurviali));
        }
        return buttons;
    }

    private static List<Button> getHopeDestinationButtons(
            Game game, Player destinationOwner, Player mover, Planet source) {
        List<String> candidatePlanets = PlanetTargetService.knownPlanetIds(game, mover, java.util.Set.of()).stream()
                .filter(destinationOwner.getPlanets()::contains)
                .sorted()
                .toList();
        List<Button> buttons = new ArrayList<>();
        for (String planetName : candidatePlanets) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            Tile tile = planet == null ? null : game.getTileFromPlanet(planetName);
            if (planet == null
                    || tile == null
                    || tile.isHomeSystem(game)
                    || !planet.hasGroundForces(destinationOwner)) {
                continue;
            }
            buttons.add(Buttons.green(
                    mover.factionButtonChecker()
                            + SELECT_HOPE_DESTINATION
                            + destinationOwner.getFaction()
                            + "|"
                            + source.getName()
                            + "|"
                            + planetName,
                    "Move to " + Helper.getPlanetRepresentation(planetName, game),
                    FactionEmojis.thurviali));
        }
        return buttons;
    }

    private static boolean canUseHopeBetween(Game game, Player firstPlayer, Player secondPlayer) {
        return hasHopeInfantry(game, firstPlayer)
                && hasHopeInfantry(game, secondPlayer)
                && hasHopeDestination(game, firstPlayer)
                && hasHopeDestination(game, secondPlayer);
    }

    private static boolean hasHopeDestination(Game game, Player player) {
        return player.getPlanets().stream().map(game::getUnitHolderFromPlanet).anyMatch(planet -> {
            Tile tile = planet == null ? null : game.getTileFromPlanet(planet.getName());
            return tile != null && !tile.isHomeSystem(game) && planet.hasGroundForces(player);
        });
    }

    private static boolean hasHopeInfantry(Game game, Player player) {
        return player.getPlanets().stream()
                .map(game::getUnitHolderFromPlanet)
                .anyMatch(planet -> planet != null && planet.getUnitCount(UnitType.Infantry, player) > 0);
    }
}
