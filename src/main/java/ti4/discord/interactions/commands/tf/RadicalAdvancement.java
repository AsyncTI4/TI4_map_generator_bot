package ti4.discord.interactions.commands.tf;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Player;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.helpers.Constants;

class RadicalAdvancement extends GameStateSubcommand {

    RadicalAdvancement() {
        super(
                Constants.RADICAL_ADVANCEMENT,
                "Do the Radical Advancement ability (return a technology then gain one with one extra prerequisite)",
                true,
                true);
        markSuspicious();
        addOptions(CommonOptions.factionColorForStats());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        ButtonHelperTwilightsFall.startRadicalAdvancement(getGame(), player, event);
    }
}
