package ti4.commands.help;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import ti4.generator.TileHelper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;


public class ListPlanets extends HelpSubcommandData {

    public ListPlanets() {
        super(Constants.LIST_PLANETS, "List all planets");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string.").setAutoComplete(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_ALIASES, "Set to true to also include common aliases, the ID, and source of the unit."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        boolean includeAliases = event.getOption(Constants.INCLUDE_ALIASES, false, OptionMapping::getAsBoolean);

        List<MessageEmbed> messageEmbeds = new ArrayList<>();

        for (PlanetModel planetModel : TileHelper.getAllPlanets().values().stream().sorted(Comparator.comparing(PlanetModel::getId)).toList()) {
            MessageEmbed planetRepresentationEmbed = planetModel.getPlanetRepresentationEmbed(includeAliases);
            if (searchString == null || planetRepresentationEmbed.getTitle().toLowerCase().contains(searchString.toLowerCase()) || (planetRepresentationEmbed.getFooter() != null && planetRepresentationEmbed.getFooter().getText().toLowerCase().contains(searchString.toLowerCase()))) {
                messageEmbeds.add(planetRepresentationEmbed);
            }
        }
        if (messageEmbeds.size() > 3) {
            String threadName = "/help list_planets" + (searchString == null ? "" : " search: " + searchString);
            MessageHelper.sendMessageEmbedsToThread(event.getChannel(), threadName, messageEmbeds);
        } else if (messageEmbeds.size() > 0) {
            event.getChannel().sendMessageEmbeds(messageEmbeds).queue();
        }
    }
}
