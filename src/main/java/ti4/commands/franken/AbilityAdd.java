package ti4.commands.franken;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.service.franken.FrankenAbilityService;

class AbilityAdd extends AbilityAddRemove {

    public AbilityAdd() {
        super(Constants.ABILITY_ADD, "Add an ability to your faction");
    }

    @Override
    public void doAction(Player player, List<String> abilityIDs, SlashCommandInteractionEvent event) {
        FrankenAbilityService.addAbilities(event, player, abilityIDs);
    }
}
