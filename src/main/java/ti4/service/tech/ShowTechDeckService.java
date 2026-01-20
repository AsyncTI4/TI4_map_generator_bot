package ti4.service.tech;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
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

    /**
     * Display the tech deck(s).
     *
     * @param game  The game instance
     * @param event The interaction event
     * @param deck  The deck to show, or null to show all decks
     */
    public static void displayTechDeck(Game game, GenericInteractionCreateEvent event, String deck) {
        if (deck != null) {
            List<TechnologyModel> techs =
                    switch (deck) {
                        case Constants.BIOTIC -> game.getBioticTechDeck();
                        case Constants.CYBERNETIC -> game.getCyberneticTechDeck();
                        case Constants.PROPULSION -> game.getPropulsionTechDeck();
                        case Constants.WARFARE -> game.getWarfareTechDeck();
                        case Constants.UNIT_UPGRADE -> game.getUnitUpgradeTechDeck();
                        default -> new ArrayList<>();
                    };
            List<MessageEmbed> embeds = techs.stream()
                    .filter(t -> !t.isFactionTech())
                    .map(t -> t.getRepresentationEmbed(false, true))
                    .toList();
            String message = StringUtils.capitalize(deck) + " Technology Deck:";
            MessageHelper.sendMessageToChannelWithEmbeds(event.getMessageChannel(), message, embeds);
        } else {
            List<TechnologyModel> techs = Stream.of(
                            game.getBioticTechDeck(),
                            game.getCyberneticTechDeck(),
                            game.getPropulsionTechDeck(),
                            game.getWarfareTechDeck(),
                            game.getUnitUpgradeTechDeck())
                    .flatMap(Collection::stream)
                    .toList();

            List<MessageEmbed> embeds = techs.stream()
                    .filter(t -> !t.isFactionTech())
                    .map(t -> t.getRepresentationEmbed(false, true))
                    .toList();
            String message = "Technology Decks:";
            MessageHelper.sendMessageToChannelWithEmbeds(event.getMessageChannel(), message, embeds);
        }
    }
}
