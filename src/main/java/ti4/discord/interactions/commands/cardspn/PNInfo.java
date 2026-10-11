package ti4.discord.interactions.commands.cardspn;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.message.MessageHelper;

class PNInfo extends GameStateSubcommand {

    public PNInfo() {
        super(Constants.INFO, "Send details on your promissory notes to your #cards-info thread", false, true);
        addOptions(CommonOptions.factionColor("Faction or Color who to send info for"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        PromissoryNoteHelper.sendPromissoryNoteInfo(getGame(), getPlayer(), true, event);
        MessageHelper.sendMessageToEventChannel(event, "Promissory note info sent.");
    }
}
