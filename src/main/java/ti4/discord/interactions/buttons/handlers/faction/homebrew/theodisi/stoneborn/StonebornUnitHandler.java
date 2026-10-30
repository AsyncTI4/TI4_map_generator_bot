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
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.Mapper;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class StonebornUnitHandler {
    private static final String USE_STALACTIUM = "useStalactium";
    private static final String SELECT_STALACTIUM_SYSTEM = "selectStalactiumSystem_";
    private static final String PURGE_STALACTIUM_FRAGMENT = "purgeStalactiumFragment_";
    private static final String STALACTIUM_TARGET = "stalactiumTarget_";
    private static final String STALACTIUM_FRAGMENTS = "stalactiumFragments_";
    private static final String STALACTIUM_USED = "stalactiumUsed_";
    private static final String CANCEL_STALACTIUM = "cancelStalactium";
    private static final String USE_GOLEM_EXPLORE = "useGolemExplore_";
    private static final String RESOLVE_GOLEM_EXPLORE = "resolveGolemExplore_";
    private static final String GOLEM_EXPLORE_CARDS = "golemExploreCards_";

    public static boolean offerGolemExplore(
            Game game, Player player, Tile tile, String planetName, String trait, GenericInteractionCreateEvent event) {
        Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(planetName);
        int availableGolems = getAvailableGolemCount(player, planet);
        if (availableGolems == 0) {
            return false;
        }
        resetGolemExplore(game, player);
        List<Button> buttons = new ArrayList<>();
        for (int golemCount = 1; golemCount <= availableGolems; golemCount++) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + USE_GOLEM_EXPLORE + golemCount + "|" + planetName + "|" + trait,
                    "Use " + golemCount + " Golem" + (golemCount == 1 ? "" : "s")));
        }
        buttons.add(Buttons.red(
                player.factionButtonChecker() + USE_GOLEM_EXPLORE + "0|" + planetName + "|" + trait,
                "Explore Normally"));
        MessageHelper.sendMessageToChannelWithButtons(
                getOutputChannel(game, player, event),
                player.getRepresentation()
                        + ", you may use each Golem on this planet to draw 1 additional exploration card.",
                buttons);
        return true;
    }

    @ButtonHandler(USE_GOLEM_EXPLORE)
    public static void useGolemExplore(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String[] payload = buttonID.substring(USE_GOLEM_EXPLORE.length()).split("\\|", 3);
        if (payload.length != 3) {
            return;
        }
        Tile tile = game.getTileFromPlanet(payload[1]);
        Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(payload[1]);
        int golemCount = parseCost(payload[0]);
        if (planet == null
                || !player.getPlanetsAllianceMode().contains(payload[1])
                || (golemCount > 0 && golemCount > getAvailableGolemCount(player, planet))) {
            return;
        }
        if (golemCount == 0) {
            ExploreService.explorePlanet(event, tile, payload[1], payload[2], player, true, game, 1, false);
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (player.hasUnit("stoneborn_mechdormant")) {
            tile.addUnitDamage(planet.getName(), Units.getUnitKey(UnitType.Mech, player.getColorID()), golemCount);
        }
        offerGolemExploreResolution(game, player, event, payload[1], payload[2], golemCount + 1);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(RESOLVE_GOLEM_EXPLORE)
    public static void resolveGolemExplore(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        int selectedCardIndex = parseCost(buttonID.substring(RESOLVE_GOLEM_EXPLORE.length()));
        String[] state = game.getStoredValue(getGolemExploreCardsKey(player)).split("\\|", 2);
        if (state.length != 2) {
            return;
        }
        List<String> cards = new ArrayList<>(List.of(state[1].split(",")));
        if (selectedCardIndex < 0 || selectedCardIndex >= cards.size()) {
            return;
        }
        String selectedCard = cards.remove(selectedCardIndex);
        cards.forEach(game::addExplore);
        game.shuffleExplores();
        game.removeStoredValue(getGolemExploreCardsKey(player));
        Tile tile = game.getTileFromPlanet(state[0]);
        ExploreService.resolveExplore(
                event,
                selectedCard,
                tile,
                state[0],
                player.getRepresentationNoPing() + " chose to resolve _"
                        + Mapper.getExplore(selectedCard).getName() + "_.",
                player,
                game);
        ButtonHelper.deleteMessage(event);
    }

    private static int getAvailableGolemCount(Player player, Planet planet) {
        if (planet == null || planet.getUnitCount(UnitType.Mech, player) < 1) {
            return 0;
        }
        int golemCount = Math.min(4, planet.getUnitCount(UnitType.Mech, player));
        if (player.hasUnit("stoneborn_mechunleashed")) {
            return golemCount;
        }
        if (!player.hasUnit("stoneborn_mechdormant")) {
            return 0;
        }
        return golemCount - planet.getDamagedUnitCount(UnitType.Mech, player.getColorID());
    }

    private static void offerGolemExploreResolution(
            Game game, Player player, ButtonInteractionEvent event, String planetName, String trait, int exploreCount) {
        List<String> cards = new ArrayList<>();
        for (int i = 0; i < exploreCount; i++) {
            String card = game.drawExplore(trait);
            if (card == null) {
                break;
            }
            cards.add(card);
        }
        if (cards.isEmpty()) {
            MessageHelper.sendMessageToChannel(getOutputChannel(game, player, event), "Planet cannot be explored.");
            return;
        }
        game.setStoredValue(getGolemExploreCardsKey(player), planetName + "|" + String.join(",", cards));
        List<ExploreModel> explores = cards.stream().map(Mapper::getExplore).toList();
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < explores.size(); i++) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + RESOLVE_GOLEM_EXPLORE + i,
                    explores.get(i).getName()));
        }
        String foundCards = explores.stream()
                .map(ExploreModel::getName)
                .map(name -> "_" + name + "_")
                .collect(java.util.stream.Collectors.joining(", "));
        MessageHelper.sendMessageToChannel(
                getOutputChannel(game, player, event),
                player.getRepresentationNoPing() + " used Golems and found " + foundCards + " on "
                        + Helper.getPlanetRepresentationPlusEmoji(planetName) + ".");
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                getOutputChannel(game, player, event),
                player.getRepresentation() + " please choose 1 exploration card to resolve.",
                explores.stream().map(ExploreModel::getRepresentationEmbed).toList(),
                buttons);
    }

    private static String getGolemExploreCardsKey(Player player) {
        return GOLEM_EXPLORE_CARDS + player.getFaction();
    }

    public static void resetGolemExplore(Game game, Player player) {
        String[] state = game.getStoredValue(getGolemExploreCardsKey(player)).split("\\|", 2);
        if (state.length == 2 && !state[1].isEmpty()) {
            List.of(state[1].split(",")).forEach(game::addExplore);
            game.shuffleExplores();
        }
        game.removeStoredValue(getGolemExploreCardsKey(player));
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

    public static void damageMobileMountain(Game game, Player player) {
        if (game == null
                || player == null
                || !player.hasPlanet("mobilemountain")
                || !player.hasUnit("stoneborn_flagship")) {
            return;
        }
        UnitKey mobileMountain = Units.getUnitKey(UnitType.Flagship, player.getColorID());
        game.getTileMap().values().stream()
                .filter(tile -> tile.getSpaceUnitHolder().getUnitCount(mobileMountain)
                        > tile.getSpaceUnitHolder().getDamagedUnitCount(mobileMountain))
                .findFirst()
                .ifPresent(tile -> tile.addUnitDamage(Constants.SPACE, mobileMountain, 1));
    }

    public static void repairMobileMountain(Game game, Player player) {
        if (game == null
                || player == null
                || !player.hasPlanet("mobilemountain")
                || !player.hasUnit("stoneborn_flagship")) {
            return;
        }
        UnitKey mobileMountain = Units.getUnitKey(UnitType.Flagship, player.getColorID());
        game.getTileMap().values().stream()
                .filter(tile -> tile.getSpaceUnitHolder().getDamagedUnitCount(mobileMountain) > 0)
                .findFirst()
                .ifPresent(tile -> tile.removeUnitDamage(Constants.SPACE, mobileMountain, 1));
    }

    public static void syncMobileMountainDamage(Game game, Player player, UnitKey unitKey) {
        if (game == null
                || player == null
                || !player.hasPlanet("mobilemountain")
                || !player.hasUnit("stoneborn_flagship")) {
            return;
        }
        UnitKey mobileMountain = Units.getUnitKey(UnitType.Flagship, player.getColorID());
        if (!mobileMountain.equals(unitKey)) {
            return;
        }
        boolean damaged = game.getTileMap().values().stream()
                .anyMatch(tile -> tile.getSpaceUnitHolder().getDamagedUnitCount(mobileMountain) > 0);
        if (damaged) {
            player.exhaustPlanet("mobilemountain");
        } else {
            player.refreshPlanet("mobilemountain");
        }
    }

    private static boolean isMobileMountainOnBoard(Game game, Player player) {
        UnitKey mm = Units.getUnitKey(UnitType.Flagship, player.getColorID());
        return game.getTileMap().values().stream()
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .anyMatch(unitHolder -> unitHolder.getUnitCount(mm) > 0);
    }

    public static Button getStalactiumEndTurnButton(Game game, Player player) {
        int cost = getStalactiumCost(player);
        if (cost == 0
                || !game.getStoredValue(STALACTIUM_USED + player.getFaction()).isEmpty()
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
        game.setStoredValue(STALACTIUM_USED + player.getFaction(), "pending");
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
        game.setStoredValue(STALACTIUM_USED + player.getFaction(), "used");
    }

    @ButtonHandler(CANCEL_STALACTIUM)
    public static void cancelStalactium(Game game, Player player, ButtonInteractionEvent event) {
        clearStalactiumState(game, player);
        ButtonHelper.deleteMessage(event);
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
                        buttons,
                        List.of(Buttons.red(player.factionButtonChecker() + CANCEL_STALACTIUM, "Cancel")),
                        player.factionButtonChecker() + PURGE_STALACTIUM_FRAGMENT,
                        24,
                        0,
                        true));
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
        game.removeStoredValue(STALACTIUM_USED + player.getFaction());
    }

    public static void resetStalactiumTurnUse(Game game, Player player) {
        clearStalactiumState(game, player);
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
