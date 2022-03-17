package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class Stats extends PlayerSubcommandData{
    public Stats() {
        super(Constants.STATS, "Player Stats: CC,TG,Commodities");
        addOptions(new OptionData(OptionType.STRING, Constants.CC, "CC's Example: 3/3/2"))
                .addOptions(new OptionData(OptionType.STRING, Constants.TG, "Trade goods count"))
                .addOptions(new OptionData(OptionType.STRING, Constants.COMMODITIES, "Commodity count"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Player Stats received");
    }
}
