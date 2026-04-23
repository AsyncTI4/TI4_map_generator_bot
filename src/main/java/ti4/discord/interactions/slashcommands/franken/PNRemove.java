package ti4.discord.interactions.slashcommands.franken;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.franken.FrankenPromissoryService;

class PNRemove extends PNAddRemove {

    public PNRemove() {
        super(Constants.PN_REMOVE, "Remove an Promissory Note from your faction's owned notes");
    }

    @Override
    public void doAction(Player player, List<String> pnIDs, SlashCommandInteractionEvent event) {
        FrankenPromissoryService.removePromissoryNotes(event, player, pnIDs);
    }
}
