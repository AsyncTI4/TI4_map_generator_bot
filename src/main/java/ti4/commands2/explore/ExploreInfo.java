package ti4.commands2.explore;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.service.explore.ExploreService;

class ExploreInfo extends GameStateSubcommand {

    public ExploreInfo() {
        super(Constants.INFO, "Display cards in exploration decks and discards.", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TRAIT, "Cultural, Industrial, Hazardous, or Frontier.").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.OVERRIDE_FOW, "TRUE if override fog"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping override = event.getOption(Constants.OVERRIDE_FOW);
        boolean over = false;
        if (override != null) {
            over = "TRUE".equalsIgnoreCase(override.getAsString());
        }

        OptionMapping reqType = event.getOption(Constants.TRAIT);
        List<String> types = new ArrayList<>();
        if (reqType != null) {
            types.add(reqType.getAsString());
        } else {
            types.add(Constants.CULTURAL);
            types.add(Constants.INDUSTRIAL);
            types.add(Constants.HAZARDOUS);
            types.add(Constants.FRONTIER);
        }
        Game game = getGame();
        ExploreService.secondHalfOfExpInfo(types, event, getPlayer(), game, over);
    }
}
