package ti4.stubs;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import ti4.map.Game;
import ti4.map.Player;

public class TestPlayer extends Player {
    private final TextChannel correctChannel;

    public TestPlayer(String userId, String userName, Game game, TextChannel correctChannel) {
        super(userId, userName, game);
        this.correctChannel = correctChannel;
    }

    @Override
    public TextChannel getCorrectChannel() {
        return correctChannel;
    }
}