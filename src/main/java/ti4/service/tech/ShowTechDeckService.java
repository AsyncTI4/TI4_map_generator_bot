package ti4.service.tech;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;

@UtilityClass
public class ShowTechDeckService {

    public static void displayTechDeck(Game game, GenericInteractionCreateEvent event, String deck) {
        List<TechnologyModel> techs = switch (deck) {
            case Constants.PROPULSION -> game.getPropulsionTechDeck();
            case Constants.WARFARE -> game.getWarfareTechDeck();
            case Constants.CYBERNETIC -> game.getCyberneticTechDeck();
            case Constants.BIOTIC -> game.getBioticTechDeck();
            case Constants.UNIT_UPGRADE -> game.getUnitUpgradeTechDeck();
            default -> new ArrayList<>();
        };
        List<MessageEmbed> embeds = techs.stream()
            .filter(t -> !t.isFactionTech())
            .map(t -> t.getRepresentationEmbed(false, true))
            .toList();
        String message = StringUtils.capitalize(deck) + " Tech Deck:";
        MessageHelper.sendMessageToChannelWithEmbeds(event.getMessageChannel(), message, embeds);
    }
}
