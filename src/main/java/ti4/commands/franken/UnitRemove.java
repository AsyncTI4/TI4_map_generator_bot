package ti4.commands.franken;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.service.franken.FrankenUnitService;

class UnitRemove extends UnitAddRemove {

    public UnitRemove() {
        super(Constants.UNIT_REMOVE, "Remove an unit from your faction");
    }

    @Override
    public void doAction(Player player, List<String> unitIDs, SlashCommandInteractionEvent event) {
        FrankenUnitService.removeUnits(event, player, unitIDs);
    }
}
