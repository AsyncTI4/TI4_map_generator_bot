package ti4.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;

class PickACFromDiscard extends GameStateSubcommand {

    public PickACFromDiscard() {
        super(Constants.PICK_AC_FROM_DISCARD, "Pick an Action Card from discard pile into your hand", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action Card ID, which is found between ()").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int acIndex = event.getOption(Constants.ACTION_CARD_ID).getAsInt();
        ActionCardHelper.getActionCardFromDiscard(event, getGame(), getPlayer(), acIndex);
    }
}
