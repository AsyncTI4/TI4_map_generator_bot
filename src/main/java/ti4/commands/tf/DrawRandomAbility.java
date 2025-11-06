package ti4.commands.tf;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.helpers.Constants;
import ti4.map.Player;

class DrawRandomAbility extends GameStateSubcommand {

    public DrawRandomAbility() {
        super(Constants.DRAW_RANDOM_ABILITY, "Draw a random TF Ability", true, true);
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                        .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        ButtonHelperTwilightsFall.drawSingularNewSpliceCard(getGame(), "spoof_ability", player, event);
    }
}
