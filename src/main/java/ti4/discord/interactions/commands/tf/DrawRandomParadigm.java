package ti4.discord.interactions.commands.tf;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperTwilightsFall;

class DrawRandomParadigm extends GameStateSubcommand {

    DrawRandomParadigm() {
        super("draw_random_paradigm", "Draw a random Twilight's Fall Paradigm", true, true);
        markSuspicious();
        addOptions(CommonOptions.factionColor("Faction or Color for which you draw"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        ButtonHelperTwilightsFall.drawParadigm(game, player, null, false);
    }
}
