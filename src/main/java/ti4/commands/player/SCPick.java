package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

public class SCPick extends PlayerSubcommandData {
    public SCPick() {
        super(Constants.SC_PICK, "Pick SC");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC, "Strategy Card Number count").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Your not a player of this game");
            return;
        }
        Stats.pickSC(event, activeMap, player, event.getOption(Constants.SC));
        int sc = player.getSC();
        String msg = "";
        String msgExtra = "";
        if (sc != 0) {
            msg += Helper.getFactionIconFromDiscord(player.getFaction());
            msg += " " + player.getUserName();
            String color = player.getColor();
            if (color != null) {
                msg += " (" + color + ")";
            }
            msg += " Picked: " + Helper.getSCAsMention(sc);

            boolean nextCorrectPing = false;
            boolean allPicked = true;
            Queue<Player> players = new ArrayDeque<>(activeMap.getPlayers().values());
            while (players.iterator().hasNext()) {
                Player player_ = players.poll();
                if (nextCorrectPing && player_ != null && player_.getSC() == 0) {
                    msgExtra += Helper.getFactionIconFromDiscord(player_.getFaction());
                    msgExtra += " " + Helper.getPlayerPing(event, player_) + " To Pick SC";
                    allPicked = false;
                    break;
                }
                if (player_ == player) {
                    nextCorrectPing = true;
                }
                if (player_ != null && player_.getSC() == 0) {
                    players.add(player_);
                }
            }
            if (allPicked) {
                msgExtra += Helper.getGamePing(event, activeMap) + "All Picked SC, Start round";

                LinkedHashMap<Integer, Integer> scTradeGoods = activeMap.getScTradeGoods();
                Set<Integer> scPickedList = activeMap.getPlayers().values().stream().map(Player::getSC).collect(Collectors.toSet());
                for (Integer scNumber : scTradeGoods.keySet()) {
                    if (!scPickedList.contains(scNumber) && scNumber != 0) {
                        Integer tgCount = scTradeGoods.get(scNumber);
                        tgCount = tgCount == null ? 1 : tgCount + 1;
                        activeMap.setScTradeGood(scNumber, tgCount);
                    }
                }

                Player nextPlayer = null;
                int lowestSC = 100;
                for (Player player_ : activeMap.getPlayers().values()) {
                    int scPicked = player_.getSC();
                    String scNumberIfNaaluInPlay = GenerateMap.getSCNumberIfNaaluInPlay(player_, activeMap, Integer.toString(sc));
                    if (scNumberIfNaaluInPlay.startsWith("0/")) {
                        nextPlayer = player_;
                        break;
                    }
                    if (scPicked < lowestSC) {
                        lowestSC = scPicked;
                        nextPlayer = player_;
                    }
                }
                if (nextPlayer != null) {
                    msgExtra += " " + Helper.getPlayerPing(event, nextPlayer) + " To Start Round";
                }
            }

        } else {
            msg = "No SC picked.";
        }
        MessageHelper.replyToMessage(event, msg);
        if (!msgExtra.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), msgExtra);
        }
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
        MapSaveLoadManager.saveMap(activeMap);
    }
}
