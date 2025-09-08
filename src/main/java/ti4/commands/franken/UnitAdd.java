package ti4.commands.franken;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.service.franken.FrankenUnitService;

class UnitAdd extends UnitAddRemove {
    private static final String ALLOW_DUPLICATES = "allow_duplicates";

    public UnitAdd() {
        super(Constants.UNIT_ADD, "Add a unit to your faction");
        addOptions(new OptionData(OptionType.BOOLEAN, ALLOW_DUPLICATES, "Prevent removing duplicate unit types."));
    }

    @Override
    public void doAction(Player player, List<String> unitIDs, SlashCommandInteractionEvent event) {
        FrankenUnitService.addUnits(
                event, player, unitIDs, event.getOption(ALLOW_DUPLICATES, false, OptionMapping::getAsBoolean));
    }
}
