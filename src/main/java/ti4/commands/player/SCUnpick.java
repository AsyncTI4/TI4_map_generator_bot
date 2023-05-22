package ti4.commands.player;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
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

public class SCUnpick extends PlayerSubcommandData {
    public SCUnpick() {
        super(Constants.SC_UNPICK, "Unpick an SC");
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy Card #").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);

        Boolean privateGame = FoWHelper.isPrivateGame(activeMap, event);
        boolean isFowPrivateGame = (privateGame != null && privateGame);

        if (player == null) {
            sendMessage("You're not a player of this game");
            return;
        }

        Collection<Player> activePlayers = activeMap.getPlayers().values().stream()
                .filter(player_ -> player_.getFaction() != null && !player_.getFaction().isEmpty() && !player_.getColor().equals("null"))
                .collect(Collectors.toList());
        int maxSCsPerPlayer = activeMap.getSCList().size() / activePlayers.size();

        OptionMapping option = event.getOption(Constants.STRATEGY_CARD);
        int scUnpicked = option.getAsInt();

        player.removeSC(scUnpicked);

        int playerSCCount = player.getSCs().size();
        if (playerSCCount >= maxSCsPerPlayer) {
            return;
        }

        // return; //force end this for now, let players manage next to pick until look at this again

        String msg = "";
        String msgExtra = "";
        boolean allPicked = true;
        Player privatePlayer = null;

        boolean nextCorrectPing = false;
        Queue<Player> players = new ArrayDeque<>(activePlayers);
        while (players.iterator().hasNext()) {
            Player player_ = players.poll();
            if (player_ == null || !player_.isRealPlayer()) {
                continue;
            }
            int player_SCCount = player_.getSCs().size();
            if (nextCorrectPing && player_SCCount < maxSCsPerPlayer && player_.getFaction() != null) {
                msgExtra += Helper.getPlayerRepresentation(player_, activeMap, event.getGuild(), true) + " To Pick SC";
                privatePlayer = player_;
                allPicked = false;
                break;
            }
            if (player_ == player) {
                nextCorrectPing = true;
            }
            if (player_SCCount < maxSCsPerPlayer && player_.getFaction() != null) {
                players.add(player_);
            }
        }

        //INFORM ALL PLAYER HAVE PICKED
        if (allPicked) {
            msgExtra += Helper.getGamePing(event, activeMap) + "\nAll players picked SC";

            LinkedHashMap<Integer, Integer> scTradeGoods = activeMap.getScTradeGoods();
            Set<Integer> scPickedList = new HashSet<>();
            for (Player player_ : activePlayers) {
                scPickedList.addAll(player_.getSCs());
            }

            //ADD A TG TO UNPICKED SC
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
                int playersLowestSC = player_.getLowestSC();
                String scNumberIfNaaluInPlay = GenerateMap.getSCNumberIfNaaluInPlay(player_, activeMap, Integer.toString(playersLowestSC));
                if (scNumberIfNaaluInPlay.startsWith("0/")) {
                    nextPlayer = player_; //no further processing, this player has the 0 token
                    break;
                }
                if (playersLowestSC < lowestSC) {
                    lowestSC = playersLowestSC;
                    nextPlayer = player_;
                }
            }

            //INFORM FIRST PLAYER IS UP FOR ACTION
            if (nextPlayer != null) {
                msgExtra += " " + Helper.getPlayerRepresentation(nextPlayer, activeMap) + " is up for an action";
                privatePlayer = nextPlayer;
                activeMap.updateActivePlayer(nextPlayer);
            }
        }

        //SEND EXTRA MESSAGE
        if (isFowPrivateGame ) {
            if (allPicked) {
                msgExtra = Helper.getPlayerRepresentation(privatePlayer, activeMap, event.getGuild(), true) + " UP NEXT";
            }
            String fail = "User for next faction not found. Report to ADMIN";
            String success = "The next player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(privatePlayer, activeMap, event, msgExtra, fail, success);
        } else {
            if (allPicked) {
                ListTurnOrder.turnOrder(event, activeMap);
            }
            if (!msgExtra.isEmpty()) {
                sendMessage(msgExtra);
            }
        }
    }
}
