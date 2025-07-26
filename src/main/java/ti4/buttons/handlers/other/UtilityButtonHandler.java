package ti4.buttons.handlers.other;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.image.TileGenerator;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.button.ReactionService;

import java.util.List;

@UtilityClass
class UtilityButtonHandler {

    @ButtonHandler("genericReact")
    public static void genericReact(ButtonInteractionEvent event, Game game, Player player) {
        String message = game.isFowMode() ? "Turned down window" : null;
        ReactionService.addReaction(event, game, player, message);
    }

    @ButtonHandler("genericRemove_")
    public static void genericRemove(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("genericRemove_", "");

        game.getTacticalActionDisplacement().clear();
        List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, game, game.getTileByPosition(pos), "Remove");

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Removing units from " + game.getTileByPosition(pos).getRepresentationForButtons(game, player) + ".");
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Please choose the units you wish to remove.", systemButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("deleteButtons")
    public static void deleteButtons(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("refresh_")
    public static void refresh(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planetName = buttonID.split("_")[1];
        Player p2 = player;
        if (buttonID.split("_").length > 2) {
            String faction = buttonID.split("_")[2];
            p2 = game.getPlayerFromColorOrFaction(faction);
        }

        if (p2 != null) {
            p2.refreshPlanet(planetName);
            String message = p2.getFactionEmoji() + " readied " + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("refreshViewOfSystem_")
    public static void refreshViewOfSystem(ButtonInteractionEvent event, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);

        try {
            String tileName = tile.getTileID();
            String tilePath = "async/" + tileName + ".png";
            TileGenerator.generateTile(tileName, game, event, player, null);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Refreshed view of system " + tile.getRepresentationForButtons(game, null));
        } catch (Exception e) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Error refreshing system view: " + e.getMessage());
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("chooseMapView")
    public static void chooseMapView(ButtonInteractionEvent event) {
        List<Button> buttons = ButtonHelper.getMapViewButtons();
        String message = "Choose the map view you'd like to see:";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("temporaryPingDisable")
    public static void temporaryPingDisable(ButtonInteractionEvent event, Game game) {
        String message = "Pings have been temporarily disabled for this game.";
        game.setTemporaryPingDisable(true);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("shuffleExplores")
    public static void shuffleExplores(ButtonInteractionEvent event, Game game) {
        game.shuffleExploreDecks();
        String message = "All exploration decks have been shuffled.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("doneRemoving")
    public static void doneRemoving(ButtonInteractionEvent event, Game game) {
        String message = "Done removing units.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("run_status_cleanup")
    public static void runStatusCleanup(ButtonInteractionEvent event, Game game, Player player) {
        String message = "Running status cleanup for " + player.getFactionEmoji();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        // StatusCleanupService.runStatusCleanup(game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(value = "refreshStatusSummary", save = false)
    public static void refreshStatusSummary(ButtonInteractionEvent event, Game game) {
        String msg = "Please score objectives.";
        msg += "\n\n" + Helper.getNewStatusScoringRepresentation(game);
        event.getMessage().editMessage(msg).queue();
    }

    @ButtonHandler("setAutoPassMedian_")
    public static void setAutoPassMedian(ButtonInteractionEvent event, Player player, String buttonID) {
        String hours = buttonID.split("_")[1];
        int median = Integer.parseInt(hours);
        player.setAutoSaboPassMedian(median);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Set median time to " + median + " hours");

        if (median > 0) {
            if (!player.hasAbility("quash") && !player.ownsPromissoryNote("rider")
                && !player.getPromissoryNotes().containsKey("riderm")
                && !player.hasAbility("radiance") && !player.hasAbility("galactic_threat")
                && !player.hasAbility("conspirators")
                && !player.ownsPromissoryNote("riderx")
                && !player.ownsPromissoryNote("riderm") && !player.ownsPromissoryNote("ridera")
                && !player.hasTechReady("gr")) {

                String msg = player.getRepresentation() + ", the bot may also auto react for you when you have no \"when\"s or \"after\"s."
                    + " Default for this is off. This will only apply to this game."
                    + " If you have any \"when\"s or \"after\"s or related \"when\"/\"after\" abilities, it will not do anything. ";
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg);
            }
        }
        ButtonHelper.deleteMessage(event);
    }
}