package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public class SwapStage1 extends CustomSubcommandData {
    public SwapStage1() {
        super(Constants.SWAP_STAGE1, "Swap the place of 1 Objective With Another");
        addOptions(new OptionData(OptionType.INTEGER, Constants.LOCATION1, "Location 1").setRequired(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.LOCATION2, "Location 2").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        OptionMapping loc1 = event.getOption(Constants.LOCATION1);
        OptionMapping loc2 = event.getOption(Constants.LOCATION2);
        game.swapStage1(loc1.getAsInt(), loc2.getAsInt());
        MessageHelper.sendMessageToChannel(event.getChannel(), "Objectives at position " + loc1.getAsInt() + " and position " + loc2.getAsInt() + " swapped.");
        GameSaveLoadManager.saveGame(game, event);
    }
}
