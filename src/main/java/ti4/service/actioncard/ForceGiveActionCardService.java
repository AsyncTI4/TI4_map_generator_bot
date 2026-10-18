package ti4.service.actioncard;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie.XinTechHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

@UtilityClass
public class ForceGiveActionCardService {

    public static void sendGiveACButtons(Player receiver, Player giver, Game game, String prompt) {
        if (XinTechHandler.hasAstromanticCloakSteel(giver)) {
            MessageHelper.sendMessageToChannel(
                    receiver.getCorrectChannel(),
                    receiver.getRepresentation()
                            + ", you cannot forcibly take " + giver.getRepresentationNoPing() + "'s"
                            + " Action Cards because they have _Astromantic Cloak (Steel)!");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (var entry : giver.getActionCards().entrySet()) {
            String acName = Mapper.getActionCard(entry.getKey()).getName();
            buttons.add(Buttons.green(
                    "FFCC_" + giver.getFaction() + "_forceGiveAC_" + entry.getValue() + "_" + receiver.getFaction(),
                    acName,
                    CardEmojis.getACEmoji(giver)));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), giver.getRepresentationNoPing() + " has no action cards to give.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(giver.getCardsInfoThread(), prompt, buttons);
    }
}
