package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class SwapStage1 extends GameStateSubcommand {

    public SwapStage1() {
        super(Constants.SWAP_STAGE1, "Swap the place of 1 Objective With Another", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.LOCATION1, "Location 1").setRequired(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.LOCATION2, "Location 2").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        int loc1 = event.getOption(Constants.LOCATION1).getAsInt();
        int loc2 = event.getOption(Constants.LOCATION2).getAsInt();
        game.swapStage1(loc1, loc2);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Objectives at position " + loc1 + " and position " + loc2 + " swapped.");
    }
}
