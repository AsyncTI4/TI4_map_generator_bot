package ti4.discord.interactions.commands.testbed;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.testbed.TestBedPanelService;
import ti4.service.testbed.TestBedService;

class TestBedPanel extends GameStateSubcommand {

    TestBedPanel() {
        super("panel", "Show your private control panel: act-as switch and quick tools", false, false);
    }

    @Override
    public boolean isEphemeral(SlashCommandInteractionEvent event) {
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!TestBedService.isTestBed(game)) {
            MessageHelper.replyToMessage(event, "This game is not a test bed. Run `/testbed apply` or `enable` first.");
            return;
        }
        Player target = TestBedService.resolveActingPlayer(
                game, event, game.getPlayer(event.getUser().getId()));
        event.getHook()
                .sendMessage(TestBedPanelService.content(game, target, null))
                .setComponents(TestBedPanelService.components(game, target))
                .setEphemeral(true)
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
