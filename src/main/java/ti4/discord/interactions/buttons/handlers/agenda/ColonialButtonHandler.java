package ti4.discord.interactions.buttons.handlers.agenda;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
class ColonialButtonHandler {

    @ButtonHandler("colonialRedTarget_")
    public static void resolveColonialRedTarget(Game game, String buttonID, ButtonInteractionEvent event) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p2 == null) return;
        String planet = buttonID.split("_")[2];
        Tile tile = game.getTileFromPlanet(planet);
        if (tile != null) {
            AddUnitService.addUnits(event, tile, game, p2.getColor(), "1 inf " + planet);
        }
        String reminder = " Reminder that this is technically optional and was done automatically for conveinence.";
        if (game.isFowMode()) {
            // The colour and the raw planet id both went to the main channel unconditionally. In fog the
            // recipient learns their own placement; everyone else only needs to know it happened.
            MessageHelper.sendMessageToChannel(
                    p2.getCorrectChannel(),
                    "1 of your infantry was added to " + AgendaHelper.getAgendaOutcomeName(game, planet, true) + "."
                            + reminder);
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "An infantry was added to the elected planet." + reminder);
        } else {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    "1 " + p2.getColor() + " infantry was added to " + planet + "." + reminder);
        }
        ButtonHelper.deleteMessage(event);
    }
}
