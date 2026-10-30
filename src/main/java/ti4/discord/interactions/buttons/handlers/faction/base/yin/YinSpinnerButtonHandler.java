package ti4.discord.interactions.buttons.handlers.faction.base.yin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.ResourceHelper;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.RandomHelper;
import ti4.message.MessageHelper;

@UtilityClass
class YinSpinnerButtonHandler {

    @ButtonHandler("startYinSpinner")
    public static void startYinSpinner(ButtonInteractionEvent event, Player player, Game game) {
        player.setYinSpinnerCounter(player.getYinSpinnerCounter() + 2);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getRepresentationNoPing() + " is using _Yin Spinner_."
                        + "\n-# _Yin Spinner_ has placed " + player.getYinSpinnerCounter()
                        + " infantry this game so far.");
        List<Button> buttons =
                new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game, "2gf", "placeOneNDone_skipbuild"));
        buttons.addAll(Helper.getTileWithShipsPlaceUnitButtons(player, game, "2gf", "placeOneNDone_skipbuild"));
        String message = "Use buttons to drop 2 infantry on a planet or with your ships.";
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        int randomJokeChance = RandomHelper.current().nextInt(1, 11);
        if (randomJokeChance == 10) {
            File audioFile = ResourceHelper.getFile("voices/yin/", "YinSpin.mp3");
            if (audioFile.exists()) {
                MessageHelper.sendFileToChannel(player.getCorrectChannel(), audioFile);
            }
        }
    }
}
