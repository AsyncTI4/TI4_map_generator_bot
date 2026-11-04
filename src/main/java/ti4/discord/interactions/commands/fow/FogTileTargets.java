package ti4.discord.interactions.commands.fow;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;

final class FogTileTargets {

    private FogTileTargets() {}

    static boolean refusedForOtherSeats(
            SlashCommandInteractionEvent event, Game game, Player self, List<Player> targets) {
        boolean targetsSomeoneElse = targets.stream().anyMatch(target -> target != self);
        if (!targetsSomeoneElse || FoWHelper.isGameMaster(event.getUser().getId(), game)) {
            return false;
        }
        MessageHelper.replyToMessage(event, "Only a GM can change another player's fog tiles.");
        return true;
    }
}
