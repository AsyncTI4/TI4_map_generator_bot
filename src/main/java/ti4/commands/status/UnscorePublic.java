package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class UnscorePublic extends StatusSubcommandData {
    public UnscorePublic() {
        super(Constants.UNSCORE_OBJECTIVE, "Unscore Public Objective");
        addOptions(new OptionData(OptionType.INTEGER, Constants.PO_ID, "Public Objective ID that is between ()").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which to score Public Objective"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayerFromEvent(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        OptionMapping option = event.getOption(Constants.PO_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please select what Public Objective to score");
            return;
        }

        boolean scored = game.unscorePublicObjective(player.getUserID(), option.getAsInt());
        if (!scored) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Public Objective ID found, please retry");
        }
    }
}
