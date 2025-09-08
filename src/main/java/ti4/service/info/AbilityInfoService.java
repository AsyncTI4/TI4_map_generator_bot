package ti4.service.info;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.commands.CommandHelper;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AbilityModel;

@UtilityClass
public class AbilityInfoService {

    public static void sendAbilityInfo(Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentation() + " Somebody" + CommandHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, headerText);
        sendAbilityInfo(player);
    }

    public static void sendAbilityInfo(Player player) {
        MessageHelper.sendMessageEmbedsToCardsInfoThread(player, "__Abilities:__", getAbilityMessageEmbeds(player));
    }

    private static List<MessageEmbed> getAbilityMessageEmbeds(Player player) {
        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        for (AbilityModel model : player.getAbilities().stream()
                .map(Mapper::getAbility)
                .sorted(Comparator.comparing(AbilityModel::getAlias))
                .toList()) {
            MessageEmbed representationEmbed = model.getRepresentationEmbed();
            messageEmbeds.add(representationEmbed);
        }
        return messageEmbeds;
    }
}
