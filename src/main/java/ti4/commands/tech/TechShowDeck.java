package ti4.commands.tech;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;

public class TechShowDeck extends TechSubcommandData {
    public TechShowDeck() {
        super("show_deck", "Look at available non-faction techs");
        addOptions(new OptionData(OptionType.STRING, Constants.TECH_TYPE, "The deck type you want to see").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String techType = event.getOption(Constants.TECH_TYPE, null, OptionMapping::getAsString);
        if (techType == null) {
            return;
        }
        displayTechDeck(getActiveGame(), event, techType);
    }

    public static void displayTechDeck(Game game, GenericInteractionCreateEvent event, String deck) {
        List<TechnologyModel> techs = switch (deck) {
            case Constants.PROPULSION -> game.getPropulsionTechDeck();
            case Constants.WARFARE -> game.getWarfareTechDeck();
            case Constants.CYBERNETIC -> game.getCyberneticTechDeck();
            case Constants.BIOTIC -> game.getBioticTechDeck();
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
