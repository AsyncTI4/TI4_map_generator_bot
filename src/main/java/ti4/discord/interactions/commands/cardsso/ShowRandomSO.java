package ti4.discord.interactions.commands.cardsso;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.SecretObjectiveHelper;
import ti4.message.MessageHelper;

class ShowRandomSO extends GameStateSubcommand {

    ShowRandomSO() {
        super("show_random", "Show a Secret Objective to a player", true, true);
        addOptions(CommonOptions.targetFactionOrColor());
        addOptions(CommonOptions.factionColor("Source faction or color (default is you)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();

        List<String> secrets = new ArrayList<>(player.getSecrets().keySet());
        if (secrets.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No secret objectives to reveal");
            return;
        }

        Player otherPlayer = CommandHelper.getOtherPlayerOrReply(game, event);
        if (otherPlayer == null) return;

        SecretObjectiveHelper.resolveShowRandomSecretObjective(game, player, otherPlayer, event);
    }
}
