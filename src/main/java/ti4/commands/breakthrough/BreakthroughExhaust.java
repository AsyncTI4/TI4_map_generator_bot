package ti4.commands.breakthrough;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;

public class BreakthroughExhaust extends GameStateSubcommand {
    public BreakthroughExhaust() {
        super(Constants.BREAKTHROUGH_EXHAUST, "Exhaust breakthrough", true, true);
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        BreakthroughCommandHelper.exhaustBreakthrough(event, getPlayer());
    }
}
