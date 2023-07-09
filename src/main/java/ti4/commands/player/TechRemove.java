package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Player;

public class TechRemove extends TechAddRemove {
    public TechRemove() {
        super(Constants.TECH_REMOVE, "Remove Tech");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        player.removeTech(techID);
        sendMessage(Helper.getPlayerRepresentation(player, getActiveMap()) + " removed tech: " + Helper.getTechRepresentation(techID));
    }
}
