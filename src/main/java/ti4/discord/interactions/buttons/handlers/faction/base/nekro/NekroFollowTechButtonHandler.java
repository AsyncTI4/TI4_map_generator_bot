package ti4.discord.interactions.buttons.handlers.faction.base.nekro;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;

@UtilityClass
class NekroFollowTechButtonHandler {

    @ButtonHandler("nekroTechExhaust")
    public static void nekroTechExhaust(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + ", please choose the planets you wish to exhaust.";
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        List<Button> dwsCommanders = game.getPlayers().values().stream()
                .filter(p1 -> p1 != player)
                .filter(p1 -> game.playerHasLeaderUnlockedOrAlliance(p1, "deepwroughtcommander"))
                .map(p1 -> {
                    String id = "useDwsDiscount_" + p1.getFaction();
                    boolean anon = game.isFowMode() && !FoWHelper.canSeeStatsOfPlayer(game, p1, player);
                    String ident = anon ? "Somebody's" : p1.getFactionModel().getShortName() + "'s";
                    String label = "Use " + ident + " Aello Discount";
                    String emoji = p1.getFactionEmoji();
                    return Buttons.gray(id, label, emoji);
                })
                .toList();
        buttons.addAll(dwsCommanders);
        Button doneExhausting = Buttons.red("deleteButtons_technology", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
        }
        ButtonHelper.deleteMessage(event);
    }
}
