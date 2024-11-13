package ti4.commands.relic;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class RelicAddBackIntoDeck extends RelicSubcommandData {

    public RelicAddBackIntoDeck() {
        super(Constants.ADD_BACK_INTO_DECK, "Add relic back into deck if already purged");
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to add back into deck").setAutoComplete(true).setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayerFromEvent(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.RELIC);
        if (option == null) {
            MessageHelper.sendMessageToEventChannel(event, "Specify relic");
            return;
        }
        String relicId = option.getAsString();
        List<String> allRelics = getActiveGame().getAllRelics();
        if (!allRelics.contains(relicId)) {
            getActiveGame().shuffleRelicBack(relicId);
            MessageHelper.sendMessageToEventChannel(event, "Relic " + relicId + " added back into deck");
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Invalid relic or specified relic exists in deck");
        }
    }
}
