package ti4.helpers.thundersedge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
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
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Space;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.regex.RegexService;
import ti4.service.unit.RemoveUnitService;

public class TeHelperTechs {

    // Generic Tech
    @ButtonHandler("useMagenDefense_")
    private static void useMagenDefenseGrid(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "useMagenDefense_" + RegexHelper.posRegex(game);
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String pos = matcher.group("pos");
            Tile tile = game.getTileByPosition(pos);

            int total = 0;
            UnitKey infKey = Units.getUnitKey(UnitType.Infantry, player.getColorID());

            String msg =
                    player.getFactionEmoji() + " resolved **__Magen Defense Grid__** on " + tile.getPosition() + ":";
            for (UnitHolder uh : tile.getUnitHolders().values()) {
                int count = uh.countPlayersUnitsWithModelCondition(player, UnitModel::getIsStructure);
                if (player.hasAbility("byssus")) count += uh.getUnitCount(UnitType.Mech, player);
                if (count > 0) {
                    total += count;
                    uh.addUnit(infKey, count);
                    String emoji = infKey.unitEmoji().emojiString();
                    String infStr = emoji.repeat(count);
                    if (count > 6) infStr += "(" + count + " total)";
                    if (uh instanceof Space) {
                        msg += "\n-# > " + emoji.repeat(count) + " added to Space.";
                    } else {
                        msg += "\n-# > " + emoji.repeat(count) + " added to "
                                + Helper.getPlanetRepresentation(uh.getName(), game);
                    }
                }
            }
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), String.format(msg, Integer.toString(total)));
        });
    }

    // Nanomachines
    public static List<Button> getNanomachineButtons(Player player) {
        // ACTION: Exhaust this card to place 1 PDS on a planet you control.
        // ACTION: Exhaust this card to repair all of your damaged units.
        // ACTION: Exhaust this card and discard an action card to draw 1 action card.
        String prefix = player.getFinsFactionCheckerPrefix() + "nanomachines_";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red(prefix + "pds", "Place a PDS", UnitEmojis.pds));
        buttons.add(Buttons.red(prefix + "actionCard", "Discard/draw 1 AC", CardEmojis.ActionCard));
        buttons.add(Buttons.red(prefix + "repair", "Repair units", "💥"));
        return buttons;
    }

    @ButtonHandler("nanomachines_")
    public static void resolveNanomachines(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        TechnologyModel nano = Mapper.getTech("nanomachines");
        switch (buttonID) {
            case "nanomachines_pds" -> {
                List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, "pds", "placeOneNDone_skipbuild");
                String message = "Choose a planet to place a PDS:";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
            }
            case "nanomachines_actionCard" -> {
                List<Button> buttons = ActionCardHelper.getDiscardActionCardButtonsWithSuffix(player, "redraw");
                String message =
                        "Choose an action card to discard: (The bot will automatically draw a new one afterwards)";
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
        Predicate<UnitKey> isInf = uk -> uk.getUnitType() == UnitType.Infantry;
        List<Tile> tilesWithInf = game.getTileMap().values().stream()
                .filter(t -> t.containsPlayersUnitsWithKeyCondition(player, isInf))
                .toList();
        List<Tile> tilesAdjToInf = tilesWithInf.stream()
                .flatMap(t -> FoWHelper.getAdjacentTiles(game, t.getPosition(), player, false).stream())
                .map(game::getTileByPosition)
                .filter(t -> t != null)
                .toList();
        return new ArrayList<>(tilesAdjToInf);
    }

    @ButtonHandler("startNeuralParasite")
    private static void handleNeuralParasiteStep1(ButtonInteractionEvent event, Game game, Player player) {
        // "At the start of your turn, destroy 1 of another player's infantry in or adjacent to a system that contains
        // your infantry."
        Predicate<UnitKey> isInf = uk -> uk.getUnitType() == UnitType.Infantry;
        List<Tile> tilesAdjToObsInf = tilesAdjToPlayersInf(game, player);
        List<Player> playersWithInfAdj = game.getRealPlayers().stream()
                .filter(p -> tilesAdjToObsInf.stream().anyMatch(t -> t.containsPlayersUnitsWithKeyCondition(p, isInf)))
                .toList();
        String prefixID = player.getFinsFactionCheckerPrefix() + "neuralParasiteS2_";
        List<Button> buttons = playersWithInfAdj.stream()
                .map(p -> Buttons.gray(prefixID + p.getFaction(), null, p.fogSafeEmoji()))
                .toList();
        TechnologyModel biorganic = Mapper.getTech("parasite-obs");
        String message = player.getRepresentation() + " Choose a player to remove 1 of their infantry using "
                + biorganic.getNameRepresentation() + ":";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("neuralParasiteS2_")
    private static void neuralParasiteStep2(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String part2 = "neuralParasiteS2_" + RegexHelper.factionRegex(game);
        RegexService.runMatcher(part2, buttonID, matcher -> {
            String faction = matcher.group("faction");
            Player victim = game.getPlayerFromColorOrFaction(faction);
            if (victim == null) return;

            List<Button> buttons = new ArrayList<>();
            for (Tile t : tilesAdjToPlayersInf(game, player)) {
                for (UnitHolder uh : t.getUnitHolders().values()) {
                    int count = uh.getUnitCount(UnitType.Infantry, victim);
                    if (count > 0) {
                        String id = "resolveNeuralParasite_" + t.getPosition() + "_" + uh.getName() + "_" + faction;
                        String label = uh.getName().equals("space")
                                ? "Space " + t.getPosition()
                                : Helper.getPlanetRepresentation(uh.getName(), game);
                        label += " (" + count + ")";
                        buttons.add(Buttons.red(id, label));
                    }
                }
            }

            String message = player.getRepresentation() + " choose an infantry belonging to "
                    + victim.getRepresentation(false, false) + " to destroy:";
            message += "\n-# The number in parenthesis (#) is the total number of infantry at that location.";
            MessageHelper.editMessageWithButtons(event, message, buttons);
        });
    }

    @ButtonHandler("resolveNeuralParasite_")
    private static void neuralParasiteFinish(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String part3 = "resolveNeuralParasite_" + RegexHelper.posRegex() + "_" + RegexHelper.unitHolderRegex(game, "uh")
                + "_" + RegexHelper.factionRegex(game);
        RegexService.runMatcher(part3, buttonID, matcher -> {
            String position = matcher.group("pos");
            String uhName = matcher.group("uh");
            String faction = matcher.group("faction");

            Player victim = game.getPlayerFromColorOrFaction(faction);
            Tile tile = game.getTileByPosition(position);
            if (victim == null || tile == null) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Button error, yell at jazz");
                return;
            }
            UnitHolder uh = tile.getUnitHolders().get(uhName);
            if (uh == null) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Button error, yell at jazz");
                return;
            }
            String location = "in the space area of " + tile.getRepresentationForButtons(game, player);
            if (!uhName.equals("space") && uh instanceof Planet) {
                location = "on the planet " + Helper.getPlanetRepresentation(uhName, game);
            }

            TechnologyModel biorganic = Mapper.getTech("parasite-obs");
            String message = victim.getRepresentation() + " one of your infantry " + location
                    + " has been destroyed via " + biorganic.getNameRepresentation() + ".";
            if (game.isFowMode()) {
                String privateMsg = "Successfully used " + biorganic.getNameRepresentation() + " to destroy 1 infantry "
                        + location + ".";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), privateMsg);
            }
            MessageHelper.sendMessageToChannel(victim.getCorrectChannel(), message);
            RemoveUnitService.removeUnits(event, tile, game, victim.getColorID(), "inf " + uhName);
            ButtonHelper.deleteMessage(event);
        });
    }

    public static void initializePlanesplitterStep1(Game game, Player player) {
        // ACTION: Exhaust this card to place or move an ingress token into a system that contains or is adjacent to
        // your ships.
        handlePlanesplitterStep1(game, player, null, "planesplitterStep1_page0");
    }

    @ButtonHandler("planesplitterStep1_")
    private static void handlePlanesplitterStep1(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        // ACTION: Exhaust this card to place or move an ingress token into a system that contains or is adjacent to
        // your ships.
        String buttonPrefix = player.getFinsFactionCheckerPrefix() + "planesplitterStep1_";
        List<Button> buttons = getPlanesplitterStep1Buttons(game, player);

        String message = "Pick a system to place or move an Ingress token to:";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, player.getCorrectChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }

        String regex = "planesplitterStep1_" + RegexHelper.posRegex(game);
        Matcher matcher = Pattern.compile(regex).matcher(buttonID);
        if (matcher.matches()) {
            String pos = matcher.group("pos");
            Tile t = game.getTileByPosition(pos);
            t.getSpaceUnitHolder().addToken(Constants.TOKEN_INGRESS);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Ingress token added to " + t.getRepresentation());
            initializePlanesplitterStep2(game, player);
            ButtonHelper.deleteMessage(event);
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Error with " + buttonID);
        }
    }

    private static List<Button> getPlanesplitterStep1Buttons(Game game, Player player) {
        // ACTION: Exhaust this card to place or move an ingress token into a system that contains or is adjacent to
        // your ships.
        String prefix = player.getFinsFactionCheckerPrefix() + "planesplitterStep1_";
        Set<String> adjTilePositions = new HashSet<>();
        ButtonHelper.getTilesWithShipsInTheSystem(player, game).forEach(tile -> {
            adjTilePositions.addAll(FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false));
        });
        List<Button> buttons = new ArrayList<>();
        adjTilePositions.stream().map(game::getTileByPosition).forEach(tile -> {
            buttons.add(Buttons.blue(prefix + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
        });
        return buttons;
    }

    @ButtonHandler("planesplitterStep2_")
    private static void handlePlanesplitterStep2(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        // ACTION: Exhaust this card to place or move an ingress token into a system that contains or is adjacent to
        // your ships.
        String buttonPrefix = player.getFinsFactionCheckerPrefix() + "planesplitterStep2_";
        List<Button> buttons = getPlanesplitterStep2Buttons(game, player);

        String message = "You **__MAY__** pick a different system to remove an ingress token from:";
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
                    player.getCorrectChannel(), "Ingress token removed from " + t.getRepresentation());
        }
    }

    private static void initializePlanesplitterStep2(Game game, Player player) {
        // ACTION: Exhaust this card to place or move an ingress token into a system that contains or is adjacent to
        // your ships.
        handlePlanesplitterStep2(game, player, null, "planesplitterStep2_page0");
    }

    private static List<Button> getPlanesplitterStep2Buttons(Game game, Player player) {
        // ACTION: Exhaust this card to place or move an ingress token into a system that contains or is adjacent to
        // your ships.
        String prefix = player.getFinsFactionCheckerPrefix() + "planesplitterStep2_";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.DONE_DELETE_BUTTONS.withLabel("No thanks"));

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
        buttons.add(Buttons.green(player.finChecker() + "useExecutiveOrder_top", "Reveal from the Top"));
        buttons.add(Buttons.green(player.finChecker() + "useExecutiveOrder_bottom", "Reveal from the Bottom"));
        String msg = player.getPing() + " you can reveal an agenda from the top or bottom of the deck:";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("useExecutiveOrder_top")
    private static void executiveOrderTop(ButtonInteractionEvent event, Game game, Player player) {
        game.setStoredValue("executiveOrder", player.getFaction());
        String msg = game.getPing() + " the top agenda has been revealed with Executive Order:";
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
        AgendaHelper.revealAgenda(event, false, game, event.getChannel());
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("useExecutiveOrder_bottom")
    private static void executiveOrderBottom(ButtonInteractionEvent event, Game game, Player player) {
        game.setStoredValue("executiveOrder", player.getFaction());
        String msg = game.getPing() + " the bottom agenda has been revealed with Executive Order:";
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
        AgendaHelper.revealAgenda(event, true, game, event.getChannel());
        ButtonHelper.deleteMessage(event);
    }
}
