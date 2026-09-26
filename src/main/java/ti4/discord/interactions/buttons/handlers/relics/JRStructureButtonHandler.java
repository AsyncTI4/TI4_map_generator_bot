package ti4.discord.interactions.buttons.handlers.relics;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.handlers.unit.monuments.MonumentsPoKButtonHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;

@UtilityClass
class JRStructureButtonHandler {

    @ButtonHandler("jrStructure_")
    public static void jrStructure(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String unit = buttonID.replace("jrStructure_", "");
        if (!"tg".equalsIgnoreCase(unit)) {
            boolean panopticon = "monument".equalsIgnoreCase(unit)
                    && player.hasUnit("empyrean_monument")
                    && Helper.getPlanetPlaceUnitButtons(player, game, unit, "placeOneNDone_dontskip")
                            .isEmpty();
            String message = player.getRepresentationUnfogged()
                    + (panopticon
                            ? ", please choose the empty system in which to place _The Panopticon_ in space."
                            : ", please choose the planet you wish to put your structure on.");
            List<Button> buttons = panopticon
                    ? MonumentsPoKButtonHandler.getPanopticonPlacementButtons(game, player)
                    : Helper.getPlanetPlaceUnitButtons(player, game, unit, "placeOneNDone_dontskip");
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);

        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getFactionEmojiOrColor() + " trade goods increased by 1 " + player.gainTG(1) + ".");
            ButtonHelperAbilities.pillageCheck(player, game);
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
        }
        ButtonHelper.deleteMessage(event);
    }
}
