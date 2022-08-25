package ti4.commands.cards;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import ti4.MapGenerator;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public abstract class CardsSubcommandData extends SubcommandData {

    private Map activeMap;
    private User user;

    public String getActionID() {
        return getName();
    }

    public CardsSubcommandData(@NotNull String name, @NotNull String description) {
        super(name, description);
    }

    public Map getActiveMap() {
        return activeMap;
    }

    public User getUser() {
        return user;
    }

    abstract public void execute(SlashCommandInteractionEvent event);

    public void preExecute(SlashCommandInteractionEvent event) {
        user = event.getUser();
        activeMap = MapManager.getInstance().getUserActiveMap(user.getId());

        Player player = Helper.getGamePlayer(activeMap, null, event, user.getId());
        if (player != null) {
            user = MapGenerator.jda.getUserById(player.getUserID());
        }
    }

    public void reply(SlashCommandInteractionEvent event) {
        MessageHelper.replyToMessage(event, "Card action executed: " + (event != null ? event.getName() : ""));
    }

}
