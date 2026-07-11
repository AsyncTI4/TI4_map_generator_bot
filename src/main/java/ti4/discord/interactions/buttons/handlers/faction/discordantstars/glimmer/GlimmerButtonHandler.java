package ti4.discord.interactions.buttons.handlers.faction.discordantstars.glimmer;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
class GlimmerButtonHandler {

    @ButtonHandler("endGlimmersRedTech_")
    public static void endGlimmersRedTech(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event != null) {
            ButtonHelper.deleteMessage(event);
        }
        String unit = buttonID.split("_")[1];

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.toString()
                        + ", please choose the system adjacent to your destroyed unit that you wish to place the unit."
                        + "\n-# Note that not all options displayed are legal options. The bot did not check where the unit was destroyed.",
                Helper.getTileWithShipsPlaceUnitButtons(player, game, unit, "placeOneNDone_skipbuild"));
    }

    @ButtonHandler("glimmersHeroOn_")
    public static void glimmerHeroOn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        AddUnitService.addUnits(event, game.getTileByPosition(pos), game, player.getColor(), unit);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmojiOrColor() + " chose to duplicate a " + unit + " in "
                        + game.getTileByPosition(pos).getRepresentationForButtons(game, player));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("glimmersHeroIn_")
    public static void glimmersHeroIn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.substring(buttonID.indexOf('_') + 1);
        List<Button> buttons = ButtonHelperHeroes.getUnitsToGlimmersHero(player, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentationUnfogged() + ", please choose which unit you wish to duplicate.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
