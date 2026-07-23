package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.tyris;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.objectives.RevealPublicObjectiveService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class TyrisAbilityHandler {

    public static void postPhantomEnergyButtons(Game game, Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        for (String asyncID : tile.getSpaceUnitHolder()
                .getUnitAsyncIdsOnHolder(player.getColorID())
                .keySet()) {
            buttons.add(Buttons.green(
                    "resolvePhantomEnergy_" + asyncID,
                    StringUtils.capitalize(Mapper.getUnitBaseTypeFromAsyncID(asyncID))));
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " choose the ship type to use **Phantom Energy** on.",
                buttons);
    }

    @ButtonHandler("resolvePhantomEnergy_")
    public static void resolvePhantomEnergy(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String asyncID = buttonID.split("_")[1];
        game.setStoredValue("phantomEnergy", game.getStoredValue("phantomEnergy") + asyncID);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " has used **Phantom Energy** on the "
                        + Mapper.getUnitBaseTypeFromAsyncID(asyncID) + " ship type.");
        ButtonHelper.deleteMessage(event);
    }

    public static void cleanupPhantomEnergy(Game game, Player player) {
        if (game.getStoredValue("phantomEnergy").isEmpty()) {
            return;
        }
        for (Tile tile : game.getTileMap().values()) {
            if (tile.hasPlayerCC(player)) {
                for (String asyncID : tile.getSpaceUnitHolder()
                        .getUnitAsyncIdsOnHolder(player.getColorID())
                        .keySet()) {
                    game.setStoredValue(
                            "phantomEnergy",
                            game.getStoredValue("phantomEnergy").replace(asyncID, ""));
                }
            }
        }
    }

    public static void checkFlagshipPhantomEnergy(Game game, Player player) {
        if (player.hasUnit("tyris_flagship")
                && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "flagship", false) > 0) {
            game.setStoredValue("phantomEnergy", game.getStoredValue("phantomEnergy") + "fs");
        }
    }

    public static void offerRewriteDestiny(Game game, Player player, String poID, int stage) {
        if (!game.getStoredValue("rewriteDestinyUsed" + game.getRound()).isEmpty()) return;
        if (player.getStrategicCC() < 1) return;
        String poName = Mapper.getPublicObjective(poID) != null
                ? Mapper.getPublicObjective(poID).getName()
                : poID;
        String msg = player.getRepresentationUnfogged()
                + " you may use **Rewrite Destiny** to discard the newly revealed objective _" + poName
                + "_ and have the speaker reveal a new stage " + stage + " public objective."
                + " This will cost 1 command token from your strategy pool.";
        List<Button> buttons = new ArrayList<>();
        CardEmojis emoji = stage == 1 ? CardEmojis.Public1 : CardEmojis.Public2;
        buttons.add(Buttons.green("rewriteDestiny;" + poID + ";" + stage, "Use Rewrite Destiny", emoji));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("rewriteDestiny")
    public static void resolveRewriteDestiny(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String poID = buttonID.split(";")[1];
        int stage = Integer.parseInt(buttonID.split(";")[2]);
        Integer idNumber = game.getRevealedPublicObjectives().get(poID);
        if (idNumber == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " the objective was not found — it may have already been discarded.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.setStrategicCC(player.getStrategicCC() - 1);
        game.setStoredValue("rewriteDestinyUsed" + game.getRound(), "true");
        String poName = Mapper.getPublicObjective(poID) != null
                ? Mapper.getPublicObjective(poID).getName()
                : poID;
        game.shuffleObjectiveBackIntoDeck(idNumber);
        String msg = "## " + game.getPing() + " " + player.getRepresentation()
                + " is using **Rewrite Destiny**, spending 1 strategy token, to discard _" + poName
                + "_ and have the speaker reveal a new stage " + stage + " public objective.";
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
        if (stage == 1) {
            RevealPublicObjectiveService.revealS1(game, event, true);
        } else {
            RevealPublicObjectiveService.revealS2(game, event, true);
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void offerCCForDestroyedReverb(Player player) {
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", use buttons to gain 1 command token for each Reverb destroyed, due to its unit ability.",
                ButtonHelper.getGainCCButtons(player));
    }

    public static void resolveTemporalDisplacementStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                buttons.add(Buttons.green(
                        "temporalDisplacement_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Done Resolving"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", use buttons to choose the system you wish to move fighters to.",
                buttons);
    }

    @ButtonHandler("temporalDisplacement_")
    public static void resolveTemporalDisplacementStep2(
            Player player, String buttonID, Game game, ButtonInteractionEvent event) {
        String destPos = buttonID.replace("temporalDisplacement_", "");
        Tile destTile = game.getTileByPosition(destPos);
        if (destTile == null) return;
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getPosition().equals(destPos)) continue;
            if (tile.getSpaceUnitHolder().getUnitCount(UnitType.Fighter, player.getColor()) > 0) {
                buttons.add(Buttons.green(
                        "temporalDisplacementMove_" + destPos + "_" + tile.getPosition(),
                        "Move Fighter from " + tile.getRepresentationForButtons(game, player)));
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Done Moving to This System"));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", use buttons to choose the fighters you wish to move to "
                        + destTile.getRepresentationForButtons(game, player) + ".",
                buttons);
    }

    @ButtonHandler("temporalDisplacementMove_")
    public static void resolveTemporalDisplacementMove(
            Player player, String buttonID, Game game, ButtonInteractionEvent event) {
        String[] parts = buttonID.replace("temporalDisplacementMove_", "").split("_");
        Tile destTile = game.getTileByPosition(parts[0]);
        Tile srcTile = game.getTileByPosition(parts[1]);
        if (destTile == null || srcTile == null) return;
        List<RemoveUnitService.RemovedUnit> removedUnits =
                RemoveUnitService.removeUnits(event, srcTile, game, player.getColor(), "ff");
        AddUnitService.addUnits(event, destTile, game, player.getColor(), "ff", removedUnits);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmojiOrColor() + " moved 1 fighter from "
                        + srcTile.getRepresentationForButtons(game, player) + " to "
                        + destTile.getRepresentationForButtons(game, player) + " using _Temporal Displacement_.");
        if (srcTile.getSpaceUnitHolder().getUnitCount(UnitType.Fighter, player.getColor()) < 1) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        }
    }
}
