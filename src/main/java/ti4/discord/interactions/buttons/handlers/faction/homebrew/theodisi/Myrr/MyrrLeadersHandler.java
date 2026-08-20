package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Myrr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class MyrrLeadersHandler {
    private static final String AGENT = "myrragent";
    private static final String AGENT_USE = "useMyrrAgent_";
    private static final String AGENT_OTHER = "useMyrrAgentOnActivePlayer";
    private static final String AGENT_DESTROY = "myrrAgentDestroy_";
    private static final String AGENT_PRODUCTION = "myrrAgentProduction_";
    private static final String AGENT_POSITION = "myrrAgentPosition_";
    private static final String COMMANDER_USES = "myrrCommanderUses_";

    private static final String HERO_REMOVE = "myrrHeroRemove_";
    private static final String HERO_DONE_REMOVING = "myrrHeroDoneRemoving";
    private static final String HERO_CHOOSE_UNIT = "myrrHeroChooseUnit_";
    private static final String HERO_PLACE = "myrrHeroPlace_";
    private static final String HERO_ACTIVE = "myrrHeroActive_";
    private static final String HERO_UNITS = "myrrHeroUnits_";

    public static void offerMyrrAgent(Game game, Player activatingPlayer, Tile tile) {
        if (game == null || activatingPlayer == null || tile == null) {
            return;
        }

        if (activatingPlayer.hasUnexhaustedLeader(AGENT)
                && !getAgentShips(activatingPlayer, tile).isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    activatingPlayer.getCorrectChannel(),
                    activatingPlayer.getRepresentation()
                            + ", you may exhaust Kelron Dross, the Myrr agent, in the activated system.",
                    List.of(
                            Buttons.gray(
                                    activatingPlayer.factionButtonChecker() + AGENT_USE + activatingPlayer.getFaction()
                                            + "|" + tile.getPosition(),
                                    "Use Myrr Agent",
                                    FactionEmojis.myrr),
                            Buttons.red("deleteButtons", "Decline")));
        }
    }

    public static Button getMyrrAgentCardsInfoButton(Player player) {
        return Buttons.gray(player.factionButtonChecker() + AGENT_OTHER, "Use Myrr Agent", FactionEmojis.myrr);
    }

    @ButtonHandler(AGENT_OTHER)
    public static void useMyrrAgentOnActivePlayer(ButtonInteractionEvent event, Game game, Player player) {
        Player target = game == null ? null : game.getActivePlayer();
        Tile tile = game == null ? null : game.getTileByPosition(game.getActiveSystem());
        if (target == null || tile == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Kelron Dross, the Myrr agent, requires an active system.");
            return;
        }
        startMyrrAgent(event, game, player, target, tile);
    }

    @ButtonHandler(AGENT_USE)
    public static void useMyrrAgent(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(AGENT_USE.length()).split("\\|", 2);
        Player target = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[1]) : null;
        startMyrrAgent(event, game, player, target, tile);
    }

    private static void startMyrrAgent(
            ButtonInteractionEvent event, Game game, Player agentOwner, Player target, Tile tile) {
        if (agentOwner == null
                || target == null
                || tile == null
                || !agentOwner.hasUnexhaustedLeader(AGENT)
                || !tile.getPosition().equals(game.getActiveSystem())
                || target != game.getActivePlayer()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Kelron Dross, the Myrr agent, cannot be used right now.");
            return;
        }

        List<Button> buttons = getAgentShips(target, tile).stream()
                .map(unitKey -> Buttons.red(
                        target.factionButtonChecker() + AGENT_DESTROY + agentOwner.getFaction() + "|"
                                + tile.getPosition() + "|" + unitKey.asyncID(),
                        "Destroy 1 " + unitKey.humanReadableName(),
                        unitKey.unitEmoji()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    target.getRepresentation() + " has no eligible ship for Kelron Dross, the Myrr agent.");
            return;
        }

        ExhaustLeaderService.exhaustLeader(game, agentOwner, agentOwner.unsafeGetLeader(AGENT));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentation()
                        + ", Kelron Dross, the Myrr agent, allows you to destroy 1 non-fighter ship with a cost less than 4 in the activated system.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(AGENT_DESTROY)
    public static void destroyShipForMyrrAgent(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(AGENT_DESTROY.length()).split("\\|", 3);
        Player agentOwner = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Tile tile = payload.length == 3 ? game.getTileByPosition(payload[1]) : null;
        UnitHolder space = tile == null ? null : tile.getSpaceUnitHolder();
        UnitKey unitKey = space == null
                ? null
                : getAgentShips(player, tile).stream()
                        .filter(key -> key.asyncID().equals(payload[2]))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), space);
        if (agentOwner == null
                || unit == null
                || !agentOwner.hasLeader(AGENT)
                || !tile.getPosition().equals(game.getActiveSystem())) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int production = Math.round(unit.getCost()) + 2;
        DestroyUnitService.destroyUnit(event, tile, game, unitKey, 1, space, false);
        game.setStoredValue(AGENT_PRODUCTION + player.getFaction(), Integer.toString(production));
        game.setStoredValue(AGENT_POSITION + player.getFaction(), tile.getPosition());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " destroyed 1 " + unit.getName() + " using Kelron Dross, the Myrr agent. "
                        + tile.getRepresentationForButtons(game, player) + " gained PRODUCTION " + production + ".");
        ButtonHelper.deleteMessage(event);
    }

    public static int getMyrrAgentProduction(Game game, Player player, Tile tile) {
        if (game == null
                || player == null
                || tile == null
                || !tile.getPosition().equals(game.getStoredValue(AGENT_POSITION + player.getFaction()))) {
            return 0;
        }
        String value = game.getStoredValue(AGENT_PRODUCTION + player.getFaction());
        try {
            return value.isEmpty() ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static void clearMyrrAgent(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(AGENT_PRODUCTION + player.getFaction());
            game.removeStoredValue(AGENT_POSITION + player.getFaction());
        }
    }

    private static List<UnitKey> getAgentShips(Player player, Tile tile) {
        UnitHolder space = tile.getSpaceUnitHolder();
        return space.getUnitKeysForPlayer(player).stream()
                .filter(key -> space.getUnitCount(key) > 0)
                .filter(key -> key.unitType() != UnitType.Fighter)
                .filter(key -> {
                    UnitModel unit = player.getPriorityUnitByAsyncID(key.asyncID(), space);
                    return unit != null && unit.getIsShip() && unit.getCost() < 4;
                })
                .toList();
    }

    public static void resolveMyrrCommander(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Tile tile,
            UnitKey unitKey,
            String location,
            int count) {
        if (game == null
                || player == null
                || tile == null
                || count < 1
                || "setup".equalsIgnoreCase(game.getPhaseOfGame())
                || !game.playerHasLeaderUnlockedOrAlliance(player, "myrrcommander")
                || (unitKey.unitType() != UnitType.Pds && unitKey.unitType() != UnitType.Spacedock)
                || !(tile.getUnitHolders().get(location) instanceof Planet)
                || !player.getPlanets().contains(location)) {
            return;
        }

        int usesThisRound = getCommanderUsesThisRound(game, player);
        int resolvedStructures = Math.min(count, Math.max(0, 2 - usesThisRound));
        if (resolvedStructures < 1) {
            return;
        }
        game.setStoredValue(getCommanderUsesKey(game, player), Integer.toString(usesThisRound + resolvedStructures));

        int cards = resolvedStructures * 2;
        ActionCardHelper.drawActionCards(player, cards);
        for (int i = 0; i < resolvedStructures; i++) {
            ActionCardHelper.sendACDiscardButtons(player);
        }
        MessageHelper.sendMessageToChannel(
                event == null ? player.getCorrectChannel() : event.getMessageChannel(),
                player.getRepresentation() + " drew " + cards + " action card" + (cards == 1 ? "" : "s")
                        + " using Thessa Scale, the Myrr commander. Buttons to discard 1 have been sent to your #cards-info thread.");
    }

    private static int getCommanderUsesThisRound(Game game, Player player) {
        try {
            return Integer.parseInt(game.getStoredValue(getCommanderUsesKey(game, player)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String getCommanderUsesKey(Game game, Player player) {
        return COMMANDER_USES + player.getFaction() + "_" + game.getRound();
    }

    public static void startMyrrHero(GenericInteractionCreateEvent event, Game game, Player player) {
        game.setStoredValue(HERO_ACTIVE + player.getFaction(), "true");
        game.setStoredValue(HERO_UNITS + player.getFaction(), "");
        sendHeroRemovalButtons(event, game, player);
    }

    @ButtonHandler(HERO_REMOVE)
    public static void removeMyrrHeroUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!isResolvingMyrrHero(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getHeroRemovalButtons(game, player);
        List<Button> extraButtons =
                List.of(Buttons.green(player.factionButtonChecker() + HERO_DONE_REMOVING, "Done Removing Units"));
        String message = getHeroRemovalMessage(player, game);
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                extraButtons,
                message,
                player.factionButtonChecker() + HERO_REMOVE,
                buttonID)) {
            return;
        }

        String[] payload = buttonID.substring(HERO_REMOVE.length()).split("\\|", 4);
        Tile tile = payload.length == 4 ? game.getTileByPosition(payload[0]) : null;
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(payload[1]);
        UnitKey unitKey =
                holder == null ? null : Mapper.getUnitKey(AliasHandler.resolveUnit(payload[2]), player.getColor());
        UnitState state = payload.length == 4 ? Units.findUnitState(payload[3]) : null;
        if (unitKey == null || state == null || holder.getUnitCountForState(unitKey, state) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        RemoveUnitService.removeUnit(event, tile, game, player, holder, unitKey.unitType(), 1, state);
        changeHeroUnitCount(game, player, unitKey.asyncID(), state, 1);
        ButtonHelper.deleteMessage(event);
        sendHeroRemovalButtons(event, game, player);
    }

    @ButtonHandler(HERO_DONE_REMOVING)
    public static void finishMyrrHeroRemovals(ButtonInteractionEvent event, Game game, Player player) {
        if (!isResolvingMyrrHero(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        ButtonHelper.deleteMessage(event);
        if (getHeroUnits(game, player).isEmpty()) {
            game.removeStoredValue(HERO_ACTIVE + player.getFaction());
            game.removeStoredValue(HERO_UNITS + player.getFaction());
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " did not relocate any units with Orun Slag, the Myrr hero.");
            return;
        }
        sendHeroUnitChoices(event, game, player);
    }

    @ButtonHandler(HERO_CHOOSE_UNIT)
    public static void chooseMyrrHeroUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!isResolvingMyrrHero(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> unitButtons = getHeroUnitChoiceButtons(game, player);
        String unitMessage =
                player.getRepresentation() + ", please choose a removed unit to place using Orun Slag, the Myrr hero.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                unitButtons,
                null,
                unitMessage,
                player.factionButtonChecker() + HERO_CHOOSE_UNIT,
                buttonID)) {
            return;
        }
        String[] payload = buttonID.substring(HERO_CHOOSE_UNIT.length()).split("\\|", 2);
        UnitKey unitKey =
                payload.length == 2 ? Mapper.getUnitKey(AliasHandler.resolveUnit(payload[0]), player.getColor()) : null;
        UnitState state = payload.length == 2 ? Units.findUnitState(payload[1]) : null;
        if (unitKey == null || state == null || getHeroUnitCount(game, player, unitKey.asyncID(), state) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = getHeroDestinationButtons(game, player, unitKey, state);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + " has no eligible destination for that unit using Orun Slag, the Myrr hero.");
            return;
        }
        String prefix = player.factionButtonChecker() + HERO_PLACE + unitKey.asyncID() + "|" + state.name() + "|";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", please choose where to place the selected unit using Orun Slag, the Myrr hero.",
                buttons.size() <= 25 ? buttons : NewStuffHelper.buttonPagination(buttons, null, prefix, 25, 0, false));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(HERO_PLACE)
    public static void placeMyrrHeroUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!isResolvingMyrrHero(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String[] payload = buttonID.substring(HERO_PLACE.length()).split("\\|", 4);
        UnitKey unitKey =
                payload.length == 4 ? Mapper.getUnitKey(AliasHandler.resolveUnit(payload[0]), player.getColor()) : null;
        UnitState state = payload.length == 4 ? Units.findUnitState(payload[1]) : null;
        if (unitKey == null || state == null) {
            return;
        }

        List<Button> buttons = getHeroDestinationButtons(game, player, unitKey, state);
        String message = player.getRepresentation()
                + ", please choose where to place the selected unit using Orun Slag, the Myrr hero.";
        String prefix = player.factionButtonChecker() + HERO_PLACE + unitKey.asyncID() + "|" + state.name() + "|";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, null, message, prefix, buttonID)) {
            return;
        }

        Tile tile = payload.length == 4 ? game.getTileByPosition(payload[2]) : null;
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(payload[3]);
        if (holder == null
                || getHeroUnitCount(game, player, unitKey.asyncID(), state) < 1
                || !isHeroDestination(game, player, tile)
                || !isValidHeroHolder(player, unitKey, holder)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Integer> states = UnitState.emptyList();
        states.set(state.ordinal(), 1);
        AddUnitService.addUnits(
                event,
                tile,
                game,
                player.getColor(),
                "1 " + unitKey.asyncID() + " " + holder.getName(),
                List.of(new RemovedUnit(unitKey, tile, holder, states)));
        changeHeroUnitCount(game, player, unitKey.asyncID(), state, -1);
        ButtonHelper.deleteMessage(event);
        if (getHeroUnits(game, player).isEmpty()) {
            game.removeStoredValue(HERO_ACTIVE + player.getFaction());
            game.removeStoredValue(HERO_UNITS + player.getFaction());
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " finished resolving Orun Slag, the Myrr hero.");
        } else {
            sendHeroUnitChoices(event, game, player);
        }
    }

    private static void sendHeroRemovalButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> buttons = getHeroRemovalButtons(game, player);
        List<Button> extraButtons =
                List.of(Buttons.green(player.factionButtonChecker() + HERO_DONE_REMOVING, "Done Removing Units"));
        List<Button> displayed = buttons.size() <= 24
                ? new ArrayList<>(buttons)
                : NewStuffHelper.buttonPagination(
                        buttons, extraButtons, player.factionButtonChecker() + HERO_REMOVE, 25, 0, false);
        if (buttons.size() <= 24) {
            displayed.addAll(extraButtons);
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "-# This is per individual unit. If you have multiple units of the same type, you will need to select them one at a time. You may want to sit down. This is going to take a while. You might want to get a snack too, just something to munch on while you move your units around. This was the only way to preserve the true intent of this hero.");
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), getHeroRemovalMessage(player, game), displayed);
    }

    private static String getHeroRemovalMessage(Player player, Game game) {
        int selected = getHeroUnits(game, player).values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        return player.getRepresentation()
                + ", please choose units to remove from the board using Orun Slag, the Myrr hero. " + selected + " unit"
                + (selected == 1 ? " has" : "s have") + " been selected.";
    }

    private static List<Button> getHeroRemovalButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        game.getTileMap().values().stream()
                .sorted(Comparator.comparing(Tile::getPosition))
                .forEach(tile -> tile.getUnitHolders().values().forEach(holder -> {
                    for (UnitKey unitKey : holder.getUnitKeysForPlayer(player)) {
                        for (UnitState state : holder.getNonZeroUnitStates(unitKey)) {
                            String stateText = state == UnitState.none ? "" : state.humanDescr() + " ";
                            buttons.add(Buttons.red(
                                    player.factionButtonChecker() + HERO_REMOVE + tile.getPosition() + "|"
                                            + holder.getName() + "|" + unitKey.asyncID() + "|" + state.name(),
                                    "Remove " + stateText + unitKey.humanReadableName() + " from "
                                            + tile.getRepresentationForButtons(game, player),
                                    unitKey.unitEmoji()));
                        }
                    }
                }));
        return buttons;
    }

    private static void sendHeroUnitChoices(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> buttons = getHeroUnitChoiceButtons(game, player);
        String message =
                player.getRepresentation() + ", please choose a removed unit to place using Orun Slag, the Myrr hero.";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                buttons.size() <= 25
                        ? buttons
                        : NewStuffHelper.buttonPagination(
                                buttons, null, player.factionButtonChecker() + HERO_CHOOSE_UNIT, 25, 0, false));
    }

    private static List<Button> getHeroUnitChoiceButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : getHeroUnits(game, player).entrySet()) {
            String[] key = entry.getKey().split("\\|", 2);
            UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(key[0]), player.getColor());
            UnitState state = Units.findUnitState(key[1]);
            if (unitKey == null || state == null || entry.getValue() < 1) {
                continue;
            }
            String stateText = state == UnitState.none ? "" : state.humanDescr() + " ";
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + HERO_CHOOSE_UNIT + unitKey.asyncID() + "|" + state.name(),
                    "Place " + stateText + unitKey.humanReadableName() + " (" + entry.getValue() + " Remaining)",
                    unitKey.unitEmoji()));
        }
        return buttons;
    }

    private static List<Button> getHeroDestinationButtons(Game game, Player player, UnitKey unitKey, UnitState state) {
        if (getHeroUnitCount(game, player, unitKey.asyncID(), state) < 1) {
            return List.of();
        }
        List<Button> buttons = new ArrayList<>();
        game.getTileMap().values().stream()
                .filter(tile -> isHeroDestination(game, player, tile))
                .sorted(Comparator.comparing(Tile::getPosition))
                .forEach(tile -> tile.getUnitHolders().values().stream()
                        .filter(holder -> isValidHeroHolder(player, unitKey, holder))
                        .forEach(holder -> buttons.add(Buttons.green(
                                player.factionButtonChecker() + HERO_PLACE + unitKey.asyncID() + "|" + state.name()
                                        + "|" + tile.getPosition() + "|" + holder.getName(),
                                "Place in " + tile.getRepresentationForButtons(game, player)
                                        + (Constants.SPACE.equals(holder.getName())
                                                ? " Space"
                                                : " on " + Helper.getPlanetRepresentation(holder.getName(), game)),
                                unitKey.unitEmoji()))));
        return buttons;
    }

    private static boolean isHeroDestination(Game game, Player player, Tile tile) {
        return tile.getPlanetUnitHolders().stream().map(Planet::getName).anyMatch(player.getPlanets()::contains)
                && !FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game);
    }

    private static boolean isValidHeroHolder(Player player, UnitKey unitKey, UnitHolder holder) {
        UnitModel unit = player.getUnitFromUnitKey(unitKey);
        if (unit == null) {
            return false;
        }
        if (unit.getIsStructure()) {
            return holder instanceof Planet planet && player.getPlanets().contains(planet.getName());
        }
        if (unit.getIsShip()) {
            return "space".equals(holder.getName());
        }
        return "space".equals(holder.getName())
                || holder instanceof Planet planet && player.getPlanets().contains(planet.getName());
    }

    private static Map<String, Integer> getHeroUnits(Game game, Player player) {
        Map<String, Integer> units = new HashMap<>();
        String stored = game.getStoredValue(HERO_UNITS + player.getFaction());
        if (stored.isEmpty()) {
            return units;
        }
        for (String entry : stored.split(",")) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2) {
                try {
                    units.put(parts[0], Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed legacy state.
                }
            }
        }
        return units;
    }

    private static int getHeroUnitCount(Game game, Player player, String asyncId, UnitState state) {
        return getHeroUnits(game, player).getOrDefault(asyncId + "|" + state.name(), 0);
    }

    private static void changeHeroUnitCount(Game game, Player player, String asyncId, UnitState state, int change) {
        Map<String, Integer> units = getHeroUnits(game, player);
        String key = asyncId + "|" + state.name();
        units.merge(key, change, Integer::sum);
        units.entrySet().removeIf(entry -> entry.getValue() < 1);
        game.setStoredValue(
                HERO_UNITS + player.getFaction(),
                units.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(java.util.stream.Collectors.joining(",")));
    }

    private static boolean isResolvingMyrrHero(Game game, Player player) {
        return game != null && player != null && "true".equals(game.getStoredValue(HERO_ACTIVE + player.getFaction()));
    }
}
