package ti4.service.info;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.franken.FrankenAlternateTextService;

@UtilityClass
public class TechInfoService {

    public static void sendTechInfo(Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentation() + " Somebody" + CommandHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, headerText);
        sendTechInfo(player);
    }

    public static void sendTechInfo(Player player) {
        MessageHelper.sendMessageEmbedsToCardsInfoThread(
                player, "__Technologies Researched:__", getTechMessageEmbeds(player));
        MessageHelper.sendMessageEmbedsToCardsInfoThread(
                player, "__Faction Technologies (Not Yet Researched)__", getFactionTechMessageEmbeds(player));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), null, getTechButtons());
    }

    private static List<Button> getTechButtons() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.REFRESH_TECH_INFO);
        return buttons;
    }

    private static List<MessageEmbed> getTechMessageEmbeds(Player player) {
        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        for (TechnologyModel techModel : player.getTechs().stream()
                .map(Mapper::getTech)
                .sorted(TechnologyModel.sortByTechRequirements)
                .toList()) {
            MessageEmbed representationEmbed =
                    FrankenAlternateTextService.getTechnologyEmbed(player.getGame(), techModel, false, false);
            messageEmbeds.add(representationEmbed);
        }
        return messageEmbeds;
    }

    private static List<MessageEmbed> getFactionTechMessageEmbeds(Player player) {
        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        List<String> notResearchedFactionTechs = player.getNotResearchedFactionTechs();
        for (TechnologyModel techModel : notResearchedFactionTechs.stream()
                .map(Mapper::getTech)
                .sorted(TechnologyModel.sortByTechRequirements)
                .toList()) {
            MessageEmbed representationEmbed =
                    FrankenAlternateTextService.getTechnologyEmbed(player.getGame(), techModel, false, true);
            messageEmbeds.add(representationEmbed);
        }
        return messageEmbeds;
    }
}
