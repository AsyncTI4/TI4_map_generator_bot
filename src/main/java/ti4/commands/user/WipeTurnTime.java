package ti4.commands.user;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.map.persistence.ManagedPlayer;
import ti4.message.MessageHelper;
import ti4.service.event.EventAuditService;

class WipeTurnTime extends GameStateSubcommand {

    public WipeTurnTime() {
        super(Constants.WIPE_TURN_TIME, "Wipe your turn time in all games", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User userP = event.getUser();
        List<User> users = new ArrayList<>();
        users.add(userP);
        List<ManagedGame> userGames = users.stream()
            .map(user -> GameManager.getManagedPlayer(user.getId()))
            .filter(Objects::nonNull)
            .map(ManagedPlayer::getGames)
            .flatMap(Collection::stream)
            .distinct()
            .toList();

        Map<String, Map.Entry<Integer, Long>> playerTurnTimes = new HashMap<>();
        for (ManagedGame game : userGames) {
            if (game.getGame().isFowMode()) {
                continue;
            }
            Game g = game.getGame();
            Player player = g.getPlayer(userP.getId());
            if (player != null) {
                player.setTotalTurnTime(0);
                player.setNumberOfTurns(0);
                GameManager.save(g, EventAuditService.getReason(event, g.isFowMode()));
            }
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), "Wiped all of your turn times");
    }
}
