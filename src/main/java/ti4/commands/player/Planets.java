package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;

public class Planets extends PlayerSubcommandData{
    public Planets() {
        super(Constants.PLANETS, "Player Planets info");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        sendMessage("Player Planets info received");
    }
}
