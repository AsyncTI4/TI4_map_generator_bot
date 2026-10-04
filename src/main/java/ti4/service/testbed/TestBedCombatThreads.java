package ti4.service.testbed;

import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class TestBedCombatThreads {

    static boolean isCombatThread(Game game, String threadName) {
        return threadName.startsWith(game.getName() + "-round-") && threadName.contains("-vs-");
    }

    static boolean isCombatThread(Game game, ThreadChannel thread) {
        return isCombatThread(game, thread.getName());
    }

    @Nullable
    static ThreadChannel latest(Game game, Player seat) {
        TextChannel parent = game.isFowMode() ? seat.getPrivateChannel() : game.getMainGameChannel();
        if (parent == null) return null;
        List<ThreadChannel> threads = parent.getThreadChannels().stream()
                .filter(thread -> isCombatThread(game, thread))
                .filter(thread -> game.isFowMode() || thread.getName().contains(seat.getFaction()))
                .toList();
        return threads.stream()
                .max(Comparator.comparingLong(ThreadChannel::getLatestMessageIdLong))
                .orElse(null);
    }
}
