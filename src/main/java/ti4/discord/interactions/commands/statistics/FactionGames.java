package ti4.discord.interactions.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.SearchGameForFactionHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;

class FactionGames extends Subcommand {

    public FactionGames() {
        super(Constants.STATISTICS_FACTION_GAMES, "List games that contain a certain faction");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction to Show")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(
                OptionType.BOOLEAN, Constants.ENDED_GAMES, "True to show ended games as well (default = false)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean includeEndedGames = event.getOption(Constants.ENDED_GAMES, Boolean.FALSE, OptionMapping::getAsBoolean);
        String rawFaction = event.getOption(Constants.FACTION, OptionMapping::getAsString);
        String faction = AliasHandler.resolveFaction(rawFaction);
        if (!Mapper.isValidFaction(faction)) {
            MessageHelper.sendMessageToEventChannel(event, "Faction `" + rawFaction + "` is not valid.");
            return;
        }
        SearchGameForFactionHelper.searchGames(faction, event, includeEndedGames);
    }
}
