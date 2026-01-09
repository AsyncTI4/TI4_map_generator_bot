package ti4.commands.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.tech.ShowTechDeckService;

class TechShowDeck extends GameStateSubcommand {

    public TechShowDeck() {
        super(Constants.TECH_SHOW_DECK, "Look at the available non-faction technology", false, false);
        addOptions(new OptionData(
                        OptionType.STRING, Constants.TECH_TYPE_AND_UNIT_UPGRADES, "The deck type you wish to to see")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping techType = event.getOption(Constants.TECH_TYPE_AND_UNIT_UPGRADES);
        ShowTechDeckService.displayTechDeck(getGame(), event, techType != null ? techType.getAsString() : null);
    }
}
