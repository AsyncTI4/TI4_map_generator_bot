package ti4.discord.interactions.commands.testbed;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.message.MessageHelper;
import ti4.service.testbed.TestBedService;

class TestBedDisable extends GameStateSubcommand {

    TestBedDisable() {
        super("disable", "Stop treating this game as a test bed", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        TestBedService.markAsTestBed(game, false);
        TestBedService.clearAllActingAs(game);
        MessageHelper.replyToMessage(event, "**" + game.getName() + "** is no longer a test bed.");
    }
}
