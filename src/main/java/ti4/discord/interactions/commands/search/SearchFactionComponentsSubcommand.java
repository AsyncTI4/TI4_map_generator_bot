package ti4.discord.interactions.commands.search;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.info.FactionComponentsService;

class SearchFactionComponentsSubcommand extends Subcommand {

    SearchFactionComponentsSubcommand() {
        super(Constants.SEARCH_FACTION_COMPONENTS, "Show all of a faction's components");
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION, "Faction")
                        .setRequired(true)
                        .setAutoComplete(true),
                new OptionData(OptionType.BOOLEAN, Constants.SHOW_MONUMENT, "Include the faction's monument"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String faction = event.getOption(Constants.FACTION, OptionMapping::getAsString);
        if (!Mapper.isValidFaction(faction)) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid faction.");
            return;
        }
        boolean showMonument = event.getOption(Constants.SHOW_MONUMENT, false, OptionMapping::getAsBoolean);
        FactionComponentsService.sendFactionComponents(event.getMessageChannel(), faction, showMonument);
    }
}
