package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;

public class Ping extends GameSubcommandData {

    public Ping() {
        super(Constants.PING, "Ping all players in the game");
    }

    public void execute(SlashCommandInteractionEvent event) {
        Helper.fixGameChannelPermissions(event.getGuild(),getActiveMap());
        event.getHook().editOriginal(Helper.getGamePing(event.getGuild(), getActiveMap())).queue();
    }
}