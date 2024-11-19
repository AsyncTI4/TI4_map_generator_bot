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
import ti4.model.TechnologyModel;

@UtilityClass
public class TechInfoService {

    public static void sendTechInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentation() + CommandHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        sendTechInfo(game, player);
    }

    public static void sendTechInfo(Game game, Player player) {
        MessageHelper.sendMessageEmbedsToCardsInfoThread(game, player, "_ _\n__**Technologies Researched:**__", getTechMessageEmbeds(player));
        MessageHelper.sendMessageEmbedsToCardsInfoThread(game, player, "_ _\n__**Faction Technologies (Not Yet Researched)**__", getFactionTechMessageEmbeds(player));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), null, getTechButtons());
    }

    private static List<Button> getTechButtons() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.REFRESH_TECH_INFO);
        return buttons;
    }

    private static List<MessageEmbed> getTechMessageEmbeds(Player player) {
        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        for (TechnologyModel techModel : player.getTechs().stream().map(Mapper::getTech).sorted(TechnologyModel.sortByTechRequirements).toList()) {
            MessageEmbed representationEmbed = techModel.getRepresentationEmbed();
            messageEmbeds.add(representationEmbed);
        }
        return messageEmbeds;
    }

    private static List<MessageEmbed> getFactionTechMessageEmbeds(Player player) {
        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        List<String> notResearchedFactionTechs = player.getNotResearchedFactionTechs();
        for (TechnologyModel techModel : notResearchedFactionTechs.stream().map(Mapper::getTech).sorted(TechnologyModel.sortByTechRequirements).toList()) {
            MessageEmbed representationEmbed = techModel.getRepresentationEmbed(false, true);
            messageEmbeds.add(representationEmbed);
        }
        return messageEmbeds;
    }
}
