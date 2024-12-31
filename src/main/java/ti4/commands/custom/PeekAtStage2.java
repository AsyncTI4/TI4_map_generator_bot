package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.ObjectiveHelper;

class PeekAtStage2 extends GameStateSubcommand {

    public PeekAtStage2() {
        super(Constants.PEEK_AT_STAGE2, "Peek at a stage 2 objective", false, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.LOCATION1, "Location Of Objective (typical 1-5)").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Integer loc1 = event.getOption(Constants.LOCATION1, null, OptionMapping::getAsInt);
        ObjectiveHelper.secondHalfOfPeakStage2(getGame(), getPlayer(), loc1);
    }
}
