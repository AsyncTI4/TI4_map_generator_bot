package ti4.discord.interactions.commands.cardspn;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.PromissoryNoteHelper;

class SendRandomPN extends GameStateSubcommand {

    public SendRandomPN() {
        super("send_random", "Send a random Promissory Note to a player", true, true);
        addOptions(CommonOptions.targetFactionOrColor());
        addOptions(CommonOptions.factionColor());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player otherPlayer = CommandHelper.getOtherPlayerOrReply(game, event);
        if (otherPlayer == null) return;
        PromissoryNoteHelper.sendRandom(event, game, getPlayer(), otherPlayer);
    }
}
