package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ResetLeader extends LeaderSubcommandData {
    public ResetLeader() {
        super(Constants.RESET, "Reset all leaders");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        OptionMapping leader = event.getOption(Constants.LEADER);
        if (leader != null) {
            player.initLeaders();
            MessageHelper.sendMessageToChannel(event.getChannel(), "Leaders were reset");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Need to specify CC's");
        }
    }
}
