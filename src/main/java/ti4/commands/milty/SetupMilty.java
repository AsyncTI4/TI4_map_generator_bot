package ti4.commands.milty;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.service.milty.MiltyService;

class SetupMilty extends GameStateSubcommand {

    private static final String setup = "setup";

    public SetupMilty() {
        super(setup, "Setup Milty Draft", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MiltyService.miltySetup(event, getGame());
    }
}
