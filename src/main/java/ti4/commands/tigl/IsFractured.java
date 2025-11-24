package ti4.commands.tigl;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class IsFractured extends GameStateSubcommand {

    public IsFractured() {
        super(Constants.IS_FRACTURED, "Mark a TIGL game as Fractured", true, false);
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.IS_FRACTURED, "True to mark as TIGL Fractured")
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!game.isCompetitiveTIGLGame()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Only TIGL games can be marked as Fractured.");
            return;
        }

        boolean isFractured = event.getOption(Constants.IS_FRACTURED, false, OptionMapping::getAsBoolean);

        boolean changed;
        String response;
        if (isFractured) {
            changed = game.addTag(Constants.TIGL_FRACTURED_TAG);
            response =
                    changed ? "Marked this game as TIGL Fractured." : "This game is already marked as TIGL Fractured.";
        } else {
            changed = game.removeTag(Constants.TIGL_FRACTURED_TAG);
            response = changed
                    ? "Removed the TIGL Fractured tag from this game."
                    : "This game was not marked as TIGL Fractured.";
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), response);
    }
}
