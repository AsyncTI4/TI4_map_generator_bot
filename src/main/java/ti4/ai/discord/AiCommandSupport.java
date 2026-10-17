package ti4.ai.discord;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.ai.seat.AiSeatService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.testbed.TestBedService;

@UtilityClass
class AiCommandSupport {

    static OptionData seatOption(String description) {
        return new OptionData(OptionType.STRING, Constants.FACTION_COLOR, description).setAutoComplete(true);
    }

    static boolean mayManage(SlashCommandInteractionEvent event, Game game) {
        String userId = event.getUser().getId();
        return game.getPlayer(userId) != null
                || userId.equals(game.getOwnerID())
                || TestBedService.isDeveloper(event.getMember());
    }

    static String notAllowed() {
        return "Only players of this game, its owner or a bot developer can manage its AI seats.";
    }

    static List<Player> targetSeats(SlashCommandInteractionEvent event, Game game) {
        return AiSeatService.findSeats(
                game, event.getOption(Constants.FACTION_COLOR, null, OptionMapping::getAsString));
    }

    static String noSeat(SlashCommandInteractionEvent event) {
        return event.getOption(Constants.FACTION_COLOR) == null
                ? "This game has no AI seat."
                : "That faction or color is not an AI seat in this game.";
    }
}
