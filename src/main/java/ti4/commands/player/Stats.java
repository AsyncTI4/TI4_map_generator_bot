package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.StringTokenizer;

public class Stats extends PlayerSubcommandData {
    public Stats() {
        super(Constants.STATS, "Player Stats: CC,TG,Commodities");
        addOptions(new OptionData(OptionType.STRING, Constants.CC, "CC's Example: 3/3/2"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.TG, "Trade goods count"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.COMMODITIES, "Commodity count"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        @SuppressWarnings("ConstantConditions")
        String cc = AliasHandler.resolveFaction(event.getOption(Constants.CC).getAsString().toLowerCase());
        StringTokenizer tokenizer = new StringTokenizer(cc, "/");
        if (tokenizer.countTokens() != 3) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Wrong format for tokens count. Must be 3/3/3");
        } else {
            try {
                player.setTacticalCC(Integer.parseInt(tokenizer.nextToken()));
                player.setFleetCC(Integer.parseInt(tokenizer.nextToken()));
                player.setStrategicCC(Integer.parseInt(tokenizer.nextToken()));
            } catch (Exception e) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Not number entered, check CC count again");
            }
        }
        player.setTg(event.getOption(Constants.TG).getAsInt());
        player.setCommodities(event.getOption(Constants.COMMODITIES).getAsInt());

    }
}
