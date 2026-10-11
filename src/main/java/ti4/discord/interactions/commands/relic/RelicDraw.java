package ti4.discord.interactions.commands.relic;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.RelicHelper;

class RelicDraw extends GameStateSubcommand {

    RelicDraw() {
        super(Constants.RELIC_DRAW, "Draw a relic", true, true);
        addOptions(CommonOptions.factionColor());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        RelicHelper.drawRelicAndNotify(getPlayer(), event, getGame());
    }
}
