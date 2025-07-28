package ti4.stubs;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import ti4.map.Game;

public class TestGame extends Game {
    private final TextChannel mainGameChannel;

    public TestGame(TextChannel mainGameChannel) {
        this.mainGameChannel = mainGameChannel;
    }

    @Override
    public TextChannel getMainGameChannel() {
        return mainGameChannel;
    }
}
