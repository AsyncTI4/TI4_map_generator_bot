package ti4.discord.interactions.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;

class ShowAllAC extends GameStateSubcommand {

    ShowAllAC() {
        super(Constants.SHOW_ALL, "Show all Action Cards to one player", true, true);
        addOptions(CommonOptions.targetFactionOrColor("Faction or Color being shown cards"));
        addOptions(CommonOptions.factionColor("Faction or Color showing cards"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player factionToShowTo = CommandHelper.getOtherPlayerOrReply(game, event);
        if (factionToShowTo == null) return;
        Player player = getPlayer();
        ActionCardHelper.showAll(player, factionToShowTo, game);
    }
}
