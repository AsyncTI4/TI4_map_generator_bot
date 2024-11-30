package ti4.commands2.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.info.UnitInfoService;

class UnitInfo extends GameStateSubcommand {

    public UnitInfo() {
        super(Constants.UNIT_INFO, "Send special unit information to your Cards Info channel", false, true);
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_ALL_UNITS, "'True' also show basic (non-faction) units (Default: False)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean showAllUnits = event.getOption(Constants.SHOW_ALL_UNITS, false, OptionMapping::getAsBoolean);
        UnitInfoService.sendUnitInfo(getGame(), getPlayer(), event, showAllUnits);
    }
}
