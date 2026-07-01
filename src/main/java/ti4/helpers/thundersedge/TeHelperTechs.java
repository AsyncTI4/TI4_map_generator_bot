package ti4.helpers.thundersedge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Space;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.regex.RegexService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParsedUnit;
import ti4.service.webhook.GameWebhookNotifierFacade;

public final class TeHelperTechs {

    // Generic Tech
    @ButtonHandler("useMagenDefense_")
    private static void useMagenDefenseGrid(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "useMagenDefense_" + RegexHelper.posRegex(game);
        RegexService.runMatcher(regex, buttonID, matcher -> {
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            boolean bulwark = player.hasUnit("tk-blacktrenchbulwark");
            resolveMagen(game, player, tile, bulwark);
            ButtonHelper.deleteMessage(event);
        });
    }

    public static void revertMagen(Game game, Tile tile) {
        clearMagenPlacement(game, tile, true);
    }

    public static void clearMagenStoredValues(Game game, Tile tile) {
        clearMagenPlacement(game, tile, false);
    }

    private static void clearMagenPlacement(Game game, Tile tile, boolean removeInfantry) {
        if (tile == null) return;
        for (Player p : game.getRealPlayers()) {
            UnitKey infKey = removeInfantry ? Units.getUnitKey(UnitType.Infantry, p.getColorID()) : null;
            for (UnitHolder uh : tile.getUnitHolders().values()) {
                String stored = game.getStoredValue("magenPlaced" + p.getFaction() + uh.getName());
                if (!stored.isEmpty()) {
                    if (removeInfantry) uh.removeUnit(infKey, Integer.parseInt(stored));
                    game.removeStoredValue("magenPlaced" + p.getFaction() + uh.getName());
                }
            }
        }
    }

    public static void resolveMagen(Game game, Player player, Tile tile, boolean bulwark) {
        int total = 0;
        UnitKey infKey = Units.getUnitKey(UnitType.Infantry, player.getColorID());
        String ability = bulwark ? "_Black Trench Bulwark_" : "_Magen Defense Grid_";
        StringBuilder msg = new StringBuilder(
                player.getFactionEmoji() + " resolved " + ability + " on " + tile.getPosition() + ":");
        for (UnitHolder uh : tile.getUnitHolders().values()) {
            int count = uh.countPlayersUnitsWithModelCondition(player, UnitModel::getIsStructure);
            if (player.hasAbility("byssus")) count += uh.getUnitCount(UnitType.Mech, player);
            for (String token : uh.getTokenList()) {
                if (player.getPlanets().contains(uh.getName()) && token.contains("superweapon")) {
                    count++;
                }
            }
            if (bulwark) {
                count = uh.getUnitCount(UnitType.Pds, player);
            }
            if (count > 0) {
                total += count;
                uh.addUnit(infKey, count);
                String emoji = infKey.unitEmoji().emojiString();
                String infStr = emoji.repeat(count);
                if (count > 6) infStr += "(" + count + " total)";
                if (uh instanceof Space) {
                    msg.append("\n-# > ").append(infStr).append(" added to space.");
                } else {
                    msg.append("\n-# > ")
                            .append(infStr)
                            .append(" added to ")
                            .append(Helper.getPlanetRepresentation(uh.getName(), game))
                            .append(".");
                }
            }
        }
        if (!bulwark) {
            player.setMagenInfantryCounter(player.getMagenInfantryCounter() + total);
            msg.append("\n-# _Magen Defense Grid_ has placed ")
                    .append(player.getMagenInfantryCounter())
                    .append(" infantry this game so far.");
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg.toString());
    }

