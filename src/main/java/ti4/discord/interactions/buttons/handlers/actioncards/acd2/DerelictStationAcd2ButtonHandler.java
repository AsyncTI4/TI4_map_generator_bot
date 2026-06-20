package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;

@UtilityClass
class DerelictStationAcd2ButtonHandler {

    @ButtonHandler("resolveDerelictStation")
    public static void resolveDerelictStation(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("derelictStationFrontier", "Explore a Frontier Token"));
        buttons.add(Buttons.blue("derelictStationComms", "Gain 2 Commodities", MiscEmojis.comm));
        buttons.add(Buttons.red("deleteButtons", "Done"));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose how to resolve _Derelict Station_.",
                buttons);
    }

    @ButtonHandler("derelictStationComms")
    public static void resolveDerelictStationComms(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelperStats.gainComms(event, game, player, 2, true);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("derelictStationFrontier")
    public static void resolveDerelictStationFrontier(Player player, Game game, ButtonInteractionEvent event) {
        String frontierFilename = Mapper.getTokenID(Constants.FRONTIER);
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile == null || !tile.getPlanetUnitHolders().isEmpty()) {
                continue;
            }
            if (tile.getSpaceUnitHolder().getTokenList().contains(frontierFilename)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "derelictStationExplore_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", there are no planetless systems with a frontier token to explore for _Derelict"
                            + " Station_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the system whose frontier token you want to explore.",
                buttons);
    }

    @ButtonHandler("derelictStationExplore_")
    public static void resolveDerelictStationExplore(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.replace("derelictStationExplore_", "");
        Tile tile = game.getTileByPosition(pos);
        ButtonHelper.deleteMessage(event);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not resolve _Derelict Station_ for that system.");
            return;
        }
        ButtonHelper.resolveFullFrontierExplore(game, player, tile, event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " explored the frontier token in " + tile.getRepresentation()
                        + " via _Derelict Station_.");
    }
}
