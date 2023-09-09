package ti4.commands.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Player;

public class TechRefresh extends TechAddRemove {
    public TechRefresh() {
        super(Constants.TECH_REFRESH, "Ready Tech");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        player.refreshTech(techID);
        sendMessage(Helper.getPlayerRepresentation(player, getActiveGame()) + " readied tech: " + Helper.getTechRepresentation(techID));
    }
}
