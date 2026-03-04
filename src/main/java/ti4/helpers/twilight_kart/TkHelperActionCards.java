package ti4.helpers.twilight_kart;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;

@UtilityClass
public class TkHelperActionCards {

    public static void nop() {}

    public static boolean resolveTkActionCard(ActionCardModel card, Player player, String introMsg) {
        String resolve = "Resolve " + card.getName();
        List<Button> buttons = new ArrayList<>();

        switch (card.getAutomationID()) {
            case "tk-amalgamate" -> buttons.add(Buttons.green(player.finChecker() + "resolveAmalgamate", resolve));
            case "tk-avenge" -> nop();
            case "tk-bestow" -> nop();
            case "tk-commission" -> nop();
            case "tk-conscript" -> nop();
            case "tk-contract" -> nop();
            case "tk-daunt" -> nop();
            case "tk-evade" -> nop();
            case "tk-exhort" -> nop();
            case "tk-fortify" -> nop();
            case "tk-graft" -> nop();
            case "tk-incubate" -> nop();
            case "tk-initiate" -> nop();
            case "tk-oppress" -> nop();
            case "tk-orchestrate" -> nop();
            case "tk-ordain" -> nop();
            case "tk-posture" -> nop();
            case "tk-preside" -> nop();
            case "tk-raze" -> nop();
            case "tk-riposte" -> nop();
            case "tk-spite" -> nop();
            case "tk-succor" -> nop();
            case "tk-thwart" -> nop();
        }

        if (buttons != null && !buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), introMsg, buttons);
            return true;
        }

        return false;
    }

    @ButtonHandler("resolveAmalgamate")
    private void drawOneGenome(ButtonInteractionEvent event, Game game, Player player) {
        game.getGenomeSpliceDeck(false);
    }
}
