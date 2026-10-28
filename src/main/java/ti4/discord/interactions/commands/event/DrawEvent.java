package ti4.discord.interactions.commands.event;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;

class DrawEvent extends GameStateSubcommand {

    public DrawEvent() {
        super(Constants.DRAW, "Draw Event", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw, default 1")
                .setRequiredRange(1, 10));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        int count = Math.clamp(event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt), 1, 10);
        for (int i = 0; i < count; i++) {
            game.drawEvent(player.getUserID());
        }
        EventInfo.sendEventInfo(player, event);
    }
}
