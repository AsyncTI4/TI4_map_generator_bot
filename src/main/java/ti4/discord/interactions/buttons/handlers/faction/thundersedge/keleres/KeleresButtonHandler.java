package ti4.discord.interactions.buttons.handlers.faction.thundersedge.keleres;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.leader.PurgeHeroService;
import ti4.service.unit.AddUnitService;

@UtilityClass
class KeleresButtonHandler {

    @ButtonHandler("useLawsOrder")
    public static void useLawsOrder(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmojiOrColor()
                        + " is paying 1 trade good or 1 commodity to ignore laws for the turn.");
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
        Button doneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), "Please spend 1 commodity or 1 trade good.", buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        game.setStoredValue("lawsDisabled", "yes");
    }

    @ButtonHandler("purgeKeleresAHero")
    public static void purgeKeleresAHero(ButtonInteractionEvent event, Player player, Game game) { // TODO: add service
        PurgeHeroService.purgeHeroPreamble(
                event, player, game, "keleresherokuuasi", "Kuuasi Aun Jalatai, the Keleres (Argent) hero");
        AddUnitService.addUnits(
                event,
                game.getTileByPosition(game.getActiveSystem()),
                game,
                player.getColor(),
                "2 cruiser, 1 flagship");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", 2 cruisers and a flagship have been added to "
                        + game.getTileByPosition(game.getActiveSystem()).getRepresentation() + ".");
    }
}
