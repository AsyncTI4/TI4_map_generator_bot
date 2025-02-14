package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class SetUpPeakableObjectives extends GameStateSubcommand {

    public SetUpPeakableObjectives() {
        super(
                Constants.SETUP_PEAKABLE_OBJECTIVES,
                "Set up how many remaining unrevealed objectives there are",
                true,
                true);
        addOptions(new OptionData(
                OptionType.INTEGER, Constants.NUMBER_OF_STAGE1_OBJECTIVES, "How many unrevealed stage 1s"));
        addOptions(new OptionData(
                OptionType.INTEGER, Constants.NUMBER_OF_STAGE2_OBJECTIVES, "How many unrevealed stage 2s"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        OptionMapping loc1 = event.getOption(Constants.NUMBER_OF_STAGE1_OBJECTIVES);
        if (loc1 != null) {
            game.setUpPeakableObjectives(loc1.getAsInt(), 1);
        }
        OptionMapping loc2 = event.getOption(Constants.NUMBER_OF_STAGE2_OBJECTIVES);
        if (loc2 != null) {
            game.setUpPeakableObjectives(loc2.getAsInt(), 2);
        }
        MessageHelper.sendMessageToChannel(
                event.getChannel(), "Set up objective decks. Check map to confirm remaining unrevealed objectives.");
    }
}
