package ti4.commands.breakthrough;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;

class BreakthroughSetTg extends GameStateSubcommand {

    BreakthroughSetTg() {
        super(Constants.BREAKTHROUGH_SET_TG, "Add or remove trade goods on your breakthrough", true, true);
        addOption(OptionType.STRING, Constants.TG, "Trade goods count - can use +1/-1 etc. to add/subtract", true);
        addOption(OptionType.STRING, Constants.BREAKTHROUGH, "Which breakthrough to set tgs", false, true);
        addOption(OptionType.STRING, Constants.FACTION_COLOR, "Faction to modify their breakthrough TGs", false, true);
    }

    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping optionTG = event.getOption(Constants.TG);
        if (optionTG == null) return;

        String id = BreakthroughCommandHelper.getBreakthroughsFromEvent(event, getPlayer(), true).stream()
                .findFirst()
                .orElse(null);
        BreakthroughCommandHelper.updateBreakthroughTradeGoods(getPlayer(), optionTG.getAsString(), id);
    }
}
