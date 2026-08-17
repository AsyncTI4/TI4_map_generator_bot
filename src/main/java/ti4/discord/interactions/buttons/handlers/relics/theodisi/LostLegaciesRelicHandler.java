package ti4.discord.interactions.buttons.handlers.relics.theodisi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponentUnion;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.CaptureUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class LostLegaciesRelicHandler {
    // Economic Boon
    private static final String USE_EBOON = "useEconomicBoon";
    // Nature's Boon
    private static final String USE_NBOON = "useNaturesBoon_";
    private static final String CHOOSE_NBOON_PLANET = "chooseNaturesBoonPlanet_";
    // Diplomatic Boon
    private static final String CHOOSE_DBOON_PLANET = "chooseDiplomaticBoonPlanet_";
    private static final String PLACE_UNIT_DBOON = "placeUnitWithDiplomaticBoon_";
    private static final String FINISH_DBOON = "finishDiplomaticBoonPlacement";
    // Cosmic Boon
    private static final String USE_CBOON = "useCosmicBoon";
    // Ancient Radar
    private static final String RADAR_EXPLORE = "exploreAncientRadar_";
    // Horn of the Abyss
    private static final String PLACE_NEUTRAL = "hornOfTheAbyssPlace_";
    private static final String CHOOSE_HORN_SYSTEM = "chooseHornOfTheAbyssSystem_";
    private static final String PLACE_HORN_SHIP = "placeHornOfTheAbyssShip_";
    private static final String DONE_HORN_SHIPS = "donePlacingHornOfTheAbyssShips";
    private static final String HORN_SYSTEM = "hornOfTheAbyssSystem_";
    private static final String HORN_COST = "hornOfTheAbyssCost_";
    private static final String HORN_SHIPS = "hornOfTheAbyssShips_";
    private static final String HORN_REPLACEMENT_BATCH = "hornOfTheAbyssReplacementBatch";
    private static final String HORN_REPLACEMENT_CHOICES = "hornOfTheAbyssReplacementChoices_";
    private static final int HORN_COST_LIMIT = 8;

    // Horn of the Abyss
    public static void offerNeutralReplacement(
            GenericInteractionCreateEvent event, Game game, List<RemovedUnit> destroyedUnits) {
        Map<Player, List<RemovedUnit>> destroyedByKiller = new LinkedHashMap<>();
        for (RemovedUnit destroyedUnit : destroyedUnits) {
            if (!Mapper.getColorID(game.getNeutralColor())
                    .equals(destroyedUnit.unitKey().colorID())) {
                continue;
            }
            List<Player> possibleKillers = CaptureUnitService.listProbableKiller(game, destroyedUnit).stream()
                    .filter(killer -> destroyedUnit.uh().getUnitKeys().stream().anyMatch(killer::unitBelongsToPlayer))
                    .distinct()
                    .toList();
            if (possibleKillers.size() == 1 && possibleKillers.getFirst().hasRelic("horn_of_the_abyss")) {
                destroyedByKiller
                        .computeIfAbsent(possibleKillers.getFirst(), ignored -> new ArrayList<>())
                        .add(destroyedUnit);
            }
        }

        for (Map.Entry<Player, List<RemovedUnit>> entry : destroyedByKiller.entrySet()) {
            Player killer = entry.getKey();
            Map<String, RemovedUnit> replacementChoices = getNeutralReplacementChoices(killer, entry.getValue());
            if (replacementChoices.isEmpty()) {
                continue;
            }
            if (!game.getStoredValue(HORN_REPLACEMENT_BATCH).isEmpty()) {
                String key = HORN_REPLACEMENT_CHOICES + killer.getFaction();
                List<String> choices =
                        new ArrayList<>(List.of(game.getStoredValue(key).split(",")));
                choices.removeIf(String::isEmpty);
                replacementChoices.keySet().forEach(choice -> {
                    if (!choices.contains(choice)) {
                        choices.add(choice);
                    }
                });
                game.setStoredValue(key, String.join(",", choices));
                continue;
            }
            sendNeutralReplacementPrompt(event, game, killer, replacementChoices.keySet());
        }
    }

    public static void beginNeutralReplacementBatch(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(HORN_REPLACEMENT_CHOICES + player.getFaction());
        }
        game.setStoredValue(HORN_REPLACEMENT_BATCH, "yes");
    }

    public static void finishNeutralReplacementBatch(GenericInteractionCreateEvent event, Game game) {
        game.removeStoredValue(HORN_REPLACEMENT_BATCH);
        for (Player player : game.getRealPlayers()) {
            String key = HORN_REPLACEMENT_CHOICES + player.getFaction();
            List<String> choices =
                    new ArrayList<>(List.of(game.getStoredValue(key).split(",")));
            choices.removeIf(String::isEmpty);
            game.removeStoredValue(key);
            sendNeutralReplacementPrompt(event, game, player, choices);
        }
    }

    private static Map<String, RemovedUnit> getNeutralReplacementChoices(
            Player killer, List<RemovedUnit> destroyedUnits) {
        Map<String, RemovedUnit> replacementChoices = new LinkedHashMap<>();
        boolean canPlaceWarSun = killer.hasTech("ws")
                || killer.hasTech("absol_ws")
                || killer.getUnitModels().stream()
                        .anyMatch(unit -> unit.getUnitType() == UnitType.Warsun
                                && unit.getFaction().isPresent());
        for (RemovedUnit destroyedUnit : destroyedUnits) {
            if (destroyedUnit.unitKey().unitType() == UnitType.Warsun && !canPlaceWarSun) {
                continue;
            }
            String payload = destroyedUnit.tile().getPosition() + "|"
                    + destroyedUnit.unitKey().unitName();
            replacementChoices.putIfAbsent(payload, destroyedUnit);
        }
        return replacementChoices;
    }

    private static void sendNeutralReplacementPrompt(
            GenericInteractionCreateEvent event, Game game, Player killer, Iterable<String> choices) {
        List<Button> buttons = new ArrayList<>();
        for (String choice : choices) {
            String[] payload = choice.split("\\|", 2);
            if (payload.length != 2 || game.getTileByPosition(payload[0]) == null) {
                continue;
            }
            var unitKey = Units.getUnitKey(payload[1], killer.getColor());
            if (unitKey == null) {
                continue;
            }
            buttons.add(Buttons.green(
                    killer.factionButtonChecker() + PLACE_NEUTRAL + choice, "Place 1 " + unitKey.humanReadableName()));
        }
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                killer.getRepresentationNoPing()
                        + ", choose 1 destroyed neutral unit to replace with _Horn of the Abyss_.\n"
                        + "-# Selecting a unit removes every other choice from this batch.",
                buttons);
    }

    @ButtonHandler(PLACE_NEUTRAL)
    public static void placeNeutralReplacement(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasRelic("horn_of_the_abyss")) {
            return;
        }

        String[] payload = buttonID.substring(PLACE_NEUTRAL.length()).split("\\|", 2);
        if (payload.length != 2) {
            return;
        }

        Tile tile = game.getTileByPosition(payload[0]);
        if (tile == null) {
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + payload[1]);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " used _Horn of the Abyss_ and placed 1 "
                        + payload[1]
                        + " in "
                        + tile.getRepresentationForButtons(game, player)
                        + ".");

        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getHornOfTheAbyssSystemButtons(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.getTileModel().isHyperlane())
                .filter(tile -> !hasOtherPlayersShips(tile, game, player))
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + CHOOSE_HORN_SYSTEM + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    @ButtonHandler(CHOOSE_HORN_SYSTEM)
    public static void chooseHornOfTheAbyssSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasRelic("horn_of_the_abyss")
                || !player.getExhaustedRelics().contains("horn_of_the_abyss")) {
            return;
        }

        List<Button> systemButtons = getHornOfTheAbyssSystemButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", please choose the system in which to place neutral ships with _Horn of the Abyss_.\n"
                + "-# You may place neutral ships with a combined cost of 4 or less.";
        String buttonPrefix = player.factionButtonChecker() + CHOOSE_HORN_SYSTEM;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), systemButtons, message, buttonPrefix, buttonID)) {
            return;
        }

        String position = buttonID.substring(CHOOSE_HORN_SYSTEM.length());
        Tile tile = game.getTileByPosition(position);
        if (tile == null || tile.getTileModel().isHyperlane() || hasOtherPlayersShips(tile, game, player)) {
            return;
        }

        game.setStoredValue(HORN_SYSTEM + player.getFaction(), position);
        game.setStoredValue(HORN_COST + player.getFaction(), "0");
        game.removeStoredValue(HORN_SHIPS + player.getFaction());
        sendHornOfTheAbyssShipButtons(event, game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_HORN_SHIP)
    public static void placeHornOfTheAbyssShip(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = game.getStoredValue(HORN_SYSTEM + player.getFaction());
        Tile tile = position.isEmpty() ? null : game.getTileByPosition(position);
        UnitModel ship = game.getNeutral().getUnitFromAsyncID(buttonID.substring(PLACE_HORN_SHIP.length()));
        if (!player.getExhaustedRelics().contains("horn_of_the_abyss")
                || tile == null
                || ship == null
                || !ship.getIsShip()
                || hasOtherPlayersShips(tile, game, player)) {
            return;
        }

        int totalCost = getHornCost(game, player);
        int shipCost = getHornShipCost(ship);
        if (shipCost <= 0 || totalCost + shipCost > HORN_COST_LIMIT) {
            return;
        }

        AddUnitService.addUnits(event, tile, game, game.getNeutralColor(), "1 " + ship.getAsyncId());
        game.setStoredValue(HORN_COST + player.getFaction(), Integer.toString(totalCost + shipCost));
        String shipList = game.getStoredValue(HORN_SHIPS + player.getFaction());
        game.setStoredValue(
                HORN_SHIPS + player.getFaction(),
                shipList.isEmpty() ? ship.getName() : shipList + ", " + ship.getName());
        sendHornOfTheAbyssShipButtons(event, game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DONE_HORN_SHIPS)
    public static void finishHornOfTheAbyssShips(ButtonInteractionEvent event, Game game, Player player) {
        String position = game.getStoredValue(HORN_SYSTEM + player.getFaction());
        String shipList = game.getStoredValue(HORN_SHIPS + player.getFaction());
        int totalCost = getHornCost(game, player);
        Tile tile = position.isEmpty() ? null : game.getTileByPosition(position);
        game.removeStoredValue(HORN_SYSTEM + player.getFaction());
        game.removeStoredValue(HORN_COST + player.getFaction());
        game.removeStoredValue(HORN_SHIPS + player.getFaction());

        if (!shipList.isEmpty() && tile != null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " exhausted _Horn of the Abyss_ and placed " + shipList + " in "
                            + tile.getRepresentationForButtons(game, player) + " (combined cost "
                            + formatHornCost(totalCost) + ").");
        }
        ButtonHelper.deleteMessage(event);
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
    }

    private static void sendHornOfTheAbyssShipButtons(ButtonInteractionEvent event, Game game, Player player) {
        int totalCost = getHornCost(game, player);
        List<Button> shipButtons = game.getNeutral().getUnitModels().stream()
                .filter(UnitModel::getIsShip)
                .filter(ship -> getHornShipCost(ship) > 0 && totalCost + getHornShipCost(ship) <= HORN_COST_LIMIT)
                .sorted(Comparator.comparing(UnitModel::getCost).thenComparing(UnitModel::getName))
                .map(ship -> Buttons.green(
                        player.factionButtonChecker() + PLACE_HORN_SHIP + ship.getAsyncId(),
                        "Place 1 " + ship.getName() + " (Cost " + formatHornCost(getHornShipCost(ship)) + ")",
                        ship.getUnitEmoji()))
                .toList();
        shipButtons = new ArrayList<>(shipButtons);
        shipButtons.add(Buttons.red(player.factionButtonChecker() + DONE_HORN_SHIPS, "Done Placing Ships"));

        String message =
                player.getRepresentationNoPing() + ", please choose neutral ships to place with _Horn of the Abyss_.\n"
                        + "-# Combined cost: " + formatHornCost(totalCost) + "/4.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, shipButtons);
    }

    private static int getHornShipCost(UnitModel ship) {
        return Math.round(ship.getCost() * 2);
    }

    private static int getHornCost(Game game, Player player) {
        try {
            return Integer.parseInt(game.getStoredValue(HORN_COST + player.getFaction()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void clearHornOfTheAbyssState(Game game) {
        game.removeStoredValue(HORN_REPLACEMENT_BATCH);
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(HORN_SYSTEM + player.getFaction());
            game.removeStoredValue(HORN_COST + player.getFaction());
            game.removeStoredValue(HORN_SHIPS + player.getFaction());
            game.removeStoredValue(HORN_REPLACEMENT_CHOICES + player.getFaction());
        }
    }

    private static String formatHornCost(int halfCost) {
        return halfCost % 2 == 0 ? Integer.toString(halfCost / 2) : halfCost / 2 + ".5";
    }

    private static boolean hasOtherPlayersShips(Tile tile, Game game, Player player) {
        return tile.getSpaceUnitHolder().getUnitKeys().stream().anyMatch(unitKey -> {
            Player owner = game.getPlayerFromColorOrFaction(unitKey.colorID());
            UnitModel model = owner == null ? null : owner.getUnitFromUnitKey(unitKey);
            return owner != null && !owner.isNeutral() && owner != player && model != null && model.getIsShip();
        });
    }

    // Ancient Radar
    public static List<Button> getAncientRadarPlanets(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            Tile tile = game.getTileFromPlanet(planetName);
            if (planet != null
                    && tile != null
                    && !planet.isHomePlanet()
                    && !tile.isMecatol(game)
                    && !planet.isLegendary()) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + RADAR_EXPLORE + planetName, planet.getRepresentation(game)));
            }
        }

        return buttons;
    }

    @ButtonHandler(RADAR_EXPLORE)
    public static void resolveAncientRadarExplore(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }
        if (!player.hasRelic("ancient_radar") || !player.getExhaustedRelics().contains("ancient_radar")) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String message = player.getRepresentationNoPing()
                + ", please choose a non-home planet for _Ancient Radar_ to explore once as each planet trait.";
        String buttonPrefix = player.factionButtonChecker() + RADAR_EXPLORE;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                getAncientRadarPlanets(event, game, player),
                message,
                buttonPrefix,
                buttonID)) {
            return;
        }

        String planetName = buttonID.substring(RADAR_EXPLORE.length());
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (!player.getPlanets().contains(planetName)
                || tile == null
                || planet == null
                || planet.isHomePlanet()
                || tile.isMecatol(game)
                || planet.isLegendary()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        for (String trait : List.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL)) {
            ExploreService.explorePlanet(event, tile, planetName, trait, player, true, game, 1, false);
        }

        ButtonHelper.deleteMessage(event);
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
    }

    // Cosmic Boon
    public static Button getCosmicBoonButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + USE_CBOON, "Exhaust Cosmic Boon");
    }

    @ButtonHandler(USE_CBOON)
    public static void resolveCosmicBoon(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasRelicReady("cosmicboon")) {
            return;
        }

        player.addExhaustedRelic("cosmicboon");
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", you may use these buttons to explore 2 planets you control.",
                ButtonHelper.getButtonsToExploreAllPlanets(player, game));

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    // Diplomatic Boon
    public static List<Button> getDiplomaticBoonPlanets(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return List.of();
        }

        List<Button> planets = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            Tile tile = game.getTileFromPlanet(planetName);
            if (planet != null
                    && !planet.isHomePlanet()
                    && tile != null
                    && !tile.isMecatol()
                    && FoWHelper.playerHasShipsInSystem(player, tile)) {
                planets.add(Buttons.green(
                        player.factionButtonChecker() + CHOOSE_DBOON_PLANET + planetName,
                        planet.getRepresentation(game)));
            }
        }

        return planets;
    }

    @ButtonHandler(CHOOSE_DBOON_PLANET)
    public static void chooseDBoonInfantryOrFighter(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String planetName = buttonID.replace(CHOOSE_DBOON_PLANET, "");
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (planet == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find planet.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", place up to " + planet.getInfluence()
                        + " total infantry and fighters using _Diplomatic Boon_ on "
                        + planet.getRepresentation(game) + ".",
                getDiplomaticBoonPlacementButtons(game, player, planetName, planet.getInfluence()));

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_UNIT_DBOON)
    public static void placeUnitUsingDiplomaticBoon(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String[] payload = buttonID.replace(PLACE_UNIT_DBOON, "").split("~", 4);
        if (payload.length != 4) {
            return;
        }

        String planetName = payload[0];
        int remaining;
        try {
            remaining = Integer.parseInt(payload[1]);
        } catch (NumberFormatException e) {
            return;
        }
        if (remaining < 1) {
            return;
        }

        String unit = payload[2];
        String destination = payload[3];
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet == null || tile == null || (!"infantry".equals(unit) && !"fighter".equals(unit))) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find planet or tile.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        if ("infantry".equals(unit)) {
            Planet destinationPlanet = game.getUnitHolderFromPlanet(destination);
            Tile destinationTile = game.getTileFromPlanet(destination);
            if (destinationPlanet == null
                    || destinationTile == null
                    || !player.getPlanets().contains(destination)
                    || !tile.getPosition().equals(destinationTile.getPosition())) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(), "Could not find an eligible destination planet.");
                ButtonHelper.deleteMessage(event);
                return;
            }
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + destination);
        } else {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 fighter");
        }

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " placed 1 " + unit + " using _Diplomatic Boon_.");

        if (remaining > 1) {
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentation() + " may place " + (remaining - 1)
                            + " more total infantry and fighters using _Diplomatic Boon_.",
                    getDiplomaticBoonPlacementButtons(game, player, planetName, remaining - 1));
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(FINISH_DBOON)
    public static void finishDiplomaticBoonPlacement(ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getDiplomaticBoonPlacementButtons(
            Game game, Player player, String planetName, int remaining) {
        Tile tile = game.getTileFromPlanet(planetName);
        if (tile == null) {
            return List.of(Buttons.red(player.factionButtonChecker() + FINISH_DBOON, "Done Placing"));
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + PLACE_UNIT_DBOON + planetName + "~" + remaining + "~fighter~space",
                "Place 1 Fighter (" + remaining + " remaining)",
                UnitEmojis.fighter));
        for (String controlledPlanet : player.getPlanets()) {
            Planet destination = game.getUnitHolderFromPlanet(controlledPlanet);
            Tile destinationTile = game.getTileFromPlanet(controlledPlanet);
            if (destination != null
                    && destinationTile != null
                    && tile.getPosition().equals(destinationTile.getPosition())) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + PLACE_UNIT_DBOON + planetName + "~" + remaining + "~infantry~"
                                + controlledPlanet,
                        "Place 1 Infantry on " + destination.getRepresentation(game) + " (" + remaining + " remaining)",
                        UnitEmojis.infantry));
            }
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + FINISH_DBOON, "Done Placing"));
        return buttons;
    }

    // Economic Boon
    public static Button getEconomicBoonCardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_EBOON, "Ready Planet with Economic Boon", CardEmojis.RelicCard);
    }

    public static Button getNaturesBoonSpendButton(Player player, String whatIsItFor) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_NBOON + whatIsItFor, "Use Nature's Boon", CardEmojis.RelicCard);
    }

    @ButtonHandler(USE_EBOON)
    public static void resolveEconomicBoon(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasRelicReady("economicboon")) {
            return;
        }

        List<Button> buttons = Helper.getPlanetRefreshButtons(player, game);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        player.addExhaustedRelic("economicboon");
        buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Readying"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", please choose the exhausted planet you wish to ready with _Economic Boon_.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(USE_NBOON)
    public static void offerNaturesBoonPlanets(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasRelicReady("naturesboon")) {
            return;
        }

        String whatIsItFor = buttonID.substring(USE_NBOON.length());
        String paymentMessageId = event.getMessageId();
        List<Button> buttons = getNaturesBoonPlanetButtons(player, game, whatIsItFor, paymentMessageId);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", spend a planet before using _Nature's Boon_.");
            return;
        }

        String message =
                player.getRepresentation() + ", choose a planet already spent for this payment with _Nature's Boon_.";
        String buttonPrefix =
                player.factionButtonChecker() + CHOOSE_NBOON_PLANET + whatIsItFor + "|" + paymentMessageId + "|";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), message, NewStuffHelper.buttonPagination(buttons, buttonPrefix, 0));
    }

    @ButtonHandler(CHOOSE_NBOON_PLANET)
    public static void resolveNaturesBoon(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasRelicReady("naturesboon")) {
            return;
        }

        String payload = buttonID.substring(CHOOSE_NBOON_PLANET.length());
        String[] data = payload.split("\\|", 3);
        if (data.length != 3) {
            return;
        }
        String whatIsItFor = data[0];
        String paymentMessageId = data[1];
        String planetName = data[2];
        List<Button> buttons = getNaturesBoonPlanetButtons(player, game, whatIsItFor, paymentMessageId);
        String message =
                player.getRepresentation() + ", choose a planet already spent for this payment with _Nature's Boon_.";
        String buttonPrefix =
                player.factionButtonChecker() + CHOOSE_NBOON_PLANET + whatIsItFor + "|" + paymentMessageId + "|";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }
        if (!player.getSpentThingsThisWindow().contains(planetName)
                || !player.getExhaustedPlanets().contains(planetName)
                || !game.getPlanetsInfo().containsKey(planetName)) {
            return;
        }

        player.addExhaustedRelic("naturesboon");
        player.addSpentThing("naturesboon_" + planetName);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " exhausted _Nature's Boon_ for "
                        + Helper.getPlanetRepresentation(planetName, game) + ".");
        event.getChannel()
                .retrieveMessageById(paymentMessageId)
                .queue(
                        paymentMessage -> {
                            List<Button> paymentButtons = new ArrayList<>();
                            for (ActionRow row :
                                    paymentMessage.getComponentTree().findAll(ActionRow.class)) {
                                for (ActionRowChildComponentUnion component : row.getComponents()) {
                                    if (component instanceof Button button
                                            && (button.getCustomId() == null
                                                    || !button.getCustomId().contains(USE_NBOON))) {
                                        paymentButtons.add(button);
                                    }
                                }
                            }
                            paymentMessage
                                    .editMessage(Helper.buildSpentThingsMessage(player, game, whatIsItFor))
                                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(paymentButtons))
                                    .queue(Consumers.nop(), BotLogger::catchRestError);
                        },
                        BotLogger::catchRestError);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getNaturesBoonPlanetButtons(
            Player player, Game game, String whatIsItFor, String paymentMessageId) {
        List<Button> buttons = new ArrayList<>();
        for (String spentThing : player.getSpentThingsThisWindow()) {
            if (!player.getExhaustedPlanets().contains(spentThing)
                    || !game.getPlanetsInfo().containsKey(spentThing)) {
                continue;
            }
            buttons.add(Buttons.gray(
                    player.factionButtonChecker()
                            + CHOOSE_NBOON_PLANET
                            + whatIsItFor
                            + "|"
                            + paymentMessageId
                            + "|"
                            + spentThing,
                    Helper.getPlanetRepresentation(spentThing, game)));
        }
        return buttons;
    }
}
