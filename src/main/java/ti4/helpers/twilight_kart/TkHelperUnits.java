package ti4.helpers.twilight_kart;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;

@UtilityClass
public class TkHelperUnits {

    @ButtonHandler("startMoyinsChosen")
    private void startMoyinsChosen(ButtonInteractionEvent event, Game game, Player player) {
        String ident = player.getRepresentationNoPing();
        MessageHelper.sendMessageToChannel(event.getChannel(), ident + " is using _Moyin's Chosen_.");

        List<Button> buttons = Helper.getTileWithShipsPlaceUnitButtons(player, game, "1ff", "placeOneNDone_skipbuild");
        String message = player.getRepresentation() + " use the buttons to deploy 1 fighter with your ships using the ";
        message += UnitEmojis.destroyer + " " + FactionEmojis.Yin + " _Moyin's Chosen_ ability.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }

    @ButtonHandler("maleagantBegin")
    private void serveMaleagantButtons(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red("discardSpliceCard_ability", "Discard 1 Ability"));
        buttons.add(Buttons.green("drawSingularNewSpliceCard_ability", "Draw 1 Ability"));
        buttons.add(Buttons.gray("deleteButtons", "Done Resolving"));

        String msg = player.getRepresentation() + ", use these buttons to resolve the ";
        msg += UnitEmojis.fighter + " " + FactionEmojis.Nekro + " _Maleagant_ ability.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("useVisionariaArchive")
    private void useVisionariaArchive(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (player.getTg() < 3) {
            String msg = "You only have " + player.getTg() + " trade goods, so you cannot use this ability.";
            msg = " You can try transacting for more trade goods first.";
            MessageHelper.sendEphemeralMessageToEventChannel(event, msg);
            return;
        }

        String arciveFaction = buttonID.replace("useVisionariaArchive_", "");
        Player archive = game.getPlayerFromColorOrFaction(arciveFaction);

        String msg = player.getRepresentationUnfogged() + " spent 3 trade goods " + player.gainTG(-3);
        msg += " to draw an ability.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelperTwilightsFall.drawAbilityFromDeck(game, player);

        if (player != archive) {
            String msg2 = archive.getRepresentation() + "!!!!\n";
            msg2 += player.getRepresentation() + " spent 3 trade goods to draw an ability, so you get one as well.";
            MessageHelper.sendMessageToChannel(archive.getCorrectChannel(), msg2);
            ButtonHelperTwilightsFall.drawAbilityFromDeck(game, archive);
        }
        ButtonHelper.deleteMessage(event);
    }
}
