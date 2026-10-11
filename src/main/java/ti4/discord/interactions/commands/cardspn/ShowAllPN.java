package ti4.discord.interactions.commands.cardspn;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;

class ShowAllPN extends GameStateSubcommand {

    public ShowAllPN() {
        super(Constants.SHOW_ALL, "Show Promissory Note to player", true, true);
        addOptions(CommonOptions.targetFactionOrColor());
        addOptions(CommonOptions.factionColor("Source faction or color (default is you)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player targetPlayer = CommandHelper.getOtherPlayerOrReply(game, event);
        if (targetPlayer == null) return;

        PromissoryNoteHelper.showAll(getPlayer(), targetPlayer, game);
    }
}
