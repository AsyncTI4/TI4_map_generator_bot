package ti4.commands2.franken;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.service.franken.FrankenAbilityService;

class AbilityRemove extends AbilityAddRemove {

    public AbilityRemove() {
        super(Constants.ABILITY_REMOVE, "Remove an ability from your faction");
    }

    @Override
    public void doAction(Player player, List<String> abilityIDs, SlashCommandInteractionEvent event) {
        FrankenAbilityService.removeAbilities(event, player, abilityIDs);
    }
}
