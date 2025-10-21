package ti4.commands.cardspn;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.message.MessageHelper;

class PNInfo extends GameStateSubcommand {

    public PNInfo() {
        super(Constants.INFO, "Send details on your promissory notes to your #cards-info thread", false, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color who to send info for")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        PromissoryNoteHelper.sendPromissoryNoteInfo(getGame(), getPlayer(), true, event);
        MessageHelper.sendMessageToEventChannel(event, "Promissory note info sent.");
    }
}
