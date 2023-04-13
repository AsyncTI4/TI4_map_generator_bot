package ti4.commands.franken;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import org.jetbrains.annotations.NotNull;

import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.message.MessageHelper;

public abstract class FrankenSubcommandData extends SubcommandData {

    private SlashCommandInteractionEvent event;
    private Map activeMap;
    private User user;

    public String getActionID() {
        return getName();
    }

    public FrankenSubcommandData(@NotNull String name, @NotNull String description) {
        super(name, description);
    }

    public Map getActiveMap() {
        return activeMap;
    }

    public User getUser() {
        return user;
    }
    
    public SlashCommandInteractionEvent getEvent() {
        return this.event;
    }

    /**
     * Send a message to the event's channel, handles large text
     * @param messageText new message
     */
    public void sendMessage(String messageText) {
        MessageHelper.replyToSlashCommand(event, messageText);
    }

    abstract public void execute(SlashCommandInteractionEvent event);

    public void preExecute(SlashCommandInteractionEvent event) {
        this.event = event;
        user = event.getUser();
        activeMap = MapManager.getInstance().getUserActiveMap(user.getId());
    }

    public void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
        MapSaveLoadManager.saveMap(activeMap);
    }
}
