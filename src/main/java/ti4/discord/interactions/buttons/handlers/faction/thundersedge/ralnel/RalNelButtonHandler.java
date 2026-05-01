package ti4.discord.interactions.buttons.handlers.faction.thundersedge.ralnel;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.CommandCounterHelper;
import ti4.message.MessageHelper;

@UtilityClass
class RalNelButtonHandler {

    @ButtonHandler("ralnelCStep3_")
    public static void ralnelCStep3(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        String pos2 = buttonID.split("_")[1];
        Tile targetTile = game.getTileByPosition(pos2);
        CommandCounterHelper.addCC(event, player, targetTile);
        ButtonHelperHeroes.argentHeroStep3(game, player, buttonID);
    }

    @ButtonHandler("ralnelCommander_")
    public static void ralnelCommander(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        if (ButtonHelperModifyUnits.getRetreatSystemButtons(player, game, game.getActiveSystem(), false, false)
                .isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "## There are no valid systems to retreat to!");
            return;
        }
        ButtonHelper.deleteTheOneButton(event);
        List<Button> buttons = ButtonHelperModifyUnits.getRalnelCommanderButtons(player, game, buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", is using Watchful Ojz, the Ral Nel commander, to immediately retreat 2 ships (and maybe transport).\nReminder: You need a valid retreat location before you can announce retreats and use this commander, but after you clear that hurdle, the system this commander retreats to does not have to follow a conventionally valid retreat path.");
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", please choose which system you wish to move units to.",
                buttons);
    }
}
