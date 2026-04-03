package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class Leave extends GameStateSubcommand {

    Leave() {
        super(Constants.LEAVE, "Leave map as player", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game name").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        var player = getPlayer();
        if (player.isRealPlayer()) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    "You are a real player, and thus should not do `/game leave`."
                            + " You should do `/game replace` or `/player stats npc:True` to set yourself as an NPC, depending on what you are looking for. Note that NPC is not allowed in TIGL games.");
            return;
        }

        game.removePlayer(player.getUserID());

        MessageHelper.replyToMessage(event, "Left map: " + game.getName() + " successful");
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return !getPlayer().isSpectator();
    }
}
