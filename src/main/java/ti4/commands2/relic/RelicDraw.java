package ti4.commands2.relic;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.RelicHelper;

class RelicDraw extends GameStateSubcommand {

    public RelicDraw() {
        super(Constants.RELIC_DRAW, "Draw a relic", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        RelicHelper.drawRelicAndNotify(getPlayer(), event, getGame());
    }
}
