package ti4.discord.interactions.commands.testbed;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.testbed.TestBedService;

class TestBedEnable extends GameStateSubcommand {

    TestBedEnable() {
        super("enable", "Mark this game as a test bed (developers may then act as any seat)", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player nonDeveloper = TestBedService.findNonDeveloper(event.getGuild(), game.getRealPlayers());
        if (nonDeveloper != null) {
            MessageHelper.replyToMessage(
                    event,
                    "Refused: " + nonDeveloper.getUserName() + " is a real player without the developer role. "
                            + "The test bed is only for games with developers and virtual seats.");
            return;
        }
        TestBedService.markAsTestBed(game, true);
        MessageHelper.replyToMessage(
                event,
                "**" + game.getName() + "** is now a test bed. Buttons pressed inside a seat's channel act as that"
                        + " seat; use `/testbed act_as` for buttons in shared channels.");
    }
}
