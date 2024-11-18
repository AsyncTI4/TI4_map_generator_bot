package ti4.commands2.milty;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.map.Game;

public class ShowMilty extends GameStateSubcommand {

    private static final String show = "show";

    public ShowMilty() {
        super(show, "Show the current draft status and refresh the buttons", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        MiltyDraftManager manager = game.getMiltyDraftManager();
        manager.repostDraftInformation(game);
    }
}
