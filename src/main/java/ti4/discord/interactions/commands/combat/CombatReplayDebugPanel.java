package ti4.discord.interactions.commands.combat;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.contest.replay.buttons.CombatReplayDebugButtonHandler;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.message.MessageHelper;

class CombatReplayDebugPanel extends GameStateSubcommand {

    CombatReplayDebugPanel() {
        super("replay_debug", "Show combat replay debug controls.", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), "Combat replay debug controls", CombatReplayDebugButtonHandler.buttons());
    }
}
