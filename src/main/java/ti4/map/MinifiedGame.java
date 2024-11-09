package ti4.map;

import lombok.Getter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

@Getter
public class MinifiedGame {

    private final String name;
    private final boolean hasEnded;
    private final long lastModifiedDate;
    private final String creationDate;
    private final long lastActivePlayerChange;
    private final long endedDate;
    // TODO: Better to have just ids?
    private final Guild guild;
    private final TextChannel mainGameChannel;
    private final TextChannel actionsChannel;
    private final TextChannel tableTalkChannel;
    private final String ping;

    public MinifiedGame(Game game) {
        name = game.getName();
        hasEnded = game.isHasEnded();
        lastModifiedDate = game.getLastModifiedDate();
        creationDate = game.getCreationDate();
        lastActivePlayerChange = game.getLastActivePlayerChange() == null ? 0 : game.getLastActivePlayerChange().getTime();
        endedDate = game.getEndedDate();
        guild = game.getGuild();
        mainGameChannel = game.getMainGameChannel();
        actionsChannel = game.getActionsChannel();
        tableTalkChannel = game.getTableTalkChannel();
        ping = game.getPing();
    }

    public boolean matches(Game game) {
        return name.equals(game.getName()) && lastModifiedDate == game.getLastModifiedDate();
    }
}
