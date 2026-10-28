package ti4.discord.interactions.commands.custom;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.StringHelper;
import ti4.message.MessageHelper;

class ShuffleBackInUnrevealedObj extends GameStateSubcommand {

    ShuffleBackInUnrevealedObj() {
        super(
                Constants.SHUFFLE_BACK_IN_UNREVEALED_OBJ,
                "Shuffle an unrevealed objective back in and redraw one in its place",
                true,
                true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.LOCATION1, "Location of the unrevealed objective")
                .setRequiredRange(1, 30)
                .setRequired(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.STAGE_1_OR_2, "Stage 1 or 2")
                .setRequiredRange(1, 2)
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        int location = event.getOption(Constants.LOCATION1).getAsInt();
        int stage = event.getOption(Constants.STAGE_1_OR_2).getAsInt();
        if (stage != 1 && stage != 2) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Stage must be 1 or 2.");
            return;
        }
        List<String> unrevealed =
                stage == 1 ? game.getPublicObjectives1Peekable() : game.getPublicObjectives2Peekable();
        if (location < 1 || location > unrevealed.size()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "There " + (unrevealed.size() == 1 ? "is" : "are") + " only "
                            + StringHelper.pluralize(unrevealed.size(), "unrevealed stage " + stage + " objective")
                            + "; location must be between 1 and " + unrevealed.size() + ".");
            return;
        }
        List<String> deck = stage == 1 ? game.getPublicObjectives1() : game.getPublicObjectives2();
        if (deck.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "The stage " + stage + " objective deck is empty; nothing to draw.");
            return;
        }
        String id = game.getTopPublicObjective(stage);
        game.swapPublicObjectiveOut(stage, location - 1, id);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Shuffle objective at position " + location + " back into the deck and drew a new one there.");
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
