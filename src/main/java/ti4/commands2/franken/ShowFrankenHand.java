package ti4.commands2.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.franken.FrankenDraftBagService;

class ShowFrankenHand extends GameStateSubcommand {

    public ShowFrankenHand() {
        super(Constants.SHOW_HAND, "Shows your current hand of drafted cards", false, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        FrankenDraftBagService.displayPlayerHand(getGame(), getPlayer());
    }
}
