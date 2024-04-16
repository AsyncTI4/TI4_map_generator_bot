package ti4.commands.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;
import ti4.model.Source.ComponentSource;


public class SearchPlanets extends SearchComponentModel {

    public SearchPlanets() {
        super(Constants.SEARCH_PLANETS, "List all planets");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_ALIASES, "Set to true to also include common aliases, the ID, and source of the unit."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source = ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));
        boolean includeAliases = event.getOption(Constants.INCLUDE_ALIASES, false, OptionMapping::getAsBoolean);

        if (Mapper.isValidPlanet(searchString)) {
            event.getChannel().sendMessageEmbeds(Mapper.getPlanet(searchString).getRepresentationEmbed(includeAliases)).queue();
            return;
        }

        List<MessageEmbed> messageEmbeds = new ArrayList<>();

        for (PlanetModel model : TileHelper.getAllPlanets().values().stream().sorted(Comparator.comparing(PlanetModel::getId)).toList()) {
            MessageEmbed representationEmbed = model.getRepresentationEmbed(includeAliases);
            if (Helper.embedContainsSearchTerm(representationEmbed, searchString)) messageEmbeds.add(representationEmbed); 
        }
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
