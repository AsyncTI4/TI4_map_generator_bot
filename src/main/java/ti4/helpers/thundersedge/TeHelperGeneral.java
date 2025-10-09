package ti4.helpers.thundersedge;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.commands.tokens.AddTokenCommand;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.RegexHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Expeditions;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.TriadService;
import ti4.service.planet.AddPlanetService;
import ti4.service.unit.AddUnitService;

public class TeHelperGeneral {

    public static void checkTransientInfo(Game game) {
        TriadService.checkAndUpdateTriad(game);
    }

    @ButtonHandler("expeditionInfo")
    private static void expeditionInfo(ButtonInteractionEvent event, Game game, Player player) {
        String info = game.getExpeditions().printExpeditionInfo();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), info);
    }

    @ButtonHandler("expeditionInfoAndButtons")
    private static void expeditionInfoWithButtons(ButtonInteractionEvent event, Game game, Player player) {
        String info = game.getExpeditions().printExpeditionInfo();
        List<Button> butts = game.getExpeditions().getRemainingExpeditionButtons(player);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), info, butts);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void addStationsToPlayArea(GenericInteractionCreateEvent event, Game game, Tile tile) {
        List<Player> playersInSpace = tile.getSpaceUnitHolder().getUnitColorsOnHolder().stream()
                .map(game::getPlayerFromColorOrFaction)
                .filter(Objects::nonNull)
                .toList();
        if (playersInSpace.isEmpty()) return;

        Player newOwner = game.getPlayerThatControlsTile(tile);
        for (Planet station : tile.getSpaceStations()) {
            Player prevOwner = game.getPlayerThatControlsPlanet(station.getName());
            if (prevOwner != null && FoWHelper.playerHasActualShipsInSystem(prevOwner, tile)) continue;

            AddPlanetService.addPlanet(newOwner, station.getName(), game, event, false);
            MessageHelper.sendMessageToChannel(
                    newOwner.getCorrectChannel(),
                    newOwner.getRepresentation() + " acquired control of the " + station.getRepresentation(game)
                            + " trade station.");
        }
    }

    @ButtonHandler("placeThundersEdge")
    public static void resolvePlaceThundersEdge(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Expeditions exp = game.getExpeditions();
        String part1 = "placeThundersEdge";
        String part2 = part1 + "_" + RegexHelper.posRegex(game);
        String part3 = part2 + "_" + RegexHelper.factionRegex(game);

        Matcher matcher;
        String newMessage = null;
        List<Button> newButtons = new ArrayList<>();
        if ((matcher = Pattern.compile(part1).matcher(buttonID)).matches()) {
            List<Tile> tiles = game.getTileMap().values().stream()
                    .filter(Tile.tileMayHaveThundersEdge())
                    .toList();
            tiles.stream()
                    .map(t -> Buttons.green(
                            "placeThundersEdge_" + t.getPosition(), t.getRepresentationForButtons(game, player)))
                    .forEach(newButtons::add);
            newMessage = player.getRepresentation() + " You can place Thunder's Edge on any of the following tiles:";

        } else if ((matcher = Pattern.compile(part2).matcher(buttonID)).matches()) {
            String pos = matcher.group("pos");
            Tile tile = game.getTileByPosition(pos);
            String prefix = player.getFinsFactionCheckerPrefix() + "placeThundersEdge_" + pos + "_";

            newMessage = player.getRepresentation() + " You are placing place Thunder's Edge on "
                    + tile.getRepresentationForButtons(game, player);
            newMessage +=
                    "\nYou must select one of the players with the most completed expeditions to place infantry on Thunder's Edge:";
            exp.getFactionsWithMostComplete().forEach(faction -> {
                Player p2 = game.getPlayerFromColorOrFaction(faction);
                if (p2 != null)
                    newButtons.add(
                            Buttons.blue(prefix + faction, p2.getFactionModel().getFactionName()));
            });

        } else if ((matcher = Pattern.compile(part3).matcher(buttonID)).matches()) {
            String pos = matcher.group("pos");
            Tile tile = game.getTileByPosition(pos);
            String faction = matcher.group("faction");
            Player p2 = game.getPlayerFromColorOrFaction(faction);

            if (p2 != null) {
                AddTokenCommand.addToken(event, tile, Constants.THUNDERSEDGE, game);
                game.clearPlanetsCache();

                int most = exp.getMostCompleteByAny();
                AddUnitService.addUnits(event, tile, game, p2.getColor(), most + " inf t");
                ButtonHelper.deleteMessage(event);
                String message = "Placed Thunder's Edge in " + tile.getRepresentationForButtons(game, player)
                        + " and added " + most + " " + p2.getRepresentation() + " infantry.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            }
        }

        if (newMessage != null) {
            // edit the message with the new partX buttons
            event.getMessage()
                    .editMessage(newMessage)
                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(newButtons))
                    .queue();
        }
    }
}
