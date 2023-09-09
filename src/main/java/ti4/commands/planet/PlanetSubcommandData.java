package ti4.commands.planet;

import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

public abstract class PlanetSubcommandData extends SubcommandData {
    
    private SlashCommandInteractionEvent event;
    private Game activeGame;
    private User user;

    public String getActionID() {
        return getName();
    }

    public PlanetSubcommandData(@NotNull String name, @NotNull String description) {
        super(name, description);
    }

    public Game getActiveMap() {
        return activeGame;
    }

    public User getUser() {
        return user;
    }

    /**
     * Send a message to the event's channel, handles large text
     * @param messageText new message
     */
    public void sendMessage(String messageText) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), messageText);
    }

    abstract public void execute(SlashCommandInteractionEvent event);

    public void preExecute(SlashCommandInteractionEvent event) {
        this.event = event;
        user = event.getUser();
        activeGame = GameManager.getInstance().getUserActiveGame(user.getId());
    }

    public void reply(SlashCommandInteractionEvent event) {
        PlanetCommand.reply(event);
    }
}
