package ti4.commands2.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.service.tech.PlayerTechService;

class TechPurge extends TechAddRemove {

    public TechPurge() {
        super(Constants.TECH_PURGE, "Purge Tech (Can't research/gain it again)");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        PlayerTechService.purgeTech(event, player, techID);
    }
}
