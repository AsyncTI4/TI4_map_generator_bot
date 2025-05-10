package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.image.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class SoAddToGame extends GameStateSubcommand {

    public SoAddToGame() {
        super(Constants.ADD_SO_TO_GAME, "Add a secret objective to the game", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.SO_ID, "Secret objective ID").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String soID = event.getOption(Constants.SO_ID).getAsString();
        if (!Mapper.getSecretObjectives().containsKey(soID)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid secret objective ID.");
            return;
        }

        game.addSOToGame(soID);
        if (game.getSecretObjectives().contains(soID)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Secret objective added to game deck.");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Secret objective was not added for an unknown reason.");
        }
    }
}
