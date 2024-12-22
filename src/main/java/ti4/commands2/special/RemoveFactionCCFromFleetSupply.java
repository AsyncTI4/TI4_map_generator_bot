package ti4.commands2.special;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;

class RemoveFactionCCFromFleetSupply extends AddRemoveFactionCCToFromFleet {

    public RemoveFactionCCFromFleetSupply() {
        super(Constants.REMOVE_CC_FROM_FS, "Remove faction command token from fleet pool");
    }

    @Override
    void action(SlashCommandInteractionEvent event, List<String> colors, Game game, Player player) {
        for (String color : colors) {
            player.removeMahactCC(color);
        }
    }
}
