package ti4.discord.interactions.commands.ll;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.kairn.KairnBreakthroughHandler;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.message.MessageHelper;

class RelicTradingHub extends GameStateSubcommand {
    RelicTradingHub() {
        super("relic_trading_hub", "Refresh Relic Trading Hub relics", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!getPlayer().hasUnlockedBreakthrough("kairnbt")) {
            MessageHelper.sendMessageToEventChannel(event, "You do not have the Relic Trading Hub breakthrough.");
            return;
        }
        KairnBreakthroughHandler.refreshRelics(getGame(), getPlayer());
    }
}
