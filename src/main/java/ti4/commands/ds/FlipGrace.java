package ti4.commands.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class FlipGrace extends DiscordantStarsSubcommandData {

    public FlipGrace() {
        super(Constants.FLIP_GRACE, "Flip Grace (Edyn Faction Ability) to show it has been used");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayerFromEvent(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        if (!player.hasAbility("grace")) {
            MessageHelper.sendMessageToEventChannel(event, "Player does not have Grace (Edyn Faction Ability)");
            return;
        }

        if (player.removeExhaustedAbility("grace")) {
            MessageHelper.sendMessageToEventChannel(event, "Grace (Edyn Faction Ability) was exhausted. Flipping it back to ready.");
            return;
        }

        player.addExhaustedAbility("grace");
        MessageHelper.sendMessageToEventChannel(event, "Grace (Edyn Faction Ability) exhausted");
    }

}
