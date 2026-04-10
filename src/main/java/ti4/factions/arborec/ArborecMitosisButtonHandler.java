package ti4.factions.arborec;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.UnitReplacementHelper;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;

@UtilityClass
public class ArborecMitosisButtonHandler {

    public static List<Button> getMitosisOptions(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("mitosisInf", "Place 1 infantry"));
        if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) < 4
                && !ButtonHelper.isLawInPlay(game, "articles_war")) {
            buttons.add(Buttons.blue("mitosisMech", "Remove 1 Infantry to Deploy 1 Mech"));
        }
        return buttons;
    }

    @ButtonHandler("mitosisInf")
    public static void resolveMitosisInf(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons =
                new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game, "infantry", "placeOneNDone_skipbuild"));
        String message = player.getRepresentationUnfogged() + ", please choose which planet to place an infantry on.";
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getFactionEmojiOrColor() + " is resolving **Mitosis**.");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("mitosisMech")
    public static void resolveMitosisMech(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>(UnitReplacementHelper.getMechReplacementButtons(player, game));
        String message = player.getRepresentationUnfogged()
                + ", please choose where you wish to replace an infantry with a mech.";
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getFactionEmojiOrColor() + " is resolving **Mitosis**.");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
