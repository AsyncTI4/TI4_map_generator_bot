package ti4.commands.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.tech.ShowTechDeckService;

class TechShowDeck extends GameStateSubcommand {

    public TechShowDeck() {
        super("show_deck", "Look at the available non-faction technology", false, false);
        addOptions(new OptionData(OptionType.STRING, Constants.TECH_TYPE, "The deck type you wish to to see")
            .setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ShowTechDeckService.displayTechDeck(getGame(), event, event.getOption(Constants.TECH_TYPE).getAsString());
    }
}
