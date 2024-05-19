package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class SoAddToGame extends CustomSubcommandData {
    public SoAddToGame() {
        super(Constants.ADD_SO_TO_GAME, "Add SO to game");
        addOptions(new OptionData(OptionType.STRING, Constants.SO_ID, "Secret ID").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        String soID = event.getOption(Constants.SO_ID, null, OptionMapping::getAsString);
        if (soID == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify SO");
            return;
        }
        if (!Mapper.getSecretObjectives().containsKey(soID)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid SO ID");
            return;
        }

        game.addSOToGame(soID);
        if (game.getSecretObjectives().contains(soID)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "SO added to game deck");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "SO was not added for an unknown reason");
        }
    }
}
