package ti4.helpers;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.service.unit.DestroyUnitService;

public class ButtonHelperTwilightsFallActionCards {

   

    @ButtonHandler("resolveEngineer")
    public static void resolveEngineer(Game game, Player player, ButtonInteractionEvent event) {
        game.setStoredValue("engineerACSplice", "True");
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                player.getRepresentation()
                        + " added 2 more cards to the splice and you should be prompted to discard 2 cards after choosing yours.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveLocust")
    public static void resolveLocust(Game game, Player player, ButtonInteractionEvent event) {
        List<String> tilesSeen = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile)) {
                for (String tilePos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true)) {
                    if (tilesSeen.contains(tilePos)) {
                        continue;
                    } else {
                        tilesSeen.add(tilePos);
                    }
                    Tile tile2 = game.getTileByPosition(tilePos);
                    for (UnitHolder uH : tile2.getUnitHolders().values()) {
                        String label;
                        for (Player p2 : game.getRealAndEliminatedAndDummyPlayers()) {
                            if (uH.getName().equals("space")) {
                                label = "(" + StringUtils.capitalize(p2.getColor()) + ") Space Area of "
                                        + tile2.getRepresentationForButtons();
                            } else {
                                label = "(" + StringUtils.capitalize(p2.getColor()) + ") "
                                        + Helper.getPlanetRepresentation(uH.getName(), game);
                            }
                            if (uH.getUnitCount(UnitType.Infantry, p2.getColor()) > 0) {
                                buttons.add(Buttons.gray(
                                        player.getFinsFactionCheckerPrefix() + "locustOn_" + tilePos + "_"
                                                + uH.getName() + "_" + p2.getColor(),
                                        label));
                            }
                        }
                    }
                }
            }
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " choose the infantry to start the locust.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("locustOn")
    public static void locustOn(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String tileP = buttonID.split("_")[1];
        String uHName = buttonID.split("_")[2];
        String color = buttonID.split("_")[3];
        Tile oG = game.getTileByPosition(tileP);

        Die d1 = new Die(3);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " choose to hit the infantry on Tile " + tileP + " on " + uHName
                        + " and rolled a " + d1.getResult());
        if (d1.isSuccess()) {
            UnitKey key = Units.getUnitKey(UnitType.Infantry, color);
            DestroyUnitService.destroyUnit(
                    event, oG, game, key, 1, oG.getUnitHolders().get(uHName), false);
            List<Button> buttons = new ArrayList<>();
            for (String tilePos : FoWHelper.getAdjacentTiles(game, tileP, player, false, true)) {
                Tile tile2 = game.getTileByPosition(tilePos);
                for (UnitHolder uH : tile2.getUnitHolders().values()) {
                    String label;

                    for (Player p2 : game.getRealAndEliminatedAndDummyPlayers()) {
                        if (uH.getName().equals("space")) {
                            label = "(" + StringUtils.capitalize(p2.getColor()) + ") Space Area of "
                                    + tile2.getRepresentationForButtons();
                        } else {
                            label = "(" + StringUtils.capitalize(p2.getColor()) + ") "
                                    + Helper.getPlanetRepresentation(uH.getName(), game);
                        }
                        if (uH.getUnitCount(UnitType.Infantry, p2.getColor()) > 0) {
                            buttons.add(Buttons.gray(
                                    player.getFinsFactionCheckerPrefix() + "locustOn_" + tilePos + "_" + uH.getName()
                                            + "_" + p2.getColor(),
                                    label));
                        }
                    }
                }
            }
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " choose the next infantry to locust. You must choose a different player to hit if possible.",
                    buttons);
        }

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveReverseTF")
    public static void resolveReverseTF(Game game, Player player, ButtonInteractionEvent event) {
        game.setStoredValue("reverseSpliceOrder", "True");
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(), player.getRepresentation() + " reversed the order of the splice.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveThieve")
    public static void resolveThieve(Game game, Player player, ButtonInteractionEvent event) {
        ButtonHelperTwilightsFall.sendPlayerSpliceOptions(game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveCreate")
    public static void resolveCreate(Game game, Player player, ButtonInteractionEvent event) {
        RelicHelper.drawRelicAndNotify(player, event, game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveUnravel")
    public static void resolveUnravel(Game game, Player player, ButtonInteractionEvent event) {
        RelicHelper.drawRelicAndNotify(player, event, game);
        ButtonHelper.deleteMessage(event);
    }

    

    

    


    

    
}
