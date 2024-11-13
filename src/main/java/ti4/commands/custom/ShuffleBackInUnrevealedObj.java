package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public class ShuffleBackInUnrevealedObj extends GameStateSubcommand {

    public ShuffleBackInUnrevealedObj() {
        super(Constants.SHUFFLE_BACK_IN_UNREVEALED_OBJ, "Shuffle an unrevealed objective back in and redraw one in its place", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.LOCATION1, "Location of the unrevealed objective").setRequired(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.STAGE_1_OR_2, "Stage 1 or 2").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        OptionMapping loc1 = event.getOption(Constants.LOCATION1);
        OptionMapping loc2 = event.getOption(Constants.STAGE_1_OR_2);
        String id = game.getTopObjective(loc2.getAsInt());
        game.swapObjectiveOut(loc2.getAsInt(), loc1.getAsInt() - 1, id);
        MessageHelper.sendMessageToChannel(event.getChannel(),
            "Shuffle objective at position " + loc1.getAsInt() + " back into the deck and drew a new one there.");
        GameSaveLoadManager.saveGame(game, event);
    }
}
