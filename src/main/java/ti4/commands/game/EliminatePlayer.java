package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class EliminatePlayer extends GameStateSubcommand {

    public EliminatePlayer() {
        super(Constants.ELIMINATE, "Eliminate player from game", true, true);
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Player @playerName").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        StringBuilder stringBuilder = new StringBuilder();

        OptionMapping option = event.getOption(Constants.PLAYER1);
        if (option == null) {
            return;
        }
        User extraUser = option.getAsUser();
        Player player = game.getPlayer(extraUser.getId());
        ButtonHelper.removeUser(event, game, player, stringBuilder);

        Helper.fixGameChannelPermissions(event.getGuild(), game);

        MessageHelper.replyToMessage(event, stringBuilder.toString());
    }
}
