package ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.arvaxi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.tech.ListTechService;

@UtilityClass
public class ArvaxiTechHandler {

    public static void offerWartimeInnovation(Player player, Game game, String msg) {
        List<Button> exhaustButtons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, "inf"));
        exhaustButtons.add(Buttons.DONE_EXHAUSTING_PLANETS_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                msg + ", you may spend 4 influence to research a unit upgrade technology of the same type as 1 of"
                        + " your units participating in this combat via _Wartime Innovation_."
                        + " Exhaust planets below for influence.",
                exhaustButtons);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", choose the unit upgrade technology to research.",
                ListTechService.unitUpgradeButtons(game, player));
    }
}
