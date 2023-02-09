package ti4.commands.player;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.MapGenerator;
import ti4.commands.status.ListTurnOrder;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.*;
import java.util.stream.Collectors;

public class SCPick extends PlayerSubcommandData {
    public SCPick() {
        super(Constants.SC_PICK, "Pick SC");
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy Card Number count").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "You're not a player of this game");
            return;
        }
        Stats.pickSC(event, activeMap, player, event.getOption(Constants.STRATEGY_CARD));
        int sc = player.getSC();
        String msg = "";
        String msgExtra = "";
        boolean allPicked = true;
        Player privatePlayer = null;
        if (sc != 0) {
            msg += Helper.getPlayerRepresentation(event, player);
            msg += " Picked: " + Helper.getSCEmojiFromInteger(sc) + Helper.getSCAsMention(event.getGuild(), sc);

            boolean nextCorrectPing = false;
            Collection<Player> activePlayers = activeMap.getPlayers().values().stream()
                    .filter(player_ -> player_.isActivePlayer())
                    .collect(Collectors.toList());
            Queue<Player> players = new ArrayDeque<>(activePlayers);
            while (players.iterator().hasNext()) {
                Player player_ = players.poll();
                if (!player_.isActivePlayer()) {
                    continue;
                }
                if (nextCorrectPing && player_.getSC() == 0 && player_.getFaction() != null) {
                    msgExtra += Helper.getPlayerRepresentation(event, player_) + " To Pick SC";
                    privatePlayer = player_;
                    allPicked = false;
                    break;
                }
                if (player_ == player) {
                    nextCorrectPing = true;
                }
                if (player_.getSC() == 0 && player_.getFaction() != null) {
                    players.add(player_);
                }
            }
            if (allPicked) {
                msgExtra += Helper.getGamePing(event, activeMap) + "All players picked SC";

                LinkedHashMap<Integer, Integer> scTradeGoods = activeMap.getScTradeGoods();
                Set<Integer> scPickedList = activePlayers.stream().map(Player::getSC).collect(Collectors.toSet());
                for (Integer scNumber : scTradeGoods.keySet()) {
                    if (!scPickedList.contains(scNumber) && scNumber != 0) {
                        Integer tgCount = scTradeGoods.get(scNumber);
                        tgCount = tgCount == null ? 1 : tgCount + 1;
                        activeMap.setScTradeGood(scNumber, tgCount);
                    }
                }

                Player nextPlayer = null;
                int lowestSC = 100;
                for (Player player_ : activePlayers) {
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
                    msgExtra += " " + Helper.getPlayerRepresentation(event, nextPlayer) + " is up for an action";
                    privatePlayer = nextPlayer;
                }
            }
        } else {
            msg = "No SC picked.";
        }
        MessageHelper.replyToMessage(event, msg);
        Boolean privateGame = FoWHelper.isPrivateGame(activeMap, event);
        if (privateGame != null && privateGame) {
            if (privatePlayer == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), msgExtra + " Ping personally as Bot could not find player");
                return;
            }
            User user = MapGenerator.jda.getUserById(privatePlayer.getUserID());
            if (user == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "User for faction not found. Report to ADMIN");
            } else {
                MessageHelper.sentToMessageToUser(event, activeMap.getName() + " " + msgExtra, user);
            }
        }
        if (allPicked) {
            ListTurnOrder.turnOrder(event, activeMap);
        }
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
