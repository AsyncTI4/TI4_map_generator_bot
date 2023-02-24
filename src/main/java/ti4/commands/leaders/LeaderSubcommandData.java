package ti4.commands.leaders;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.message.BotLogger;

public abstract class LeaderSubcommandData extends SubcommandData {

    private Map activeMap;
    private User user;
    
    public String getActionID() {
        return getName();
    }
    
    public LeaderSubcommandData(@NotNull String name, @NotNull String description) {
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
     * @param event
     * @param messageText new message - must be under 1500 chars (for now)
     */
    public void editReplyMessage(SlashCommandInteractionEvent event, String messageText) {
        event.getHook().editOriginal(messageText).queue();
    }

    abstract public void execute(SlashCommandInteractionEvent event);

    public void preExecute(SlashCommandInteractionEvent event) {
        user = event.getUser();
        activeMap = MapManager.getInstance().getUserActiveMap(user.getId());
    }

    public void reply(SlashCommandInteractionEvent event) {
        LeaderCommand.reply(event);
    }
}
