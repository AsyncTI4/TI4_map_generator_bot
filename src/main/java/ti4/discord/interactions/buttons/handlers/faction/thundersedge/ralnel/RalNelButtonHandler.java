package ti4.discord.interactions.buttons.handlers.faction.thundersedge.ralnel;

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
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.CommandCounterHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;

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
        String pos = buttonID.split("_")[1];
        if (ButtonHelperModifyUnits.getRetreatSystemButtons(player, game, pos, false, false)
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
        if (game.getTileByPosition(pos).isGravityRift()
                && !player.hasRelic("circletofthevoid")
                && !player.hasTech("tf-crucible")) {
            Button rift = Buttons.green(
                    player.factionButtonChecker() + "getRiftButtons_" + pos, "Rift Units", MiscEmojis.GravityRift);
            buttons = new ArrayList<>();
            buttons.add(rift);
            String message2 = "## " + player.getRepresentationUnfogged()
                    + ", if applicable, use this button to rift retreating units __before__ choosing where to retreat. It needs to be before you actually select where to retreat.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message2, buttons);
        }
    }
}
