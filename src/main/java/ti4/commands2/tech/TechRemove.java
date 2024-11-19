package ti4.commands2.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.service.tech.PlayerTechService;

class TechRemove extends TechAddRemove {

    public TechRemove() {
        super(Constants.TECH_REMOVE, "Remove Tech");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        PlayerTechService.removeTech(event, player, techID);
    }
}
