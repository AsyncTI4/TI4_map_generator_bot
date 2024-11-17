package ti4.commands2.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;

class SearchWarrant extends GameStateSubcommand {

    public SearchWarrant() {
        super(Constants.SEARCH_WARRANT, "Search Warrant set on/off.", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Color/Faction for which we set CC's"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        getPlayer().setSearchWarrant();
    }
}
