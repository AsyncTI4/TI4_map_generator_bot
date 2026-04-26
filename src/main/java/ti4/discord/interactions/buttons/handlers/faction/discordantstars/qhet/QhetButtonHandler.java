package ti4.discord.interactions.buttons.handlers.faction.discordantstars.qhet;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
class QhetButtonHandler {

    @ButtonHandler("qhetMechProduce_")
    public static void qhetMechProduce(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planet = buttonID.split("_")[1];
        Tile tile = game.getTile(AliasHandler.resolveTile(planet));
        AddUnitService.addUnits(event, tile, game, player.getColor(), "2 inf " + planet);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " produced 2 infantry on " + Helper.getPlanetRepresentation(planet, game));
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        Button DoneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
        buttons.add(DoneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Please pay one resource.", buttons);
        if (player.hasTech("yso")) {
            List<Button> unitButtons2 = new ArrayList<>();
            unitButtons2.add(Buttons.gray("startYinSpinner", "Yin Spin 2 Duders", FactionEmojis.Yin));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " you may use this to Yin Spin.",
                    unitButtons2);
        }
    }
}
