package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class Join extends GameStateSubcommand {

    public Join() {
        super(Constants.JOIN, "Join map as player", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game name").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        User user = event.getUser();
        game.addPlayer(user.getId(), user.getName());

        MessageHelper.replyToMessage(event, getResponseMessage(game));
    }

    private String getResponseMessage(Game game) {
        return "Joined map: " + game.getName() + " successful";
    }
}
