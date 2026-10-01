package ti4.discord.interactions.commands.testbed;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.testbed.TestBedService;

class TestBedStatus extends GameStateSubcommand {

    TestBedStatus() {
        super("status", "Show test bed state for this game", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player actingAs = TestBedService.getActingAs(game, event.getUser().getId());
        StringBuilder seats = new StringBuilder();
        for (Player player : game.getRealPlayers()) {
            seats.append("\n> ")
                    .append(player.getFaction())
                    .append(" (")
                    .append(player.getColor())
                    .append(") ")
                    .append(TestBedService.isVirtualSeat(player) ? "virtual" : player.getUserName());
        }
        MessageHelper.replyToMessage(
                event,
                "Test bed: **" + TestBedService.isTestBed(game) + "**. You are acting as: **"
                        + (actingAs == null ? "yourself" : actingAs.getFaction()) + "**.\nSeats:" + seats);
    }
}
