package ti4.discord.interactions.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.ObjectiveHelper;

class PeekAtStage extends GameStateSubcommand {

    private final int stage;

    PeekAtStage(int stage) {
        super(
                stage == 1 ? Constants.PEEK_AT_STAGE1 : Constants.PEEK_AT_STAGE2,
                "Peek at a stage " + stage + " objective",
                false,
                true);
        markSuspicious();
        this.stage = stage;
        addOptions(new OptionData(OptionType.INTEGER, Constants.LOCATION1, "Location Of Objective (typical 1-5)")
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Integer loc1 = event.getOption(Constants.LOCATION1, null, OptionMapping::getAsInt);
        if (stage == 1) {
            ObjectiveHelper.secondHalfOfPeakStage1(getGame(), getPlayer(), loc1);
        } else {
            ObjectiveHelper.secondHalfOfPeakStage2(getGame(), getPlayer(), loc1);
        }
    }
}
