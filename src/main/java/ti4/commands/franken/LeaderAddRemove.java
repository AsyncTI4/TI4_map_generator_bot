package ti4.commands.franken;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.leaders.LeaderInfo;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public abstract class LeaderAddRemove extends FrankenSubcommandData {
    public LeaderAddRemove(String name, String description) {
        super(name, description);
        addOptions(new OptionData(OptionType.STRING, Constants.LEADER, "Leader Name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.LEADER_1, "Leader Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.LEADER_2, "Leader Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.LEADER_3, "Leader Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.LEADER_4, "Leader Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        List<String> leaderIDs = new ArrayList<>();

        //GET ALL ABILITY OPTIONS AS STRING
        for (OptionMapping option : event.getOptions().stream().filter(o -> o != null && o.getName().contains(Constants.LEADER)).toList()) {
            leaderIDs.add(option.getAsString());
        }

        leaderIDs.removeIf(StringUtils::isEmpty);
        leaderIDs.removeIf(leaderID -> !Mapper.isValidLeader(leaderID));

        if (leaderIDs.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No valid leaders were provided. Please see `/help list_leaders` for available choices.");
            return;
        }

        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayerFromEvent(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        doAction(player, leaderIDs);

        LeaderInfo.sendLeadersInfo(game, player, event);
    }

    public abstract void doAction(Player player, List<String> leaderIDs);

}
