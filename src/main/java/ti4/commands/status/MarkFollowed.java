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

public class MarkFollowed extends StatusSubcommandData {
    public MarkFollowed() {
        super(Constants.MARK_FOLLOWED, "Mark player as having followed a Strategy Card");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC, "Inititive value of Strategy Card that was followed").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color who followed").setAutoComplete(true).setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayer(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found/");
            return;
        }

        OptionMapping option = event.getOption(Constants.SC);
        player.addFollowedSC(option.getAsInt());
        MessageHelper.sendMessageToChannel(event.getChannel(),
            "Successfully marked " + player.getRepresentation() + " as having followed " + Helper.getSCName(option.getAsInt(), game) + ".");

    }

}
