package ti4.discord.interactions.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;

class SendACRandom extends GameStateSubcommand {

    public SendACRandom() {
        super(Constants.SEND_AC_RANDOM, "Send a random Action Card to a player", true, true);
        addOptions(CommonOptions.targetFactionOrColor());
        addOptions(CommonOptions.factionColor());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player otherPlayer = CommandHelper.getOtherPlayerOrReply(game, event);
        if (otherPlayer == null) return;
        ActionCardHelper.sendRandomACPart2(event, game, getPlayer(), otherPlayer);
    }
}
