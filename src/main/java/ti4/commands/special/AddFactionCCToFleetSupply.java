package ti4.commands.special;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;

public class AddFactionCCToFleetSupply extends AddRemoveFactionCCToFromFleet {

    public AddFactionCCToFleetSupply() {
        super(Constants.ADD_CC_TO_FS, "Add Faction CC to Fleet Supply");
    }

    @Override
    void action(SlashCommandInteractionEvent event, List<String> colors, Game game, Player player) {
        for (String color : colors) {
            player.addMahactCC(color);
            Helper.isCCCountCorrect(event, game, color);
        }
        CommanderUnlockCheck.checkPlayer(player, "mahact");
        GameSaveLoadManager.saveGame(game, event);

    }
}
