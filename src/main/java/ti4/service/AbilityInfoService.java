package ti4.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AbilityModel;

@UtilityClass
public class AbilityInfoService {

    @ButtonHandler("refreshAbilityInfo")
    public static void sendAbilityInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentation() + CommandHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        sendAbilityInfo(game, player);
    }

    public static void sendAbilityInfo(Game game, Player player) {
        MessageHelper.sendMessageEmbedsToCardsInfoThread(game, player, "_ _\n__**Abilities:**__", getAbilityMessageEmbeds(player));
    }

    private static List<MessageEmbed> getAbilityMessageEmbeds(Player player) {
        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        for (AbilityModel model : player.getAbilities().stream().map(Mapper::getAbility).sorted(Comparator.comparing(AbilityModel::getAlias)).toList()) {
            MessageEmbed representationEmbed = model.getRepresentationEmbed();
            messageEmbeds.add(representationEmbed);
        }
        return messageEmbeds;
    }
}
