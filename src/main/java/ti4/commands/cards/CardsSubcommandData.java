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
    
    private SlashCommandInteractionEvent event;
    private Map activeMap;
    private User user;
    private boolean replyHasBeenEdited;

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

    /**
     * Edits the original message after submitting a slash command
     * @param messageText new message
     */
    public void sendMessage(String messageText) {
        if (this.replyHasBeenEdited) {
            MessageHelper.sendMessageToChannel(this.event.getChannel(), messageText);
        } else if (messageText.length() >= 2000) {
            this.event.getHook().editOriginal("_ _").queue();
            MessageHelper.sendMessageToChannel(this.event.getChannel(), messageText);
            this.replyHasBeenEdited = true;
        } else {
            this.event.getHook().editOriginal(messageText).queue();
            this.replyHasBeenEdited = true;
        }
    }

    abstract public void execute(SlashCommandInteractionEvent event);

    public void preExecute(SlashCommandInteractionEvent event) {
        this.event = event;
        replyHasBeenEdited = false;
        user = event.getUser();
        activeMap = MapManager.getInstance().getUserActiveMap(user.getId());
        Helper.checkThreadLimitAndArchive(event.getGuild());

        Player player = Helper.getGamePlayer(activeMap, null, event, user.getId());
        if (player != null) {
            user = MapGenerator.jda.getUserById(player.getUserID());
        }
    }
}
