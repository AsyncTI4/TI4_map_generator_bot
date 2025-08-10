package ti4.commands.search;

import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;

public class SearchAttachmentsSubcommand extends SearchComponentModelSubcommand {

    public SearchAttachmentsSubcommand() {
        super(Constants.SEARCH_ATTACHMENTS, "List all attachments the bot can use");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source =
                ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));

        if (Mapper.isValidAttachment(searchString)) {
            event.getChannel()
                    .sendMessageEmbeds(Mapper.getAttachmentInfo(searchString).getRepresentationEmbed())
                    .queue();
            return;
        }

        List<MessageEmbed> messageEmbeds = Mapper.getAttachments().values().stream()
                .filter(model -> model.search(searchString, source))
                .sorted((r1, r2) -> r1.getSource().compareTo(r2.getSource()))
                .map(model -> model.getRepresentationEmbed())
                .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
