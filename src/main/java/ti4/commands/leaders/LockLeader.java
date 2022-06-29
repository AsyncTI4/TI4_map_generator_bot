package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;

import java.util.ArrayList;

public class LockLeader extends LeaderAction {
    public LockLeader() {
        super(Constants.LOCK_LEADER, "Lock leader");
    }

    @Override
    void action(SlashCommandInteractionEvent event, ArrayList<String> colors, Map activeMap, Player player) {
        for (String color : colors) {
//            player.addMahactCC(color);
        }
    }
}
