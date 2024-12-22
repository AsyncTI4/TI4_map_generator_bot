package ti4.commands2.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.service.tech.PlayerTechService;

class TechAdd extends TechAddRemove {

    public TechAdd() {
        super(Constants.TECH_ADD, "Add a technology");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        PlayerTechService.addTech(event, getGame(), player, techID);
    }
}
