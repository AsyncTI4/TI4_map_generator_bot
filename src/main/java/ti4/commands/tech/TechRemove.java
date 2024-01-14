package ti4.commands.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Player;

public class TechRemove extends TechAddRemove {
    public TechRemove() {
        super(Constants.TECH_REMOVE, "Remove Tech");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        player.removeTech(techID);
        sendMessage(player.getRepresentation() + " removed tech: " + Mapper.getTech(techID).getRepresentation(false));
    }
}
