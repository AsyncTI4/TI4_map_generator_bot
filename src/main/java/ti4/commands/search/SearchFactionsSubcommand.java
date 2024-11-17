package ti4.commands.search;

import java.util.Comparator;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;

public class SearchFactionsSubcommand extends SearchComponentModelSubcommand {

    public SearchFactionsSubcommand() {
        super(Constants.SEARCH_FACTIONS, "List all factions the bot can use");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source = ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));
        
        if (Mapper.isValidFaction(searchString)) {
            event.getChannel().sendMessageEmbeds(Mapper.getFaction(searchString).getRepresentationEmbed(true, false)).queue();
            return;
        }
        
        List<MessageEmbed> messageEmbeds = Mapper.getFactions().stream()
            .filter(model -> model.search(searchString, source))
            .sorted(Comparator.comparing(FactionModel::getAlias))
            .map(model -> model.getRepresentationEmbed(true, false))
            .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
