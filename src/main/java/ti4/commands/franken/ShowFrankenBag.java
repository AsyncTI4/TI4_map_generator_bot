package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.franken.FrankenDraftBagService;

class ShowFrankenBag extends GameStateSubcommand {

    public ShowFrankenBag() {
        super(Constants.SHOW_BAG, "Shows your current FrankenDraft bag of cards left to draft.", false, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        FrankenDraftBagService.showPlayerBag(getGame(), getPlayer());
    }
}
