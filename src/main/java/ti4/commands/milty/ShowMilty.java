package ti4.commands.milty;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.map.Game;

public class ShowMilty extends MiltySubcommandData {

    private static final String show = "show";

    public ShowMilty() {
        super(show, "Show the current draft status and refresh the buttons");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        MiltyDraftManager manager = game.getMiltyDraftManager();
        manager.repostDraftInformation(game);
    }
}
