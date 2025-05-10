package ti4.commands.franken;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.service.franken.FrankenPromissoryService;

class PNAdd extends PNAddRemove {

    public PNAdd() {
        super(Constants.PN_ADD, "Add a Promissory Note to your faction's owned notes");
    }

    @Override
    public void doAction(Player player, List<String> pnIDs, SlashCommandInteractionEvent event) {
        FrankenPromissoryService.addPromissoryNotes(event, getGame(), player, pnIDs);
    }
}
