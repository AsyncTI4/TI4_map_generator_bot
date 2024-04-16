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
import ti4.model.PromissoryNoteModel;
import ti4.model.Source.ComponentSource;

public class SearchPromissoryNotes extends SearchComponentModel {

    public SearchPromissoryNotes() {
        super(Constants.SEARCH_PROMISSORY_NOTES, "List all promissory notes the bot can use");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source = ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));
        
        if (Mapper.isValidPromissoryNote(searchString)) {
            event.getChannel().sendMessageEmbeds(Mapper.getPromissoryNote(searchString).getRepresentationEmbed(false, true, true)).queue();
            return;
        }

        List<MessageEmbed> messageEmbeds = new ArrayList<>();

        for (PromissoryNoteModel model : Mapper.getPromissoryNotes().values()) {
            MessageEmbed representationEmbed = model.getRepresentationEmbed(false, true, true);
            if (Helper.embedContainsSearchTerm(representationEmbed, searchString)) messageEmbeds.add(representationEmbed);
        }
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
