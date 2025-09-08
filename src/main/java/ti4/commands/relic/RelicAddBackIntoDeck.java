package ti4.commands.relic;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class RelicAddBackIntoDeck extends GameStateSubcommand {

    public RelicAddBackIntoDeck() {
        super(Constants.ADD_BACK_INTO_DECK, "Add relic back into deck if already purged", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to add back into deck")
                .setAutoComplete(true)
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String relicId = event.getOption(Constants.RELIC).getAsString();
        List<String> allRelics = game.getAllRelics();
        if (!allRelics.contains(relicId)) {
            game.shuffleRelicBack(relicId);
            MessageHelper.sendMessageToEventChannel(event, "Relic " + relicId + " added back into deck");
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Invalid relic or specified relic exists in deck");
        }
    }
}
