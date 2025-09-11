package ti4.commands.breakthrough;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;

public class BreakthroughSetTg extends GameStateSubcommand {
    public BreakthroughSetTg() {
        super(Constants.BREAKTHROUGH_SET_TG, "Add or remove trade goods on your breakthrough", true, true);
        addOption(OptionType.STRING, Constants.TG, "Trade goods count - can use +1/-1 etc. to add/subtract", true);
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping optionTG = event.getOption(Constants.TG);
        if (optionTG == null) return;
        BreakthroughCommandHelper.updateBreakthroughTradeGoods(getPlayer(), optionTG.getAsString());
    }
}
