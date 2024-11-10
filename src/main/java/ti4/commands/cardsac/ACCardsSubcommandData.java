package ti4.commands.cardsac;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;

public abstract class ACCardsSubcommandData extends SubcommandData {

    private Game game;
    private User user;

    public String getActionID() {
        return getName();
    }

    public ACCardsSubcommandData(@NotNull String name, @NotNull String description) {
        super(name, description);
    }

    public Game getActiveGame() {
        return game;
    }

    public User getUser() {
        return user;
    }

    abstract public void execute(SlashCommandInteractionEvent event);

    public void preExecute(SlashCommandInteractionEvent event) {
        user = event.getUser();
        game = UserGameContextManager.getContextGame(user.getId());
        Helper.checkThreadLimitAndArchive(event.getGuild());

        Player player = Helper.getGamePlayer(game, null, event, user.getId());
        if (player != null) {
            user = AsyncTI4DiscordBot.jda.getUserById(player.getUserID());
        }
    }
}
