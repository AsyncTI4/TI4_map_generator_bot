package ti4.service.info;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands2.CommandHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;

@UtilityClass
public class RelicInfoService {

    public static void sendRelicInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentation() + CommandHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        sendRelicInfo(player);
    }

    public static void sendRelicInfo(Player player) {
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
            player.getCardsInfoThread(),
            null,
            getRelicEmbeds(player),
            getRelicButtons());
    }

    private static List<MessageEmbed> getRelicEmbeds(Player player) {
        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        for (String relicID : player.getRelics()) {
            RelicModel relicModel = Mapper.getRelic(relicID);
            if (relicModel != null) {
                MessageEmbed representationEmbed = relicModel.getRepresentationEmbed();
                messageEmbeds.add(representationEmbed);
            }
        }
        return messageEmbeds;
    }

    private static List<Button> getRelicButtons() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.REFRESH_RELIC_INFO);
        return buttons;
    }
}
