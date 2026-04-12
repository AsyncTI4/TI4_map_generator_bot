package ti4.buttons.handlers.faction.cymiae;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.message.MessageHelper;

@UtilityClass
public class CymiaeButtonHandler {

    @ButtonHandler("cymiaeHeroAutonetic")
    public static void cymiaeHeroAutonetic(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        String msg2 = player.getRepresentationNoPing() + " is choosing to resolve their **Autonetic Memory** ability.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        buttons.add(Buttons.green("autoneticMemoryStep3a", "Pick Action Card From the Discard"));
        buttons.add(Buttons.blue("autoneticMemoryStep3b", "Drop 1 infantry"));
        String msg = player.getRepresentationUnfogged()
                + ", you have the ability to either draw a card from the discard (and then discard a card) or place 1 infantry on a planet you control.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        buttons = new ArrayList<>();
        buttons.add(Buttons.green("cymiaeHeroStep1_" + (game.getRealPlayers().size()), "Resolve Cymiae Hero"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", please resuming resolving your hero after doing **Autonetic Memory** steps.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }
}
