package ti4.helpers.thundersedge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
import ti4.helpers.Units.UnitState;
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
import ti4.service.unit.DestroyUnitService;

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

            StringBuilder msg = new StringBuilder(
                    player.getFactionEmoji() + " resolved _Magen Defense Grid_ at " + tile.getPosition() + ".");
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
                        msg.append("\n-# > ").append(emoji.repeat(count)).append(" added to space area.");
                    } else {
                        msg.append("\n-# > ")
                                .append(emoji.repeat(count))
                                .append(" added to ")
                                .append(Helper.getPlanetRepresentation(uh.getName(), game));
                    }
                }
            }
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), String.format(msg.toString(), total));
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
        Predicate<UnitKey> isInf = uk -> uk.getUnitType() == UnitType.Infantry;
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

    public static List<Button> neuralParasiteButtons(Game game, Player player) {
        Predicate<UnitKey> isInf = uk -> uk.getUnitType() == UnitType.Infantry;
        List<Tile> tilesAdjToObsInf = tilesAdjToPlayersInf(game, player);
        List<Player> playersWithInfAdj = game.getRealPlayersNNeutral().stream()
                .filter(p -> p != player
                        && tilesAdjToObsInf.stream().anyMatch(t -> t.containsPlayersUnitsWithKeyCondition(p, isInf)))
                .toList();
        String prefixID = player.getFinsFactionCheckerPrefix() + "neuralParasiteS2_";
        List<Button> buttons = playersWithInfAdj.stream()
                .map(p -> Buttons.gray(prefixID + p.getFaction(), null, p.fogSafeEmoji()))
                .toList();
        return buttons;
    }

    @ButtonHandler("startNeuralParasite")
    private static void handleNeuralParasiteStep1(ButtonInteractionEvent event, Game game, Player player) {
        // "At the start of your turn, destroy 1 of another player's infantry in or adjacent to a system that contains
        // your infantry."
        List<Button> buttons = neuralParasiteButtons(game, player);
        TechnologyModel biorganic = Mapper.getTech("parasite-obs");
        String message = player.getRepresentation() + ", please choose a player to remove 1 of their infantry using "
                + biorganic.getNameRepresentation() + ".";
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
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Button error, yell at Jazz.");
                return;
            }
            UnitHolder uh = tile.getUnitHolders().get(uhName);
            if (uh == null) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Button error, yell at Jazz.");
                return;
            }
            String location = "in the space area of " + tile.getRepresentationForButtons(game, player);
            if (!"space".equals(uhName) && uh instanceof Planet) {
                location = "on the planet " + Helper.getPlanetRepresentation(uhName, game);
            }

            TechnologyModel biorganic = Mapper.getTech("parasite-obs");
            String message = victim.getRepresentationUnfogged() + ", one of your infantry " + location
                    + " has been destroyed via " + biorganic.getNameRepresentation() + ".";
            if (game.isFowMode()) {
                String privateMsg = "Successfully used " + biorganic.getNameRepresentation() + " to destroy 1 infantry "
                        + location + ".";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), privateMsg);
            }
            MessageHelper.sendMessageToChannel(victim.getCorrectChannel(), message);
            DestroyUnitService.destroyUnits(event, tile, game, victim.getColorID(), "inf " + uhName, false);
            ButtonHelper.deleteMessage(event);
        });
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
        String buttonPrefix = player.getFinsFactionCheckerPrefix() + "planesplitterStep1_";
        List<Button> buttons = getPlanesplitterStep1Buttons(game, player);

        String message = "Please choose a system to move an Ingress token into.";
        // if (NewStuffHelper.checkAndHandlePaginationChange(
        //         event, player.getCorrectChannel(), buttons, message, buttonPrefix, buttonID)) {
        //     return;
        // }
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
        String prefix = player.getFinsFactionCheckerPrefix() + "planesplitterStep1_";
        List<Button> buttons = new ArrayList<>();
        if (player.hasUnlockedBreakthrough("cabalbt")) {
            for (Tile tile : game.getTileMap().values()) {
                if (tile.isGravityRift(game)) {
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

        String buttonPrefix = player.getFinsFactionCheckerPrefix() + "planesplitterStep2_";
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
        String prefix = player.getFinsFactionCheckerPrefix() + "planesplitterStep2_";
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
        buttons.add(Buttons.green(player.finChecker() + "useExecutiveOrder_top", "Reveal Agenda From Top"));
        buttons.add(Buttons.green(player.finChecker() + "useExecutiveOrder_bottom", "Reveal Agenda From Bottom"));
        String msg = player.getPing()
                + ", please choose if you wish to reveal the agenda from the top or from the bottom of the agenda deck.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("useExecutiveOrder_")
    private static void executiveOrder(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        game.setStoredValue("executiveOrder", player.getFaction());
        game.setPhaseOfGame("agenda");
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
