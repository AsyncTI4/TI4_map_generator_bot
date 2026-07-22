package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Verydith;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.PromissoryNoteHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class VerydithPromissoryHandler {

    public static void usePactRenewed(Player player) {
        if (player == null
                || !player.hasPlayablePromissoryInHand("thpnverydith")
                || player.getPromissoryNotesInPlayArea().contains("thpnverydith")) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "playPactRenewed", "Play Pact Renewed", FactionEmojis.verydith));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation()
                        + ", you just played a strategy card while you have _Pact Renewed_ in hand. Use the buttons below to play it or decline.",
                buttons);
    }

    @ButtonHandler("playPactRenewed")
    public static void resolvePactRenewed(ButtonInteractionEvent event, Game game, Player player) {
        if (player == null || game == null || !player.hasPlayablePromissoryInHand("thpnverydith")) {
            return;
        }

        player.addPromissoryNoteToPlayArea("thpnverydith");
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(),
                player.getRepresentation()
                        + ", you may use these buttons to perform the secondary ability of the strategy card you played _Pact Renewed_ for.\n**REMINDER**: You do not spend a command token when doing this.",
                ButtonHelperHeroes.getSecondaryButtons(game));
    }

    public static void returnPactRenewedAtStartOfStatus(Game game) {
        for (Player holder : game.getPlayers().values()) {
            if (!holder.getPromissoryNotesInPlayArea().contains("thpnverydith")) {
                continue;
            }

            Player owner = game.getPNOwner("thpnverydith");
            if (owner == null || !owner.isRealPlayer()) {
                continue;
            }

            holder.removePromissoryNote("thpnverydith");
            owner.setPromissoryNote("thpnverydith");
            PromissoryNoteHelper.sendPromissoryNoteInfo(game, holder, false);
            PromissoryNoteHelper.sendPromissoryNoteInfo(game, owner, false);
            MessageHelper.sendMessageToChannel(
                    holder.getCorrectChannel(),
                    "_Pact Renewed_ has been returned to " + owner.getRepresentationNoPing() + ".");
        }
    }
}