    // Nanomachines
    public static List<Button> getNanomachineButtons(Player player) {
        // ACTION: Exhaust this card to place 1 PDS on a planet you control.
        // ACTION: Exhaust this card to repair all of your damaged units.
        // ACTION: Exhaust this card and discard an action card to draw 1 action card.
        String prefix = player.factionButtonChecker() + "nanomachines_";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red(prefix + "pds", "Place a PDS", UnitEmojis.pds));
        buttons.add(Buttons.red(prefix + "actionCard", "Discard/Draw 1 Action Card", CardEmojis.getACEmoji(player)));
        buttons.add(Buttons.red(prefix + "repair", "Repair Units", "💥"));
        return buttons;
    }

    @ButtonHandler("nanomachines_")
    public static void resolveNanomachines(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        TechnologyModel nano = Mapper.getTech("nanomachines");
        switch (buttonID) {
            case "nanomachines_pds" -> {
                List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, "pds", "placeOneNDone_skipbuild");
                String message = "Please choose the planet you wish to place a PDS on.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
            }
            case "nanomachines_actionCard" -> {
                List<Button> buttons = ActionCardHelper.getDiscardActionCardButtonsWithSuffix(player, "redraw");
                String message =
                        "Please choose an action card to discard; the bot will automatically draw a new one afterwards.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
            }
            case "nanomachines_repair" -> {
                game.getTileMap().values().stream()
                        .flatMap(t -> t.getUnitHolders().values().stream())
                        .forEach(uh -> uh.removeAllUnitDamage(player.getColorID()));
                String message = player.getRepresentation() + " used " + nano.getRepresentation(false)
                        + " to repair all of their units.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            }
            default -> {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "error");
                return;
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    private static List<Tile> tilesAdjToPlayersInf(Game game, Player player) {
        Predicate<UnitKey> isInf = uk -> uk.unitType() == UnitType.Infantry;
        List<Tile> tilesWithInf = game.getTileMap().values().stream()
                .filter(t -> t.containsPlayersUnitsWithKeyCondition(player, isInf))
                .toList();
        List<Tile> tilesAdjToInf = tilesWithInf.stream()
                .flatMap(t -> FoWHelper.getAdjacentTiles(game, t.getPosition(), player, false).stream())
                .map(game::getTileByPosition)
                .filter(Objects::nonNull)
                .toList();
        return new ArrayList<>(tilesAdjToInf);
    }

    private static List<Tile> tilesContainingOrAdjacentToPlayersInf(Game game, Player player) {
        Predicate<UnitKey> isInf = uk -> uk.unitType() == UnitType.Infantry;
        Set<Tile> tilesWithInf = new HashSet<>(game.getTileMap().values().stream()
                .filter(t -> t.containsPlayersUnitsWithKeyCondition(player, isInf))
                .toList());
        Set<Tile> adjacentTiles = tilesWithInf.stream()
                .flatMap(t -> FoWHelper.getAdjacentTiles(game, t.getPosition(), player, false).stream())
                .map(game::getTileByPosition)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        tilesWithInf.addAll(adjacentTiles);
        return new ArrayList<>(tilesWithInf);
    }

    public static boolean playerHasInfantryOnMap(Game game, Player player) {
        Predicate<UnitKey> isInf = uk -> uk.unitType() == UnitType.Infantry;
        return game.getTileMap().values().stream().anyMatch(t -> t.containsPlayersUnitsWithKeyCondition(player, isInf));
    }

    public static List<Button> neuralParasiteButtons(Game game, Player player) {
        if (game.isFrankenGame()) {
            return frankenNeuralParasiteButtons(game, player);
        }

        Predicate<UnitKey> isInf = uk -> uk.unitType() == UnitType.Infantry;
        List<Tile> tilesAdjToObsInf = tilesAdjToPlayersInf(game, player);
        List<Player> playersWithInfAdj = game.getRealPlayersNNeutral().stream()
                .filter(p -> p != player
                        && tilesAdjToObsInf.stream().anyMatch(t -> t.containsPlayersUnitsWithKeyCondition(p, isInf)))
                .toList();
        String prefixID = player.factionButtonChecker() + "neuralParasiteS2_";
        return playersWithInfAdj.stream()
                .map(p -> Buttons.gray(prefixID + p.getFaction(), null, p.fogSafeEmoji()))
                .toList();
    }

    private static List<Button> frankenNeuralParasiteButtons(Game game, Player player) {
        String prefixID = player.factionButtonChecker() + "neuralParasiteS2_";
        return game.getRealPlayersNNeutral().stream()
                .filter(p -> p != player)
                .filter(p -> !frankenNeuralParasiteUnitButtons(game, player, p).isEmpty())
                .map(p -> Buttons.gray(prefixID + p.getFaction(), null, p.fogSafeEmoji()))
                .toList();
    }

    @ButtonHandler("startNeuralParasite")
    private static void handleNeuralParasiteStep1(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = neuralParasiteButtons(game, player);
        TechnologyModel biorganic = getNeuralParasiteModel(game, player);
        String message;
        if (game.isFrankenGame()) {
            message = player.getRepresentation()
                    + ", please choose a player to destroy 1 of their units on a controlled planet using "
                    + biorganic.getNameRepresentation() + ".";
        } else {
            // "At the start of your turn, destroy 1 of another player's infantry in or adjacent to a system that
            // contains
            // your infantry."
            message = player.getRepresentation() + ", please choose a player to remove 1 of their infantry using "
                    + biorganic.getNameRepresentation() + ".";
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    private static List<Button> getButtonsForEachUnitState(
            Game game, Tile tile, UnitHolder uh, UnitKey unit, String prefix) {
        List<Button> buttons = new ArrayList<>();
        for (UnitState state : UnitState.defaultRemoveOrder()) {
            int count = uh.getUnitCountForState(unit, state);
            if (count > 0) {
                String id = prefix + "_" + state.name();
                String label = "space".equals(uh.getName())
                        ? "Space " + tile.getPosition()
                        : Helper.getPlanetRepresentation(uh.getName(), game);
                label += " (" + count + ")";
                if (state != UnitState.none) {
                    label += " [" + state.humanDescr() + "]";
                }
                buttons.add(Buttons.red(id, label, state.stateEmoji()));
            }
        }
        return buttons;
    }

    @ButtonHandler("neuralParasiteS2_")
    private static void neuralParasiteStep2(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String faction = buttonID.split("_")[1];
        Player victim = game.getPlayerFromColorOrFaction(faction);
        if (victim == null) return;

        if (game.isFrankenGame()) {
            List<Button> buttons = frankenNeuralParasiteUnitButtons(game, player, victim);
            String message = player.getRepresentation() + " is choosing one unit belonging to "
                    + victim.getRepresentation(false, false) + " to destroy with _Neural Parasite_.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message, buttons);
            ButtonHelper.deleteMessage(event);
            return;
        }

        UnitKey inf = Units.getUnitKey(UnitType.Infantry, victim.getColor());

        List<Button> buttons = new ArrayList<>();
        for (Tile t : tilesAdjToPlayersInf(game, player)) {
            for (UnitHolder uh : t.getUnitHolders().values()) {
                String id = "resolveNeuralParasite_" + t.getPosition() + "_" + uh.getName() + "_" + faction;
                buttons.addAll(getButtonsForEachUnitState(game, t, uh, inf, id));
            }
        }

        String message = player.getRepresentation() + " is choosing an infantry belonging to "
                + victim.getRepresentation(false, false) + " to destroy.";
        message += "\n-# The number in parenthesis (#) is the total number of infantry at that location.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> frankenNeuralParasiteUnitButtons(Game game, Player player, Player victim) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : tilesContainingOrAdjacentToPlayersInf(game, player)) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (!victim.getPlanets().contains(planet.getName())) {
                    continue;
                }
                for (UnitKey unit : planet.getUnitKeys()) {
                    if (!victim.unitBelongsToPlayer(unit)) {
                        continue;
                    }
                    buttons.addAll(frankenNeuralParasiteStateButtons(game, tile, planet, victim, unit));
                }
            }
        }
        return buttons;
    }

    private static List<Button> frankenNeuralParasiteStateButtons(
            Game game, Tile tile, Planet planet, Player victim, UnitKey unit) {
        List<Button> buttons = new ArrayList<>();
        for (UnitState state : UnitState.defaultRemoveOrder()) {
            int count = planet.getUnitCountForState(unit, state);
            if (count <= 0) {
                continue;
            }

            String id = String.join(
                    ";",
                    "resolveFrankenNeuralParasite",
                    tile.getPosition(),
                    planet.getName(),
                    victim.getFaction(),
                    unit.unitTypeVal(),
                    state.name());
            String label =
                    Helper.getPlanetRepresentation(planet.getName(), game) + " (" + count + ") " + unit.unitEmoji();
            if (state != UnitState.none) {
                label += " [" + state.humanDescr() + "]";
            }
            buttons.add(Buttons.red(id, label, state.stateEmoji()));
        }
        return buttons;
    }

    @ButtonHandler("resolveNeuralParasite_")
    private static void neuralParasiteFinish(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String part3 = "resolveNeuralParasite_" + RegexHelper.posRegex() + "_" + RegexHelper.unitHolderRegex(game, "uh")
                + "_" + RegexHelper.factionRegex(game) + "_" + RegexHelper.unitStateRegex();
        RegexService.runMatcher(part3, buttonID, matcher -> {
            String position = matcher.group("pos");
            String uhName = matcher.group("uh");
            String faction = matcher.group("faction");

            Player victim = game.getPlayerFromColorOrFaction(faction);
            Tile tile = game.getTileByPosition(position);
            if (victim == null || tile == null) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(), "This Neural Parasite button is no longer valid.");
                return;
            }
            UnitHolder uh = tile.getUnitHolders().get(uhName);
            if (uh == null) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(), "This Neural Parasite button is no longer valid.");
                return;
            }
            String location = "in the space area of " + tile.getRepresentationForButtons(game, player);
            if (!"space".equals(uhName) && uh instanceof Planet) {
                location = "on the planet " + Helper.getPlanetRepresentation(uhName, game);
            }

            TechnologyModel biorganic = getNeuralParasiteModel(game, player);
            String bioorganicRepresentation = biorganic.getNameRepresentation();
            String message = victim.getRepresentationUnfogged() + ", one of your infantry " + location
                    + " has been destroyed via " + bioorganicRepresentation + ".";
            if (game.isFowMode()) {
                String privateMsg =
                        "Successfully used " + bioorganicRepresentation + " to destroy 1 infantry " + location + ".";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), privateMsg);
            }
            MessageHelper.sendMessageToChannel(victim.getCorrectChannel(), message);
            DestroyUnitService.destroyUnits(event, tile, game, victim.getColorID(), "inf " + uhName, false);
            ButtonHelper.deleteMessage(event);
        });
    }

    @ButtonHandler("resolveFrankenNeuralParasite;")
    private static void frankenNeuralParasiteFinish(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.split(";");
        if (parts.length != 6) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "This Neural Parasite button is no longer valid.");
            return;
        }

        String position = parts[1];
        String planetName = parts[2];
        String faction = parts[3];
        String unitType = parts[4];
        String stateName = parts[5];

        Player victim = game.getPlayerFromColorOrFaction(faction);
        Tile tile = game.getTileByPosition(position);
        if (victim == null || tile == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "This Neural Parasite button is no longer valid.");
            return;
        }

        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        if (!(unitHolder instanceof Planet)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "This Neural Parasite button is no longer valid.");
            return;
        }

        UnitState preferredState = UnitState.valueOf(stateName);
        UnitKey unitKey = Mapper.getUnitKey(unitType, victim.getColorID());
        UnitModel unitModel = victim.getUnitFromUnitKey(unitKey);
        if (unitModel == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "This Neural Parasite button is no longer valid.");
            return;
        }

        TechnologyModel biorganic = getNeuralParasiteModel(game, player);
        String bioorganicRepresentation = biorganic.getNameRepresentation();
        String location = Helper.getPlanetRepresentation(planetName, game);
        String message = victim.getRepresentationUnfogged() + ", one of your " + unitModel.getName() + " on " + location
                + " has been destroyed via " + bioorganicRepresentation + ".";
        if (game.isFowMode()) {
            String privateMsg = "Successfully used " + bioorganicRepresentation + " to destroy 1 " + unitModel.getName()
                    + " on " + location + ".";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), privateMsg);
        }
        MessageHelper.sendMessageToChannel(victim.getCorrectChannel(), message);
        DestroyUnitService.destroyUnit(
                event, tile, game, new ParsedUnit(unitKey, 1, planetName), false, preferredState);
        ButtonHelper.deleteMessage(event);
    }

    private static TechnologyModel getNeuralParasiteModel(Game game, Player player) {
        if (game.isFrankenGame()) {
            if (player.hasTech("parasite-obs_y") || player.getFactionTechs().contains("parasite-obs_y")) {
                return Mapper.getTech("parasite-obs_y");
            }
        }
        return Mapper.getTech("parasite-obs");
    }

    public static void initializePlanesplitterStep1(Game game, Player player) {
        // When you perform a strategic action, you may move an ingress token into a system that contains or is adjacent
        // to your units.\nThis technology cannot be researched.
        handlePlanesplitterStep1(game, player, null, "planesplitterStep1_page0");
    }

    @ButtonHandler("planesplitterStep1_")
    public static void handlePlanesplitterStep1(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        // When you perform a strategic action, you may move an ingress token into a system that contains or is adjacent
        // to your units.\nThis technology cannot be researched.
        String buttonPrefix = player.factionButtonChecker() + "planesplitterStep1_";
        List<Button> buttons = getPlanesplitterStep1Buttons(game, player);

        String message = "Please choose a system to move an Ingress token into.";
        if (event == null) {
            if (game.isTwilightsFallMode()) {
                message = "Please choose a system to add an Ingress token into.";
            }
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message, buttons);
            return;
        }

        String regex = "planesplitterStep1_" + RegexHelper.posRegex(game);
        Matcher matcher = Pattern.compile(regex).matcher(buttonID);
        if (matcher.matches()) {
            String pos = matcher.group("pos");
            Tile t = game.getTileByPosition(pos);
            t.getSpaceUnitHolder().addToken(Constants.TOKEN_INGRESS);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Ingress token added to " + t.getRepresentation() + ".");
            initializePlanesplitterStep2(game, player, event);
            ButtonHelper.deleteMessage(event);
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Error with " + buttonID);
        }
    }

    private static List<Button> getPlanesplitterStep1Buttons(Game game, Player player) {
        // ACTION: Exhaust this card to place or move an ingress token into a system that contains or is adjacent to
        // your ships.
        String prefix = player.factionButtonChecker() + "planesplitterStep1_";
        List<Button> buttons = new ArrayList<>();
        if (player.hasUnlockedBreakthrough("cabalbt")) {
            for (Tile tile : game.getTileMap().values()) {
                if (tile.isGravityRift(game, player)) {
                    buttons.add(
                            Buttons.blue(prefix + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
                }
            }
        } else {
            Set<String> adjTilePositions = new HashSet<>();
            if (game.isTwilightsFallMode()) {
                for (Tile tile : game.getTileMap().values()) {
                    if (FoWHelper.playerHasUnitsInSystem(player, tile)) {
                        adjTilePositions.add(tile.getPosition());
                    }
                }
            } else {
                for (Tile tile : game.getTileMap().values()) {
                    if (FoWHelper.playerHasUnitsInSystem(player, tile)) {
                        adjTilePositions.addAll(
                                FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true));
                    }
                }
            }

            adjTilePositions.stream()
                    .map(game::getTileByPosition)
                    .forEach(tile -> buttons.add(
                            Buttons.blue(prefix + tile.getPosition(), tile.getRepresentationForButtons(game, player))));
        }

        return buttons;
    }

    @ButtonHandler("planesplitterStep2_")
    private static void handlePlanesplitterStep2(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {

        String buttonPrefix = player.factionButtonChecker() + "planesplitterStep2_";
        List<Button> buttons = getPlanesplitterStep2Buttons(game, player);

        String message = "Please choose the system to move the ingress token from.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, player.getCorrectChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }

        String regex = "planesplitterStep2_" + RegexHelper.posRegex(game);
        Matcher matcher = Pattern.compile(regex).matcher(buttonID);
        if (matcher.matches()) {
            String pos = matcher.group("pos");
            Tile t = game.getTileByPosition(pos);
            t.getSpaceUnitHolder().removeToken(Constants.TOKEN_INGRESS);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Ingress token removed from " + t.getRepresentation() + ".");
            ButtonHelper.deleteMessage(event);
        }
    }

    private static void initializePlanesplitterStep2(Game game, Player player, ButtonInteractionEvent event) {
        // ACTION: Exhaust this card to place or move an ingress token into a system that contains or is adjacent to
        // your ships.
        if (game.isTwilightsFallMode()) {
            return;
        }
        handlePlanesplitterStep2(game, player, event, "planesplitterStep2_page0");
    }

    private static List<Button> getPlanesplitterStep2Buttons(Game game, Player player) {
        // ACTION: Exhaust this card to place or move an ingress token into a system that contains or is adjacent to
        // your ships.
        String prefix = player.factionButtonChecker() + "planesplitterStep2_";
        List<Button> buttons = new ArrayList<>();

        game.getTileMap().values().forEach(tile -> {
            UnitHolder space = tile.getUnitHolders().get("space");
            if (space.getTokenList().contains(Constants.TOKEN_INGRESS))
                buttons.add(Buttons.red(prefix + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
        });
        return buttons;
    }

    public static void postExecutiveOrderButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(player.factionButtonChecker() + "useExecutiveOrder_top", "Reveal Agenda From Top"));
        buttons.add(
                Buttons.green(player.factionButtonChecker() + "useExecutiveOrder_bottom", "Reveal Agenda From Bottom"));
        String msg = player.getPing()
                + ", please choose if you wish to reveal the agenda from the top or from the bottom of the agenda deck.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("useExecutiveOrder_")
    private static void executiveOrder(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        game.setStoredValue("executiveOrder", player.getFaction());
        String previousPhaseOfGame = game.getPhaseOfGame();
        game.setPhaseOfGame("agenda");
        GameWebhookNotifierFacade.phaseChanged(game, previousPhaseOfGame, "agenda");
        boolean top = buttonID.contains("top");

        String msg = game.getPing() + " the " + buttonID.split("_")[1]
                + " agenda has been revealed with _Executive Order_. ";

        if (game.isFowMode()) {
            msg += "Speaker has been temporarily changed to simulate the effect of the technology.";
        } else {
            msg += player.getRepresentation()
                    + " has been temporarily made speaker to simulate the effect of the technology.";
        }
        game.setStoredValue("oldSpeakerExecutiveOrder", game.getSpeakerUserID());
        game.setSpeaker(player);

        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
        AgendaHelper.revealAgenda(event, !top, game, event.getChannel());
        ButtonHelper.deleteMessage(event);
    }
}
