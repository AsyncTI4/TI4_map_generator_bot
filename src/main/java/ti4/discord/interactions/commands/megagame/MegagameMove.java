package ti4.discord.interactions.commands.megagame;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.megagame.MegagameGroupService;

class MegagameMove extends MegagameGmSubcommand {

    MegagameMove() {
        super("move", "Move a faction to another group, e.g. to rebalance after an elimination", true);
        addOptions(factionOption(), groupOption());
        addOptions(new OptionData(
                OptionType.INTEGER, Constants.POSITION, "Seat in the group order, 1 = first (default: last)"));
    }

    @Override
    String run(SlashCommandInteractionEvent event, Game game) {
        Player player = targetPlayer(event, game);
        if (player == null) {
            return "Unknown faction or color.";
        }
        String group = event.getOption(GROUP).getAsString();
        Integer position = event.getOption(Constants.POSITION, null, OptionMapping::getAsInt);
        return withSummary(game, MegagameGroupService.movePlayer(game, player, group, position));
    }
}
