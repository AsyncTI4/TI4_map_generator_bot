package ti4.discord.interactions.commands.search;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.function.Consumers;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
import ti4.model.BreakthroughModel;
import ti4.model.Mapper;
import ti4.model.Source.ComponentSource;

class SearchBreakthroughs extends SearchComponentModelSubcommand {

    SearchBreakthroughs() {
        super(Constants.SEARCH_BREAKTHROUGHS, "List all breakthroughs the bot can use");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source =
                ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));

        if (Mapper.isValidBreakthrough(searchString)) {
            event.getChannel()
                    .sendMessageEmbeds(getEmbeds(Mapper.getBreakthrough(searchString)))
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        List<MessageEmbed> messageEmbeds = Mapper.getBreakthroughs().values().stream()
                .filter(model -> model.search(searchString, source))
                .flatMap(model -> getEmbeds(model).stream())
                .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }

    private static List<MessageEmbed> getEmbeds(BreakthroughModel breakthrough) {
        List<MessageEmbed> embeds = new ArrayList<>(List.of(breakthrough.getRepresentationEmbed(true)));
        breakthrough.getLinkedUnitUpgrade().ifPresent(upgrade -> embeds.add(upgrade.getRepresentationEmbed(true)));
        return embeds;
    }
}
