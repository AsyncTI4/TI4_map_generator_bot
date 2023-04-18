package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;

import java.util.ArrayList;

public class RemoveFactionCCFromFleetSupply extends AddRemoveFactionCCToFromFleet {
    public RemoveFactionCCFromFleetSupply() {
        super(Constants.REMOVE_CC_FROM_FS, "Remove Faction CC from Fleet Supply");
    }

    @Override
    void action(SlashCommandInteractionEvent event, ArrayList<String> colors, Map activeMap, Player player) {
        for (String color : colors) {
            player.removeMahactCC(color);
        }
        MapSaveLoadManager.saveMap(activeMap, event);
    }
}
