package ti4.commands.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Player;

public class TechRefresh extends TechAddRemove {
    public TechRefresh() {
        super(Constants.TECH_REFRESH, "Ready Tech");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        player.refreshTech(techID);
        sendMessage(player.getRepresentation() + " readied tech: " + Mapper.getTech(techID).getRepresentation(false));
    }
}
