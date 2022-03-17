package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class Stats extends PlayerSubcommandData{
    public Stats() {
        super(Constants.STATS, "Player Stats: CC,TG,Commodities");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Player Stats received");
    }
}
