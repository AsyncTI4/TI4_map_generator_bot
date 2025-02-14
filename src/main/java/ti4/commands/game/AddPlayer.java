package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.message.MessageHelper;

class AddPlayer extends GameStateSubcommand {

    public AddPlayer() {
        super(Constants.ADD, "Add player to game", true, true);
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Player @playerName").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Player @playerName"));
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

    private void addExtraUser(SlashCommandInteractionEvent event, Game game, String playerID) {
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
