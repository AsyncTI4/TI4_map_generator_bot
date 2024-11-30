package ti4.commands2.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class PlayAC extends GameStateSubcommand {

    public PlayAC() {
        super(Constants.PLAY_AC, "Play an Action Card", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.ACTION_CARD_ID, "Action Card ID that is sent between () or Name/Part of Name").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String acId = event.getOption(Constants.ACTION_CARD_ID).getAsString().toLowerCase();
        String reply = ActionCardHelper.playAC(event, getGame(), getPlayer(), acId, event.getChannel());
        if (reply != null) {
            MessageHelper.sendMessageToEventChannel(event, reply);
        }
    }
}
