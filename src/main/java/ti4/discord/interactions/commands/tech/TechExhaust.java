package ti4.discord.interactions.commands.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.tech.PlayerTechService;

class TechExhaust extends TechAddRemove {

    TechExhaust() {
        super(Constants.TECH_EXHAUST, "Exhaust a technology");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        PlayerTechService.exhaustTechAndResolve(event, getGame(), player, techID);
        PlayerTechService.checkAndApplyCombatMods(event, player, techID);
    }
}
