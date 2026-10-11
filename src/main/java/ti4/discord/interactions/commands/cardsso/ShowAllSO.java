package ti4.discord.interactions.commands.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.SecretObjectiveHelper;

class ShowAllSO extends GameStateSubcommand {

    public ShowAllSO() {
        super(Constants.SHOW_ALL, "Show all Secret Objectives to one player", true, true);
        addOptions(CommonOptions.targetFactionOrColor());
        addOptions(CommonOptions.factionColor("Source faction or color (default is you)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player targetPlayer = CommandHelper.getOtherPlayerOrReply(game, event);
        if (targetPlayer == null) return;
        SecretObjectiveHelper.showAll(getPlayer(), targetPlayer, game);
    }
}
