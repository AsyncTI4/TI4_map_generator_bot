package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;

abstract public class LeaderAction extends LeaderSubcommandData {
    public LeaderAction(String id, String description) {
        super(id, description);
        options();
    }

    protected void options() {
        addOptions(new OptionData(OptionType.STRING, Constants.LEADER, "Leader for which to do action").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        String leaderID = event.getOption(Constants.LEADER, null, OptionMapping::getAsString);
        if (leaderID == null) {
            sendMessage("Need to specify leader");
            return;
        }
        
        action(event, leaderID, activeMap, player); 
    }

    abstract void action(SlashCommandInteractionEvent event, String leader, Map activeMap, Player player);
}
