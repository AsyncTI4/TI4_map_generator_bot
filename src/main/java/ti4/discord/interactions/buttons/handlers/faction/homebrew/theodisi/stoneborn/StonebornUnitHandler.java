package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.stoneborn;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperExplore;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class StonebornUnitHandler {
    private static final String USE_STALACTIUM = "useStalactium";
    private static final String SELECT_STALACTIUM_SYSTEM = "selectStalactiumSystem_";
    private static final String PURGE_STALACTIUM_FRAGMENT = "purgeStalactiumFragment_";
    private static final String STALACTIUM_TARGET = "stalactiumTarget_";
    private static final String STALACTIUM_FRAGMENTS = "stalactiumFragments_";
    private static final String USE_GOLEM = "useGolem_";

    public static void offerGolemStatusPhase(Game game, Player player) {
        int cost = getGolemCost(player);
        if (cost == 0) {
            return;
        }

        List<Button> buttons = getGolemButtons(game, player, cost);
        if (buttons.isEmpty()) {
            return;
        }

        List<Button> pinned = List.of(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation()
                        + ", you may remove " + cost
                        + " Golems from the same planet to draw 1 random Stoneborn relic fragment.",
                paginateGolemButtons(buttons, pinned, player));
    }

    @ButtonHandler(USE_GOLEM)
    public static void useGolem(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        int cost = getGolemCost(player);
        List<Button> buttons = getGolemButtons(game, player, cost);
        String message = player.getRepresentation()
                + ", you may remove " + cost
                + " Golems from the same planet to draw 1 random Stoneborn relic fragment.";
        String prefix = player.factionButtonChecker() + USE_GOLEM;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                getOutputChannel(game, player, event),
                buttons,
                List.of(Buttons.red("deleteButtons", "Decline")),
                message,
                prefix,
                buttonID)) {
            return;
        }
        String planetName = buttonID.substring(USE_GOLEM.length());
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (cost == 0 || tile == null || planet == null || planet.getUnitCount(UnitType.Mech, player) < cost) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        int removed = RemoveUnitService.removeUnit(event, tile, game, player, planet, UnitType.Mech, cost).stream()
                .mapToInt(RemoveUnitService.RemovedUnit::getTotalRemoved)
                .sum();
        if (removed == cost) {
            StonebornAbilityHandler.drawRandomStonebornFragment(game, player, "Golem");
        }
        ButtonHelper.deleteMessage(event);
    }

    private static int getGolemCost(Player player) {
        if (player.hasUnit("stoneborn_mechunleashed")) {
            return 2;
        }
        return player.hasUnit("stoneborn_mechdormant") ? 3 : 0;
    }

    private static List<Button> getGolemButtons(Game game, Player player, int cost) {
        if (cost == 0) {
            return List.of();
        }
        return game.getTileMap().values().stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .filter(planet -> planet.getUnitCount(UnitType.Mech, player) >= cost)
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + USE_GOLEM + planet.getName(),
                        "Remove " + cost + " Golems From " + planet.getRepresentation(game)))
                .toList();
    }

    private static List<Button> paginateGolemButtons(List<Button> buttons, List<Button> pinned, Player player) {
        if (buttons.size() + pinned.size() <= 25) {
            List<Button> result = new ArrayList<>(buttons);
            result.addAll(pinned);
            return result;
        }
        return NewStuffHelper.buttonPagination(
                buttons, pinned, player.factionButtonChecker() + USE_GOLEM, 25, 0, false);
    }

    public static void syncMobileMountain(Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        if (isMobileMountainOnBoard(game, player)) {
            player.addPlanet("mobilemountain");
        } else {
            player.removePlanet("mobilemountain");
        }
    }

    private static boolean isMobileMountainOnBoard(Game game, Player player) {
        UnitKey mm = Units.getUnitKey(UnitType.Flagship, player.getColorID());
        return game.getTileMap().values().stream()
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .anyMatch(unitHolder -> unitHolder.getUnitCount(mm) > 0);
    }

    public static Button getStalactiumStartTurnButton(Game game, Player player) {
        int cost = getStalactiumCost(player);
        if (cost == 0
                || player.getFragments().size() < cost
                || ButtonHelper.getTilesWithShipsInTheSystem(player, game).isEmpty()) {
            return null;
        }
        return Buttons.gray(
                player.factionButtonChecker() + USE_STALACTIUM, "Deploy Stalactium", FactionEmojis.stoneborn);
    }

    @ButtonHandler(USE_STALACTIUM)
    public static void useStalactium(Game game, Player player, ButtonInteractionEvent event) {
        int cost = getStalactiumCost(player);
        List<Tile> tiles = ButtonHelper.getTilesWithShipsInTheSystem(player, game);
        if (cost == 0 || player.getFragments().size() < cost || tiles.isEmpty()) {
            return;
        }
        List<Button> buttons = tiles.stream()
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + SELECT_STALACTIUM_SYSTEM + cost + "|" + tile.getPosition(),
                        "Place Dreadnought in " + tile.getRepresentationForButtons(game, player)))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                getOutputChannel(game, player, event),
                player.getRepresentation() + ", choose a system containing your ships for _Stalactium_.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(SELECT_STALACTIUM_SYSTEM)
    public static void selectStalactiumSystem(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String[] payload = buttonID.substring(SELECT_STALACTIUM_SYSTEM.length()).split("\\|", 2);
        if (payload.length != 2) {
            return;
        }
        int cost = parseCost(payload[0]);
        Tile tile = game.getTileByPosition(payload[1]);
        if (cost == 0
                || cost != getStalactiumCost(player)
                || player.getFragments().size() < cost
                || tile == null
                || !ButtonHelper.getTilesWithShipsInTheSystem(player, game).contains(tile)) {
            return;
        }
        game.setStoredValue(getTargetKey(player), cost + "|" + tile.getPosition());
        game.removeStoredValue(getFragmentsKey(player));
        offerStalactiumFragmentButtons(game, player, event);
        event.getMessage().delete().queue();
    }

    @ButtonHandler(PURGE_STALACTIUM_FRAGMENT)
    public static void purgeStalactiumFragment(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = getStalactiumFragmentButtons(game, player);
        String message = getStalactiumFragmentMessage(game, player);
        String buttonPrefix = player.factionButtonChecker() + PURGE_STALACTIUM_FRAGMENT;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, getOutputChannel(game, player, event), buttons, message, buttonPrefix, buttonID)) {
            return;
        }
        String fragment = buttonID.substring(PURGE_STALACTIUM_FRAGMENT.length());
        String[] target = game.getStoredValue(getTargetKey(player)).split("\\|", 2);
        int cost = target.length == 2 ? parseCost(target[0]) : 0;
        if (cost == 0
                || cost != getStalactiumCost(player)
                || !player.getFragments().contains(fragment)) {
            return;
        }
        List<String> fragments = getSelectedFragments(game, player);
        if (fragments.contains(fragment)) {
            return;
        }
        fragments.add(fragment);
        game.setStoredValue(getFragmentsKey(player), String.join(",", fragments));
        event.getMessage().delete().queue();
        if (fragments.size() < cost) {
            offerStalactiumFragmentButtons(game, player, event);
            return;
        }
        Tile tile = game.getTileByPosition(target[1]);
        if (tile == null
                || !ButtonHelper.getTilesWithShipsInTheSystem(player, game).contains(tile)) {
            clearStalactiumState(game, player);
            return;
        }
        ButtonHelperExplore.resolvePurgedFragments(game, player, event, fragments);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 dreadnought");
        MessageHelper.sendMessageToChannel(
                getOutputChannel(game, player, event),
                player.getRepresentationNoPing() + " placed 1 dreadnought with _Stalactium_.");
        clearStalactiumState(game, player);
    }

    private static void offerStalactiumFragmentButtons(Game game, Player player, GenericInteractionCreateEvent event) {
        String[] target = game.getStoredValue(getTargetKey(player)).split("\\|", 2);
        int cost = target.length == 2 ? parseCost(target[0]) : 0;
        List<Button> buttons = getStalactiumFragmentButtons(game, player);
        if (cost == 0 || buttons.isEmpty()) {
            clearStalactiumState(game, player);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                getOutputChannel(game, player, event),
                getStalactiumFragmentMessage(game, player),
                NewStuffHelper.buttonPagination(
                        buttons, null, player.factionButtonChecker() + PURGE_STALACTIUM_FRAGMENT, 24, 0, true));
    }

    private static List<Button> getStalactiumFragmentButtons(Game game, Player player) {
        List<String> selected = getSelectedFragments(game, player);
        return player.getFragments().stream()
                .filter(fragment -> !selected.contains(fragment))
                .map(Mapper::getExplore)
                .filter(fragment -> fragment != null)
                .map(fragment -> Buttons.gray(
                        player.factionButtonChecker() + PURGE_STALACTIUM_FRAGMENT + fragment.getAlias(),
                        "Purge " + fragment.getName(),
                        ExploreEmojis.getFragEmoji(fragment.getType())))
                .toList();
    }

    private static String getStalactiumFragmentMessage(Game game, Player player) {
        String[] target = game.getStoredValue(getTargetKey(player)).split("\\|", 2);
        int cost = target.length == 2 ? parseCost(target[0]) : 0;
        return player.getRepresentation() + ", choose a relic fragment to purge for _Stalactium_ ("
                + (cost - getSelectedFragments(game, player).size()) + " remaining).";
    }

    private static int getStalactiumCost(Player player) {
        if (player.hasUnit("stoneborn_dreadnought2dormant") || player.hasUnit("stoneborn_dreadnought2unleashed")) {
            return 1;
        }
        return player.hasUnit("stoneborn_dreadnought") ? 2 : 0;
    }

    private static int parseCost(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<String> getSelectedFragments(Game game, Player player) {
        String stored = game.getStoredValue(getFragmentsKey(player));
        if (stored.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(stored.split(",")));
    }

    private static void clearStalactiumState(Game game, Player player) {
        game.removeStoredValue(getTargetKey(player));
        game.removeStoredValue(getFragmentsKey(player));
    }

    private static String getTargetKey(Player player) {
        return STALACTIUM_TARGET + player.getFaction();
    }

    private static String getFragmentsKey(Player player) {
        return STALACTIUM_FRAGMENTS + player.getFaction();
    }

    private static MessageChannel getOutputChannel(Game game, Player player, GenericInteractionCreateEvent event) {
        return game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel();
    }
}
