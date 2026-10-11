package ti4.discord.interactions.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;

class AddPlayer extends GameStateSubcommand {

    public AddPlayer() {
        super(Constants.ADD, "Add player to game", true, true);
        addOptions(CommonOptions.playerOptions(true, i -> "Player @playerName"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        addExtraUser(event, game, Constants.PLAYER1);
        addExtraUser(event, game, Constants.PLAYER2);
        addExtraUser(event, game, Constants.PLAYER3);
        addExtraUser(event, game, Constants.PLAYER4);
        addExtraUser(event, game, Constants.PLAYER5);
        addExtraUser(event, game, Constants.PLAYER6);
        addExtraUser(event, game, Constants.PLAYER7);
        addExtraUser(event, game, Constants.PLAYER8);

        Helper.fixGameChannelPermissions(event.getGuild(), game);
    }

    private static void addExtraUser(SlashCommandInteractionEvent event, Game game, String playerID) {
        OptionMapping option = event.getOption(playerID);
        if (option == null) {
            return;
        }
        User extraUser = option.getAsUser();
        if (game.getPlayerIDs().contains(extraUser.getId())) {
            MessageHelper.replyToMessage(event, extraUser.getName() + " is already a player in the game.");
            return;
        }
        game.addPlayer(extraUser.getId(), extraUser.getName());
        MessageHelper.sendMessageToEventChannel(
                event, extraUser.getName() + " added to game: " + game.getName() + " - successful");
    }
}
