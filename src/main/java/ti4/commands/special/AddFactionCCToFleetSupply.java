package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;

import java.util.List;

public class AddFactionCCToFleetSupply extends AddRemoveFactionCCToFromFleet {
    public AddFactionCCToFleetSupply() {
        super(Constants.ADD_CC_TO_FS, "Add a faction command token to your fleet pool.");
    }

    @Override
    void action(SlashCommandInteractionEvent event, List<String> colors, Game game, Player player) {
        for (String color : colors) {
            player.addMahactCC(color);
        }
        if (player.getLeaderIDs().contains("mahactcommander") && !player.hasLeaderUnlocked("mahactcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "mahact", event);
        }
        GameSaveLoadManager.saveMap(game, event);

    }
}
