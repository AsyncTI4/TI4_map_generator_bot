package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.FrankenDraftHelper;

public class ShowFrankenBag extends GameStateSubcommand {

    public ShowFrankenBag() {
        super(Constants.SHOW_BAG, "Shows your current FrankenDraft bag of cards left to draft.", false, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        FrankenDraftHelper.showPlayerBag(getGame(), getPlayer());
    }
}
