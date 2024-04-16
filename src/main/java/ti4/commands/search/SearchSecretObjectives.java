package ti4.commands.search;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.SecretObjectiveModel;
import ti4.model.Source.ComponentSource;

public class SearchSecretObjectives extends SearchComponentModel {

    public SearchSecretObjectives() {
        super(Constants.SEARCH_SECRET_OBJECTIVES, "List all secret objectives the bot can use");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source = ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));
        
        if (Mapper.isValidSecretObjective(searchString)) {
            event.getChannel().sendMessageEmbeds(Mapper.getSecretObjective(searchString).getRepresentationEmbed(true)).queue();
            return;
        }
        
        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        for (SecretObjectiveModel model : Mapper.getSecretObjectives().values().stream().sorted(SecretObjectiveModel.sortByPointsAndName).toList()) {
            MessageEmbed representationEmbed = model.getRepresentationEmbed(true);
            if (Helper.embedContainsSearchTerm(representationEmbed, searchString)) messageEmbeds.add(representationEmbed);
        }
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
