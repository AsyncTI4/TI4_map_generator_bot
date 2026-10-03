package ti4.discord.interactions.commands.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.map.FractureService;

/**
 * Recovery path for Ingress placement. In fog the GM places every token by hand from one-shot buttons, so if those
 * are lost there is otherwise no way to get them back once the region is on the board.
 */
public class PlaceIngressTokens extends GameStateSubcommand {

    public PlaceIngressTokens() {
        super(Constants.INGRESS_TOKENS, "(Re)run Ingress token placement for The Fracture", true, true);
        addOption(
                OptionType.STRING,
                Constants.FACTION_COLOR,
                "Player whose breakthrough synergy drives the placement",
                true,
                true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (game.isFowMode() && !getPlayer().isGM()) {
            MessageHelper.replyToMessage(event, "You are not authorized to use this command.");
            return;
        }

        if (!FractureService.isFractureInPlay(game)) {
            MessageHelper.sendMessageToEventChannel(
                    event, "The Fracture is not on the board - use `/map fracture` to bring it into play.");
            return;
        }

        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        FractureService.spawnIngressTokens(event, game, player, player.getBreakthroughID());
    }
}
