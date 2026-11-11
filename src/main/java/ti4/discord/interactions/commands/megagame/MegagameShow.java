package ti4.discord.interactions.commands.megagame;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.game.Game;
import ti4.message.MessageHelper;
import ti4.service.megagame.MegagameGroupService;
import ti4.service.megagame.MegagameStrategyPhaseService;

class MegagameShow extends MegagameGmSubcommand {

    private static final String NEXT_ROUND = "next_round";

    MegagameShow() {
        super("show", "Show groups, group speakers, held strategy cards and current pickers", false);
        addOptions(new OptionData(
                OptionType.BOOLEAN, NEXT_ROUND, "Preview the groups after the next shift instead (changes nothing)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!MegagameGroupService.isConfigured(game)) {
            super.execute(event);
            return;
        }
        if (event.getOption(NEXT_ROUND, false, OptionMapping::getAsBoolean)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), MegagameGroupService.nextRoundSummary(game));
            MegagameStrategyPhaseService.sendNextRoundImage(game, event.getChannel());
            return;
        }
        super.execute(event);
        MegagameStrategyPhaseService.sendGroupsImage(game, event.getChannel());
    }

    @Override
    String run(SlashCommandInteractionEvent event, Game game) {
        return MegagameGroupService.summary(game);
    }
}
