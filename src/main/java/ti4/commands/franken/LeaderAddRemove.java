package ti4.commands.franken;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;

public abstract class LeaderAddRemove extends FrankenSubcommandData {
    public LeaderAddRemove(String name, String description) {
        super(name, description);
        addOptions(new OptionData(OptionType.STRING, Constants.LEADER, "Leader Name").setRequired(true).setAutoComplete(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        List<String> leaderIDs = new ArrayList<>();

        //GET ALL ABILITY OPTIONS AS STRING
        for (OptionMapping option : event.getOptions().stream().filter(o -> o != null && o.getName().contains(Constants.LEADER)).toList()) {
            leaderIDs.add(option.getAsString());
        }

        leaderIDs.removeIf(StringUtils::isEmpty);
        leaderIDs.removeIf(leaderID -> !Mapper.getLeaderRepresentations().keySet().contains(leaderID));
        
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        doAction(player, leaderIDs);
    }

    public abstract void doAction(Player player, List<String> leaderIDs);
    
}
