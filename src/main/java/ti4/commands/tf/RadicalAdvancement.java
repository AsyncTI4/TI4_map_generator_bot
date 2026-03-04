package ti4.commands.tf;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.helpers.Constants;
import ti4.map.Player;

class RadicalAdvancement extends GameStateSubcommand {

    RadicalAdvancement() {
        super(
                Constants.RADICAL_ADVANCEMENT,
                "Do the Radical Advancement ability (return a technology then gain one with one extra prerequisite)",
                true,
                true);
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                        .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        ButtonHelperTwilightsFall.startRadicalAdvancement(getGame(), player, event);
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
