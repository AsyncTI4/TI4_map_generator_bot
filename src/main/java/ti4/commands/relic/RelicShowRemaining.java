package ti4.commands.relic;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class RelicShowRemaining extends GameStateSubcommand {

    public RelicShowRemaining() {
        super(Constants.RELIC_SHOW_REMAINING, "Show remaining relics in deck", false, true);
        addOptions(new OptionData(OptionType.STRING, Constants.OVERRIDE_FOW, "TRUE if override fog"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping override = event.getOption(Constants.OVERRIDE_FOW);
        boolean over = false;
        if (override != null) {
            over = "TRUE".equalsIgnoreCase(override.getAsString());
        }
        showRemaining(event, over, getGame(), getPlayer());
    }

    public static void showRemaining(GenericInteractionCreateEvent event, boolean over, Game game, Player player) {
        List<String> allRelics = new ArrayList<>(game.getAllRelics());

        Integer deckCount = allRelics.size();
        Double deckDrawChance = deckCount == 0 ? 0.0 : 1.0 / deckCount;
        NumberFormat formatPercent = NumberFormat.getPercentInstance();
        formatPercent.setMaximumFractionDigits(1);
        StringBuilder text;
        if (allRelics.isEmpty()) {
            text = new StringBuilder("**RELIC DECK IS EMPTY**");
        } else {
            text = new StringBuilder(Emojis.Relic).append(" **RELICS REMAINING IN DECK** (").append(deckCount).append(") _").append(formatPercent.format(deckDrawChance)).append("_\n");
            Collections.sort(allRelics);
            for (String relicId : allRelics) {
                String relicName = Mapper.getRelic(relicId).getName();
                text.append("- ").append(relicName).append("\n");
            }
        }

        if (player != null && "action".equalsIgnoreCase(game.getPhaseOfGame()) && !over && game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "It is foggy outside, please wait until status/agenda to do this command, or override the fog.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), text.toString());
        }
    }
}
