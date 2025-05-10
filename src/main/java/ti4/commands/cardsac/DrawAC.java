package ti4.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;

class DrawAC extends GameStateSubcommand {

    public DrawAC() {
        super(Constants.DRAW_AC, "Draw Action Card", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw, default 1, max 10"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        count = Math.max(count, 1);
        count = Math.min(count, 10);
        ActionCardHelper.drawActionCards(getGame(), getPlayer(), count, false);
    }
}
