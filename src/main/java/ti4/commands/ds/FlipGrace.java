package ti4.commands.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;

public class FlipGrace extends DiscordantStarsSubcommandData {

    public FlipGrace() {
        super(Constants.FLIP_GRACE, "Flip Grace (Edyn Faction Ability) to show it has been used");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        
        if (!player.hasAbility("grace")) {
            sendMessage("Player does not have Grace (Edyn Faction Ability)");
            return;
        }

        if (player.removeExhaustedAbility("grace")) {
            sendMessage("Grace (Edyn Faction Ability) was exhausted. Flipping it back to ready.");
            return;
        }

        player.addExhaustedAbility("grace");
        sendMessage("Grace (Edyn Faction Ability) exhausted");
    }
    
}
