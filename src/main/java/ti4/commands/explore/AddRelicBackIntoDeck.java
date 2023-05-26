package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import ti4.helpers.Constants;
import ti4.map.Player;

import java.util.List;

public class AddRelicBackIntoDeck extends GenericRelicAction {

    public AddRelicBackIntoDeck() {
        super(Constants.ADD_BACK_INTO_DECK, "Add relic back into deck if already purged", true);
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to add back into deck").setAutoComplete(true).setRequired(true));
    }

    @Override
    public void doAction(Player player, SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.RELIC);
        if (option == null) {
            sendMessage("Specify relic");
            return;
        }
        String relicId = option.getAsString();
        List<String> allRelics = getActiveMap().getAllRelics();
        if (!allRelics.contains(relicId)){
            getActiveMap().shuffleRelicBack(relicId);
            sendMessage("Relic " + relicId + " added back into deck");
        } else {
            sendMessage("Invalid relic or specified relic exists in deck");
        }
    }
}
