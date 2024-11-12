package ti4.commands.event;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.PlayerGameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;

public class DrawEvent extends PlayerGameStateSubcommand {

    public DrawEvent() {
        super(Constants.DRAW, "Draw Event", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw, default 1"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        int count = Math.max(1, event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt));
        for (int i = 0; i < count; i++) {
            game.drawEvent(player.getUserID());
        }
        EventInfo.sendEventInfo(game, player, event);
    }
}
