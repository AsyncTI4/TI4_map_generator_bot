package ti4.commands.context;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import ti4.service.async.BanCleanupService;

public class BanUser extends UserCommand {

    public BanUser() {
        super("Ban", Permission.BAN_MEMBERS);
    }

    private boolean success = false;

    public void execute(UserContextInteractionEvent event) {
        User victim = event.getTarget();
        success = BanCleanupService.banSpamAccount(event, victim, event.getUser());
    }

    public void postExecute(UserContextInteractionEvent event) {
        User victim = event.getTarget();
        String msg = "Successfully banned " + victim.getEffectiveName() + " from all Async servers.";
        if (!success) {
            msg = "Failed to ban " + victim.getEffectiveName() + ". Check the warning/error logs";
            msg += " for more details. This most likely happens if the user has ever participated";
            msg += " in a game, in which case an Admin will need to take care of the ban.";
        }
        event.reply(msg);
    }
}
