package ti4.commands.player;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

public abstract class PlayerSubcommandData extends SubcommandData {

    private SlashCommandInteractionEvent event;
    private Game activeGame;
    private User user;

    public String getActionID() {
        return getName();
    }

    public PlayerSubcommandData(@NotNull String name, @NotNull String description) {
        super(name, description);
    }

    public Game getActiveGame() {
        return activeGame;
    }

    public User getUser() {
        return user;
    }

    public SlashCommandInteractionEvent getEvent() {
        return event;
    }

    /**
     * Send a message to the event's channel, handles large text
     * @param messageText new message
     */
    public void sendMessage(String messageText) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), messageText);
    }
    public void sendMessage(String messageText, GenericInteractionCreateEvent event2) {
        MessageHelper.sendMessageToChannel(event2.getMessageChannel(), messageText);
    }

    abstract public void execute(SlashCommandInteractionEvent event);

    public void preExecute(SlashCommandInteractionEvent event) {
        this.event = event;
        user = event.getUser();
        activeGame = GameManager.getInstance().getUserActiveGame(user.getId());
    }

    public void reply(SlashCommandInteractionEvent event) {
        PlayerCommand.reply(event);
    }
}
