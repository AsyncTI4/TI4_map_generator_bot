package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;

import java.util.ArrayList;

public class AddFactionCCToFleetSupply extends AddRemoveFactionCCToFromFleet{
    public AddFactionCCToFleetSupply() {
        super(Constants.ADD_CC_TO_FS, "Add Faction CC to Fleet Supply");
    }

    @Override
    void action(SlashCommandInteractionEvent event, ArrayList<String> colors, Map activeMap, Player player) {
        for (String color : colors) {
            player.addMahactCC(color);
        }
        if(player.getLeaderIDs().contains("mahactcommander") && !player.hasLeaderUnlocked("mahactcommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "mahact", event);
        }
        MapSaveLoadManager.saveMap(activeMap, event);

    }
}
