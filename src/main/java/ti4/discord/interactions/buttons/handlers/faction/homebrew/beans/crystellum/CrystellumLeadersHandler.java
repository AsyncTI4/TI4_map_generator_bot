package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class CrystellumLeadersHandler {
    private static final String AGENT_ID = "crystellumagent";
    private static final String USE_AGENT = "useCrystellumAgent_";
    private static final String SELECT_OTHER_TARGET = "selectOtherCrystellumAgentTarget";
    private static final String SELECT_TARGET = "selectCrystellumAgentTarget_";
    private static final String PLACE_FIGHTERS = "placeCrystellumAgentFighters_";
    private static final String HERO_BUDGET = "crystellumHeroBudget_";
    private static final String HERO_RETURN = "crystellumHeroReturn_";
    private static final String HERO_SYSTEM = "crystellumHeroSystem_";
    private static final String HERO_PLACE = "crystellumHeroPlace_";
    private static final String HERO_DONE = "crystellumHeroDone";

    public static List<Button> getCrystellumCommanderFighterButtons(
            Player player, Tile productionTile, Game game, String placePrefix) {
        List<Button> buttons = new ArrayList<>();
        if (!game.playerHasLeaderUnlockedOrAlliance(player, "crystellumcommander")) {
            return buttons;
        }

        for (Tile tile : game.getTileMap().values()) {
            if (tile.getPosition().equalsIgnoreCase(productionTile.getPosition())
                    || !tile.hasPlayerCC(player)
                    || FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + placePrefix + "_fighter_" + tile.getPosition(),
                    "Produce 1 Fighter in " + tile.getRepresentationForButtons(game, player),
                    FactionEmojis.crystellum));
        }

        return buttons;
    }

    public static void addCrystellumAgentEndTurnButton(List<Button> buttons, Game game, Player player) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (!player.hasUnexhaustedLeader(AGENT_ID)
                || activeSystem == null
                || ButtonHelper.checkNumberNonFighterShips(player, activeSystem, false) < 1) {
            return;
        }
        buttons.add(Buttons.green(
                player.factionButtonChecker() + USE_AGENT + player.getFaction(),
                "Use Crystellum Agent",
                FactionEmojis.crystellum));
    }

    public static void addCrystellumAgentCardsInfoButton(List<Button> buttons, Player player) {
        if (player.hasUnexhaustedLeader(AGENT_ID)) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + SELECT_OTHER_TARGET,
                    "Use Crystellum Agent",
                    FactionEmojis.crystellum));
        }
    }

    @ButtonHandler(USE_AGENT)
    public static void useCrystellumAgent(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace(USE_AGENT, ""));
        if (target == null || !target.getFaction().equals(player.getFaction())) {
            return;
        }
        sendFighterPlacementButtons(event, game, player, target);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SELECT_OTHER_TARGET)
    public static void selectOtherCrystellumAgentTarget(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnexhaustedLeader(AGENT_ID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = game.getRealPlayersExcludingThis(player).stream()
                .map(target -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_TARGET + target.getFaction(),
                        target.getColorDisplayName(),
                        target.fogSafeEmoji()))
                .toList();
        if (buttons.isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose the player on whom you wish to use Shardwright Veyla, the Crystellum agent.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_TARGET)
    public static void selectCrystellumAgentTarget(
            ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace(SELECT_TARGET, ""));
        if (target == null || target.getFaction().equals(agentOwner.getFaction())) {
            return;
        }
        sendFighterPlacementButtons(event, game, agentOwner, target);
        ButtonHelper.deleteMessage(event);
    }

    private static void sendFighterPlacementButtons(
            ButtonInteractionEvent event, Game game, Player agentOwner, Player target) {
        if (!agentOwner.hasUnexhaustedLeader(AGENT_ID)) {
            return;
        }
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There is no active system for Shardwright Veyla.");
            return;
        }

        int nonFighterShips = ButtonHelper.checkNumberNonFighterShips(target, activeSystem, false);
        if (nonFighterShips < 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    target.getRepresentationUnfoggedNoPing()
                            + " has no non-fighter ships in the active system for Shardwright Veyla.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (int amount = 1; amount <= nonFighterShips; amount++) {
            buttons.add(Buttons.green(
                    target.factionButtonChecker() + PLACE_FIGHTERS + agentOwner.getFaction() + "|" + target.getFaction()
                            + "|" + activeSystem.getPosition() + "|" + amount,
                    "Place " + amount + " Fighter" + (amount == 1 ? "" : "s")));
        }
        buttons.add(Buttons.red(target.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged()
                        + ", choose how many fighters to place in "
                        + activeSystem.getRepresentationForButtons(game, target)
                        + " with Shardwright Veyla, the Crystellum agent.",
                buttons);
    }

    @ButtonHandler(PLACE_FIGHTERS)
    public static void placeCrystellumAgentFighters(
            ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] parts = buttonID.replace(PLACE_FIGHTERS, "").split("\\|", 4);
        if (parts.length != 4) {
            return;
        }
        Player agentOwner = game.getPlayerFromColorOrFaction(parts[0]);
        Player selectedTarget = game.getPlayerFromColorOrFaction(parts[1]);
        Tile activeSystem = game.getTileByPosition(parts[2]);
        int amount;
        try {
            amount = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            return;
        }
        if (agentOwner == null
                || selectedTarget == null
                || !selectedTarget.getFaction().equals(target.getFaction())
                || activeSystem == null
                || !agentOwner.hasUnexhaustedLeader(AGENT_ID)
                || amount < 1
                || amount > ButtonHelper.checkNumberNonFighterShips(target, activeSystem, false)) {
            return;
        }

        Leader agent = agentOwner.getLeader(AGENT_ID).orElse(null);
        if (agent == null) {
            return;
        }
        ExhaustLeaderService.exhaustLeader(game, agentOwner, agent);
        AddUnitService.addUnits(event, activeSystem, game, target.getColor(), amount + " fighter");
        if (!target.getFaction().equals(agentOwner.getFaction())) {
            AddUnitService.addUnits(
                    event, agentOwner.getNomboxTile(), game, agentOwner.getColor(), amount + " fighter");
        }
        ButtonHelper.deleteMessage(event);

        String message =
                agentOwner.getRepresentation() + " exhausted Shardwright Veyla, the Crystellum agent, allowing "
                        + target.getRepresentation() + " to place " + amount + " fighter" + (amount == 1 ? "" : "s")
                        + " in " + activeSystem.getRepresentationForButtons(game, target) + ".";
        if (!target.getFaction().equals(agentOwner.getFaction())) {
            message += " " + agentOwner.getRepresentation() + " captured " + amount + " fighter"
                    + (amount == 1 ? "" : "s") + " from the supply.";
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
    }

    public static void startCrystellumHero(GenericInteractionCreateEvent event, Game game, Player player) {
        int capturedFighters = player.getNomboxTile().getUnitHolders().values().stream()
                .mapToInt(holder -> holder.getUnitCount(UnitType.Fighter, player))
                .sum();
        if (capturedFighters < 1) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + " has no captured fighters to return with Facet, the Crystellum hero.");
            return;
        }
        if (getCrystellumHeroSystemButtons(game, player).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + " has no system containing one of their ships for Facet, the Crystellum hero.");
            return;
        }

        List<Button> buttons = getCrystellumHeroReturnButtons(player, capturedFighters);
        String message = player.getRepresentationUnfogged()
                + ", please choose how many captured fighters to return with Facet, the Crystellum hero.";
        String buttonPrefix = player.factionButtonChecker() + HERO_RETURN;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), message, NewStuffHelper.buttonPagination(buttons, buttonPrefix, 0));
    }

    @ButtonHandler(HERO_RETURN)
    public static void returnCrystellumHeroFighters(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int capturedFighters = player.getNomboxTile().getUnitHolders().values().stream()
                .mapToInt(holder -> holder.getUnitCount(UnitType.Fighter, player))
                .sum();
        List<Button> buttons = getCrystellumHeroReturnButtons(player, capturedFighters);
        String message = player.getRepresentationUnfogged()
                + ", please choose how many captured fighters to return with Facet, the Crystellum hero.";
        String buttonPrefix = player.factionButtonChecker() + HERO_RETURN;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }

        int returned;
        try {
            returned = Integer.parseInt(buttonID.replace(HERO_RETURN, ""));
        } catch (NumberFormatException e) {
            return;
        }
        if (returned < 1
                || returned > capturedFighters
                || getCrystellumHeroSystemButtons(game, player).isEmpty()) {
            return;
        }

        RemoveUnitService.removeUnits(event, player.getNomboxTile(), game, player.getColor(), returned + " fighter");
        game.setStoredValue(HERO_BUDGET + player.getFaction(), Integer.toString(returned * 4));
        ButtonHelper.deleteMessage(event);
        sendCrystellumHeroSystemButtons(event, game, player);
    }

    @ButtonHandler(HERO_SYSTEM)
    public static void chooseCrystellumHeroSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> systemButtons = getCrystellumHeroSystemButtons(game, player);
        String message = getCrystellumHeroSystemMessage(game, player);
        String buttonPrefix = player.factionButtonChecker() + HERO_SYSTEM;
        List<Button> extraButtons =
                List.of(Buttons.red(player.factionButtonChecker() + HERO_DONE, "Done Placing Units"));
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), systemButtons, extraButtons, message, buttonPrefix, buttonID)) {
            return;
        }

        Tile tile = game.getTileByPosition(buttonID.replace(HERO_SYSTEM, ""));
        int remainingHalfCost;
        try {
            remainingHalfCost = Integer.parseInt(game.getStoredValue(HERO_BUDGET + player.getFaction()));
        } catch (NumberFormatException e) {
            return;
        }
        if (tile == null || !FoWHelper.playerHasActualShipsInSystem(player, tile)) {
            return;
        }

        List<Button> unitButtons = getCrystellumHeroPlacementButtons(game, player, tile, remainingHalfCost);
        if (unitButtons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "No unit fits the remaining Facet budget in that system.");
            return;
        }

        ButtonHelper.deleteMessage(event);
        String placementMessage = player.getRepresentationUnfogged()
                + ", please choose a unit to place in "
                + tile.getRepresentationForButtons(game, player)
                + " with Facet, the Crystellum hero.";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                placementMessage,
                NewStuffHelper.buttonPagination(
                        unitButtons, player.factionButtonChecker() + HERO_PLACE + tile.getPosition() + "|", 0));
    }

    @ButtonHandler(HERO_PLACE)
    public static void placeCrystellumHeroUnit(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace(HERO_PLACE, "").split("\\|", 3);
        Tile tile = parts.length >= 1 ? game.getTileByPosition(parts[0]) : null;
        UnitModel unit = parts.length == 3 ? player.getUnitFromAsyncID(parts[1]) : null;
        String unitHolderName = parts.length == 3 ? parts[2] : null;
        int remainingHalfCost;
        try {
            remainingHalfCost = Integer.parseInt(game.getStoredValue(HERO_BUDGET + player.getFaction()));
        } catch (NumberFormatException e) {
            return;
        }
        int unitHalfCost = unit == null ? Integer.MAX_VALUE : Math.round(unit.getCost() * 2);
        List<Button> unitButtons =
                tile == null ? List.of() : getCrystellumHeroPlacementButtons(game, player, tile, remainingHalfCost);
        String message = tile == null
                ? ""
                : player.getRepresentationUnfogged()
                        + ", please choose a unit to place in "
                        + tile.getRepresentationForButtons(game, player)
                        + " with Facet, the Crystellum hero.";
        String buttonPrefix = player.factionButtonChecker() + HERO_PLACE + parts[0] + "|";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), unitButtons, message, buttonPrefix, buttonID)) {
            return;
        }
        if (tile == null
                || unit == null
                || unit.getIsStructure()
                || unit.getCost() < 0
                || unitHalfCost > remainingHalfCost
                || !FoWHelper.playerHasActualShipsInSystem(player, tile)
                || (!"space".equals(unitHolderName)
                        && (unit.getIsSpaceOnly()
                                || tile.getPlanetUnitHolders().stream()
                                        .map(Planet::getName)
                                        .noneMatch(planetName -> planetName.equals(unitHolderName)
                                                && player.getPlanets().contains(planetName))))) {
            return;
        }

        AddUnitService.addUnits(
                event,
                tile,
                game,
                player.getColor(),
                "1 " + unit.getAsyncId() + ("space".equals(unitHolderName) ? "" : " " + unitHolderName));
        game.setStoredValue(HERO_BUDGET + player.getFaction(), Integer.toString(remainingHalfCost - unitHalfCost));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " placed 1 " + unit.getName() + " "
                        + ("space".equals(unitHolderName)
                                ? "in space in "
                                : "on " + Helper.getPlanetRepresentation(unitHolderName, game) + " in ")
                        + tile.getRepresentationForButtons(game, player)
                        + " with Facet, the Crystellum hero.");
        sendCrystellumHeroSystemButtons(event, game, player);
    }

    @ButtonHandler(HERO_DONE)
    public static void finishCrystellumHero(ButtonInteractionEvent event, Game game, Player player) {
        game.removeStoredValue(HERO_BUDGET + player.getFaction());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " finished resolving Facet, the Crystellum hero.");
    }

    private static List<Button> getCrystellumHeroReturnButtons(Player player, int capturedFighters) {
        List<Button> buttons = new ArrayList<>();
        for (int amount = 1; amount <= capturedFighters; amount++) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + HERO_RETURN + amount,
                    "Return " + amount + " Fighter" + (amount == 1 ? "" : "s"),
                    UnitEmojis.fighter));
        }
        return buttons;
    }

    private static List<Button> getCrystellumHeroSystemButtons(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasActualShipsInSystem(player, tile))
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + HERO_SYSTEM + tile.getPosition(),
                        "Choose " + tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static String getCrystellumHeroSystemMessage(Game game, Player player) {
        int remainingHalfCost;
        try {
            remainingHalfCost = Integer.parseInt(game.getStoredValue(HERO_BUDGET + player.getFaction()));
        } catch (NumberFormatException e) {
            remainingHalfCost = 0;
        }
        String remainingCost =
                remainingHalfCost % 2 == 0 ? Integer.toString(remainingHalfCost / 2) : remainingHalfCost / 2 + ".5";
        return player.getRepresentationUnfogged()
                + ", please choose a system containing one of your ships to place a unit with Facet, the Crystellum hero."
                + " Remaining cost: " + remainingCost + ".";
    }

    private static List<Button> getCrystellumHeroPlacementButtons(
            Game game, Player player, Tile tile, int remainingHalfCost) {
        List<Button> buttons = new ArrayList<>();
        for (UnitModel unit : player.getUnitModels().stream()
                .map(UnitModel::getAsyncId)
                .filter(Objects::nonNull)
                .distinct()
                .map(player::getUnitFromAsyncID)
                .filter(Objects::nonNull)
                .filter(unit -> !unit.getIsStructure())
                .filter(unit -> unit.getCost() >= 0)
                .filter(unit -> Math.round(unit.getCost() * 2) <= remainingHalfCost)
                .sorted(Comparator.comparing(UnitModel::getName))
                .toList()) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker()
                            + HERO_PLACE
                            + tile.getPosition()
                            + "|"
                            + unit.getAsyncId()
                            + "|space",
                    "Place " + unit.getName() + " In Space (Cost " + unit.getCost() + ")",
                    unit.getUnitEmoji()));
            if (!unit.getIsSpaceOnly()) {
                for (Planet planet : tile.getPlanetUnitHolders()) {
                    if (!player.getPlanets().contains(planet.getName())) {
                        continue;
                    }
                    buttons.add(Buttons.green(
                            player.factionButtonChecker()
                                    + HERO_PLACE
                                    + tile.getPosition()
                                    + "|"
                                    + unit.getAsyncId()
                                    + "|"
                                    + planet.getName(),
                            "Place " + unit.getName() + " on "
                                    + Helper.getPlanetRepresentation(planet.getName(), game)
                                    + " (Cost "
                                    + unit.getCost()
                                    + ")",
                            unit.getUnitEmoji()));
                }
            }
        }
        return buttons;
    }

    private static void sendCrystellumHeroSystemButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> systemButtons = getCrystellumHeroSystemButtons(game, player);
        if (systemButtons.isEmpty()) {
            game.removeStoredValue(HERO_BUDGET + player.getFaction());
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " finished resolving Facet, the Crystellum hero.");
            return;
        }

        List<Button> extraButtons =
                List.of(Buttons.red(player.factionButtonChecker() + HERO_DONE, "Done Placing Units"));
        List<Button> buttons = systemButtons.size() <= 24
                ? new ArrayList<>(systemButtons)
                : NewStuffHelper.buttonPagination(
                        systemButtons, extraButtons, player.factionButtonChecker() + HERO_SYSTEM, 25, 0, false);
        if (systemButtons.size() <= 24) {
            buttons.addAll(extraButtons);
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), getCrystellumHeroSystemMessage(game, player), buttons);
    }
}
