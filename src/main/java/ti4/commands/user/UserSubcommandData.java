package ti4.commands.user;

import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

public abstract class UserSubcommandData extends SubcommandData {

    private User user;
    private UserSettings userSettings;

    public UserSubcommandData(@NotNull String name, @NotNull String description) {
        super(name, description);
    }

    public User getUser() {
        return user;
    }

    public UserSettings getUserSettings() {
        return userSettings;
    }

    public void preExecute(SlashCommandInteractionEvent event) {
        user = event.getUser();
        userSettings = UserSettingsManager.getInstance().getUserSettings(event.getUser().getId());
    }

    abstract public void execute(SlashCommandInteractionEvent event);

    public void postExecute(SlashCommandInteractionEvent event) {
        UserSettingsManager.getInstance().saveUserSetting(userSettings);
    }
}
