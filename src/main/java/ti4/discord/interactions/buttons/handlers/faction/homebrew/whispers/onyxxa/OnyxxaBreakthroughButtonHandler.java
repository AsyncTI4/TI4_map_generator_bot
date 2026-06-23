package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.onyxxa;

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
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.DiceEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.map.FractureService;
import ti4.service.unit.MoveUnitService;

@UtilityClass
public class OnyxxaBreakthroughButtonHandler {

    public static void offerSCRollButton(Game game, Player player) {
        Button rollButton = Buttons.green(
                player.factionButtonChecker() + "onyxxabtRoll", "Roll for Styx and Stones", FactionEmojis.onyxxa);
        MessageHelper.sendMessageToChannelWithButton(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", only click if you resolved the primary ability of the strategy card: you may roll for _Styx and Stones_.",
                rollButton);
    }

    @ButtonHandler("onyxxabtRoll")
    public static void handleRoll(ButtonInteractionEvent event, Game game, Player player) {
        int result = new DiceHelper.Die(0).getResult();
        DiceEmojis diceEmoji = result == 1 ? DiceEmojis.d10blue_1 : (result == 10 ? DiceEmojis.d10blue_0 : null);
        String diceStr = diceEmoji != null ? diceEmoji.toString() : DiceEmojis.getGrayDieEmoji(result);

        if (result == 1 || result == 10) {
            if (!FractureService.isFractureInPlay(game)) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation(false, false) + " rolled a " + diceStr
                                + "! The Fracture is now in play!");
                FractureService.spawnFracture(event, game);
                FractureService.spawnIngressTokens(event, game, player, "onyxxabt");
            } else {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation(false, false) + " rolled a " + diceStr
                                + "! The Fracture is already in play — move an ingress token to a system that contains your units.");
                offerMoveIngressFromButtons(game, player);
            }
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation(true, false) + " rolled a " + DiceEmojis.getGrayDieEmoji(result)
                            + ", no effect.");
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("onyxxaMoveIngressFrom_")
    public static void handleMoveIngressFrom(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String fromPos = buttonID.replace("onyxxaMoveIngressFrom_", "");
        Tile fromTile = game.getTileByPosition(fromPos);
        fromTile.getSpaceUnitHolder().removeToken(Constants.TOKEN_INGRESS);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                "Removed ingress token from " + fromTile.getRepresentationForButtons(game, player) + ".");

        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "onyxxaMoveIngressTo_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Cancel"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the system to move the ingress token to.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("onyxxaMoveIngressTo_")
    public static void handleMoveIngressTo(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String toPos = buttonID.replace("onyxxaMoveIngressTo_", "");
        Tile toTile = game.getTileByPosition(toPos);
        toTile.addToken(Constants.TOKEN_INGRESS, "space");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation(false, false) + " moved an ingress token to "
                        + toTile.getRepresentationForButtons(game, player) + ".");
        ButtonHelper.deleteMessage(event);
    }

    private static void offerMoveIngressFromButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            UnitHolder space = tile.getSpaceUnitHolder();
            if (space.getTokenList().contains(Constants.TOKEN_INGRESS)) {
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + "onyxxaMoveIngressFrom_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "No ingress tokens found to move.");
            return;
        }
        buttons.add(Buttons.gray("deleteButtons", "Cancel"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose an ingress token to move.",
                buttons);
    }

    public static void offerGroundCombatMechButtons(Game game, Player player, UnitHolder unitHolder, Tile tile) {
        String planetName = unitHolder.getName();
        int infantryCount = unitHolder.getUnitCount(UnitType.Infantry, player.getColorID());
        if (infantryCount < 1) return;

        boolean inFracture = tile.getPosition().startsWith("frac");
        boolean inNexus = "82b".equals(tile.getTileID()) || "82bh".equals(tile.getTileID());
        if (!inFracture && !inNexus) return;

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "onyxxabtMechPlacement_" + tile.getPosition() + "_" + planetName,
                "Replace 1 Infantry with 1 Mech (" + infantryCount + " available)"));
        buttons.add(Buttons.red("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", you may replace any number of your infantry with mechs on "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " (_Styx and Stones_). Click the button once per infantry.",
                buttons);
    }

    @ButtonHandler("onyxxabtMechPlacement_")
    public static void handleMechPlacement(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace("onyxxabtMechPlacement_", "").split("_", 2);
        String pos = parts[0];
        String planetName = parts[1];
        Tile tile = game.getTileByPosition(pos);
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);

        MoveUnitService.replaceUnit(event, game, player, tile, unitHolder, UnitType.Infantry, UnitType.Mech);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation(false, false) + " replaced 1 infantry with 1 mech on "
                        + Helper.getPlanetRepresentation(planetName, game) + " (_Styx and Stones_).");

        int remaining = unitHolder.getUnitCount(UnitType.Infantry, player.getColorID());
        if (remaining == 0) {
            ButtonHelper.deleteMessage(event);
        } else {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "onyxxabtMechPlacement_" + pos + "_" + planetName,
                    "Replace 1 Infantry with 1 Mech (" + remaining + " remaining)"));
            buttons.add(Buttons.red("deleteButtons", "Done"));
            MessageHelper.editMessageButtons(event, buttons);
        }
    }
}
