package ti4.commands.status;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class SCTradeGoods extends GameStateSubcommand {

    public SCTradeGoods() {
        super(Constants.SC_TRADE_GOODS, "Add trade goods to strategy cards", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy Cards number").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, Constants.TG, "Trade good count on card").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        OptionMapping scOption = event.getOption(Constants.STRATEGY_CARD);
        OptionMapping tgOption = event.getOption(Constants.TG);
        if (scOption != null || tgOption != null) {
            if (scOption == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify Strategy Card");
                return;

            }
            if (tgOption == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify Trade Good Count");
                return;

            }
            int sc = scOption.getAsInt();
            int tg = tgOption.getAsInt();
            Map<Integer, Integer> scTradeGoods = game.getScTradeGoods();
            if (!scTradeGoods.containsKey(sc)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Strategy Card must be from possible ones in Game");
                return;
            }
            Set<Integer> scPicked = new HashSet<>();
            for (Player player_ : game.getPlayers().values()) {
                scPicked.addAll(player_.getSCs());
            }
            if (scPicked.contains(sc)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Strategy Card is already picked, can't add Trade Goods");
                return;
            }
            game.setScTradeGood(sc, tg);
            return;
        }

        Map<Integer, Integer> scTradeGoods = game.getScTradeGoods();
        Set<Integer> scPicked = new HashSet<>();
        for (Player player_ : game.getPlayers().values()) {
            scPicked.addAll(player_.getSCs());
        }
        for (Integer scNumber : scTradeGoods.keySet()) {
            if (!scPicked.contains(scNumber)) {
                Integer tgCount = scTradeGoods.get(scNumber);
                tgCount = tgCount == null ? 1 : tgCount + 1;
                game.setScTradeGood(scNumber, tgCount);
            }
        }
    }
}
