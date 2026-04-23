package ti4.discord.interactions.slashcommands.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.tech.PlayerTechService;

class TechAdd extends TechAddRemove {

    TechAdd() {
        super(Constants.TECH_ADD, "Add a technology");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        PlayerTechService.addTech(event, getGame(), player, techID);
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
