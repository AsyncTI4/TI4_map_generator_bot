package ti4.discord.interactions.commands.testbed;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.testbed.TestBedService;

class TestBedActAs extends GameStateSubcommand {

    TestBedActAs() {
        super("act_as", "Act as a seat for buttons and commands (leave empty to act as yourself)", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Seat to act as").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!TestBedService.isTestBed(game)) {
            MessageHelper.replyToMessage(event, "This game is not a test bed. Run `/testbed enable` first.");
            return;
        }
        String userId = event.getUser().getId();
        OptionMapping seatOption = event.getOption(Constants.FACTION_COLOR);
        if (seatOption == null) {
            TestBedService.setActingAs(game, userId, null);
            MessageHelper.replyToMessage(event, "You are acting as yourself again.");
            return;
        }
        Player seat = game.getPlayerFromColorOrFaction(seatOption.getAsString().split(" ")[0]);
        if (seat == null || !seat.isRealPlayer()) {
            MessageHelper.replyToMessage(event, "No seat `" + seatOption.getAsString() + "` in this game.");
            return;
        }
        TestBedService.setActingAs(game, userId, seat);
        MessageHelper.replyToMessage(
                event,
                "You are now acting as **" + seat.getFaction() + "** (" + seat.getColor()
                        + ") for buttons and commands outside seat channels.");
    }
}
