package ti4.commands2.special;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.leader.CommanderUnlockCheckService;

class AddFactionCCToFleetSupply extends AddRemoveFactionCCToFromFleet {

    public AddFactionCCToFleetSupply() {
        super(Constants.ADD_CC_TO_FS, "Add Faction Command Token to Fleet Pool");
    }

    @Override
    void action(SlashCommandInteractionEvent event, List<String> colors, Game game, Player player) {
        for (String color : colors) {
            player.addMahactCC(color);
            Helper.isCCCountCorrect(event, game, color);
        }
        CommanderUnlockCheckService.checkPlayer(player, "mahact");
    }
}
