package ti4.commands2.player;

import java.util.Collections;
import java.util.Set;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.ThreadArchiveHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.strategycard.PlayStrategyCardService;

class SCPlay extends GameStateSubcommand {

    public SCPlay() {
        super(Constants.SC_PLAY, "Play a Strategy Card", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD,
            "Which strategy card to play. If you have more than 1 strategy card, this is mandatory"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player"));
        addOptions(
            new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ThreadArchiveHelper.checkThreadLimitAndArchive(event.getGuild());

        MessageChannel eventChannel = event.getChannel();
        Game game = getGame();
        MessageChannel mainGameChannel = game.getMainGameChannel() == null ? eventChannel : game.getMainGameChannel();
        Player player = getPlayer();
        Set<Integer> playersSCs = player.getSCs();
        if (playersSCs.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No strategy card has been selected.");
            return;
        }

        if (playersSCs.size() != 1 && event.getOption(Constants.STRATEGY_CARD) == null) { // Only one SC selected
            MessageHelper.sendMessageToEventChannel(event, "Player has more than one strategy card. Please try again, using the `strategy_card` option.");
            return;
        }

        Integer scToPlay = event.getOption(Constants.STRATEGY_CARD, Collections.min(player.getSCs()), OptionMapping::getAsInt);
        PlayStrategyCardService.playSC(event, scToPlay, game, mainGameChannel, player);
    }
}
