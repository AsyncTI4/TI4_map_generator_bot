package ti4.commands.player;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import org.apache.commons.collections4.ListUtils;

import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.model.PromissoryNoteModel;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.commands.cardspn.PNInfo;

import java.util.*;

public class Turn extends PlayerSubcommandData {
    public Turn() {
        super(Constants.TURN, "End Turn");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set up faction"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player mainPlayer = activeGame.getPlayer(getUser().getId());
        mainPlayer = Helper.getGamePlayer(activeGame, mainPlayer, event, null);
        mainPlayer = Helper.getPlayer(activeGame, mainPlayer, event);

        if (mainPlayer == null) {
            sendMessage("Player/Faction/Color could not be found in map:" + activeGame.getName());
            return;
        }
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(mainPlayer.getPrivateChannel(), "_ _");
        } else {
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), Helper.getPlayerRepresentation(mainPlayer, activeGame) + " ended turn");
        }
        pingNextPlayer(event, activeGame, mainPlayer);
        mainPlayer.resetOlradinPolicyFlags();
        //if (!nextMessage.isEmpty()) sendMessage(nextMessage); Sending message in ping next Player
    }

    public void execute(GenericInteractionCreateEvent event, Player mainPlayer, Game activeGame) {
        activeGame.setComponentAction(false);
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(mainPlayer.getPrivateChannel(), "_ _");
        } else {
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), Helper.getPlayerRepresentation(mainPlayer, activeGame) + " ended turn");

        }
        String nextMessage = pingNextPlayer(event, activeGame, mainPlayer);
        if (!nextMessage.isEmpty()) MessageHelper.sendMessageToChannel(event.getMessageChannel(), nextMessage);
    }

    public Player findNextUnpassedPlayer(Game activeGame, Player currentPlayer) {
        int startingInitiative = activeGame.getPlayersTurnSCInitiative(currentPlayer);
        //in a normal game, 8 is the maximum number, so we modulo on 9
        List<Player> unpassedPlayers = activeGame.getRealPlayers().stream().filter(p -> !p.isPassed()).toList();
        int maxSC = Collections.max(activeGame.getSCList()) + 1;
        for (int i = 1; i <= maxSC; i++) {
            int scCheck = (startingInitiative + i) % maxSC;
            for (Player p : unpassedPlayers) {
                if (activeGame.getPlayersTurnSCInitiative(p) == scCheck) {
                    return p;
                }
            }
        }
        if (unpassedPlayers.isEmpty()) {
            return null;
        } else {
            return unpassedPlayers.get(0);
        }
    }

    public String pingNextPlayer(GenericInteractionCreateEvent event, Game activeGame, Player mainPlayer) {
        activeGame.setComponentAction(false);
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(activeGame, event);
        MessageChannel gameChannel = activeGame.getMainGameChannel() == null ? event.getMessageChannel() : activeGame.getMainGameChannel();

        //MAKE ALL NON-REAL PLAYERS PASSED
        for (Player player : activeGame.getPlayers().values()) {
            if (!player.isRealPlayer()) {
                player.setPassed(true);
            }
        }

        if (activeGame.getPlayers().values().stream().allMatch(Player::isPassed)) {
            showPublicObjectivesWhenAllPassed(event, activeGame, gameChannel);
            activeGame.updateActivePlayer(null);
            return "";
        }

        Player nextPlayer = findNextUnpassedPlayer(activeGame, mainPlayer);
        if (!activeGame.isFoWMode()) {
            try {
                if (activeGame.getLatestTransactionMsg() != null && !"".equals(activeGame.getLatestTransactionMsg())) {
                    activeGame.getMainGameChannel().deleteMessageById(activeGame.getLatestTransactionMsg()).queue();
                    activeGame.setLatestTransactionMsg("");
                }
            } catch (Exception e) {
                //  Block of code to handle errors
            }
        }
        String text = "# " + Helper.getPlayerRepresentation(nextPlayer, activeGame, event.getGuild(), true) + " UP NEXT";
        String buttonText = "Use buttons to do your turn. ";
        List<Button> buttons = ButtonHelper.getStartOfTurnButtons(nextPlayer, activeGame, false, event);

        activeGame.updateActivePlayer(nextPlayer);
        activeGame.setCurrentPhase("action");
        ButtonHelperFactionSpecific.resolveMilitarySupportCheck(nextPlayer, activeGame);
        if (isFowPrivateGame) {

            FoWHelper.pingAllPlayersWithFullStats(activeGame, event, mainPlayer, "ended turn");
            FoWHelper.pingAllPlayersWithFullStats(activeGame, event, nextPlayer, "started turn");

            String fail = "User for next faction not found. Report to ADMIN";
            String success = "The next player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(nextPlayer, activeGame, event, text, fail, success);
            MessageHelper.sendMessageToChannelWithButtons(nextPlayer.getPrivateChannel(), buttonText, buttons);

            if (getMissedSCFollowsText(activeGame, nextPlayer) != null && !getMissedSCFollowsText(activeGame, nextPlayer).equalsIgnoreCase("")) {
                MessageHelper.sendMessageToChannel(nextPlayer.getPrivateChannel(), getMissedSCFollowsText(activeGame, nextPlayer));
            }
            if (nextPlayer.getStasisInfantry() > 0) {
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(nextPlayer, activeGame),
                    "Use buttons to revive infantry. You have " + nextPlayer.getStasisInfantry() + " infantry left to revive.", ButtonHelper.getPlaceStatusInfButtons(activeGame, nextPlayer));
            }

            activeGame.setPingSystemCounter(0);
            for (int x = 0; x < 10; x++) {
                activeGame.setTileAsPinged(x, null);
            }
        } else {
            MessageHelper.sendMessageToChannel(gameChannel, text);
            MessageHelper.sendMessageToChannelWithButtons(gameChannel, buttonText, buttons);
            if (getMissedSCFollowsText(activeGame, nextPlayer) != null && !"".equalsIgnoreCase(getMissedSCFollowsText(activeGame, nextPlayer))) {
                MessageHelper.sendMessageToChannel(gameChannel, getMissedSCFollowsText(activeGame, nextPlayer));
            }
            if (nextPlayer.getStasisInfantry() > 0) {
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(nextPlayer, activeGame),
                    "Use buttons to revive infantry. You have " + nextPlayer.getStasisInfantry() + " infantry left to revive.", ButtonHelper.getPlaceStatusInfButtons(activeGame, nextPlayer));
            }

            return "";
        }

        if(nextPlayer == null){
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Next player not found");
        }
       // MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Next player not found");
        return "";
    }

    private String getMissedSCFollowsText(Game activeGame, Player player) {
        if (!activeGame.isStratPings()) return null;
        boolean sendReminder = false;

        StringBuilder sb = new StringBuilder("> " + Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " Please react to ");
        int count = 0;
        for (int sc : activeGame.getPlayedSCs()) {
            if (!player.hasFollowedSC(sc)) {
                sb.append(Helper.getSCBackRepresentation(activeGame, sc));
                sendReminder = true;
                count++;
            }
        }
        sb.append(" above before doing anything else. You currently have ").append(player.getStrategicCC()).append(" CC in your strategy pool.");
        if (count > 1) {
            sb.append(" Make sure to resolve the strategy cards in the order they were played.");
        }
        return sendReminder ? sb.toString() : null;
    }

    public List<Button> getScoreObjectiveButtons(GenericInteractionCreateEvent event, Game activeGame) {
        LinkedHashMap<String, Integer> revealedPublicObjectives = activeGame.getRevealedPublicObjectives();
        HashMap<String, String> publicObjectivesState1 = Mapper.getPublicObjectivesStage1();
        HashMap<String, String> publicObjectivesState2 = Mapper.getPublicObjectivesStage2();
        LinkedHashMap<String, Integer> customPublicVP = activeGame.getCustomPublicVP();
        List<Button> poButtons = new ArrayList<>();
        List<Button> poButtons1 = new ArrayList<>();
        List<Button> poButtons2 = new ArrayList<>();
        List<Button> poButtonsCustom = new ArrayList<>();
        int poStatus;
        for (Map.Entry<String, Integer> objective : revealedPublicObjectives.entrySet()) {
            String key = objective.getKey();
            String po_name = publicObjectivesState1.get(key);
            poStatus = 0;
            if (po_name == null) {
                po_name = publicObjectivesState2.get(key);
                poStatus = 1;
            }
            if (po_name == null) {
                Integer integer = customPublicVP.get(key);
                if (integer != null && !key.toLowerCase().contains("custodian") && !key.toLowerCase().contains("imperial")
                    && !key.contains("Shard of the Throne")) {
                    po_name = key;
                    poStatus = 2;
                }
            }
            if (po_name != null) {
                Integer value = objective.getValue();
                Button objectiveButton;
                if (poStatus == 0) { //Stage 1 Objectives
                    objectiveButton = Button.success(Constants.PO_SCORING + value, "(" + value + ") " + po_name).withEmoji(Emoji.fromFormatted(Emojis.Public1alt));
                    poButtons1.add(objectiveButton);
                } else if (poStatus == 1) { //Stage 2 Objectives
                    objectiveButton = Button.primary(Constants.PO_SCORING + value, "(" + value + ") " + po_name).withEmoji(Emoji.fromFormatted(Emojis.Public2alt));
                    poButtons2.add(objectiveButton);
                } else { //Other Objectives
                    objectiveButton = Button.secondary(Constants.PO_SCORING + value, "(" + value + ") " + po_name);
                    poButtonsCustom.add(objectiveButton);
                }
            }
        }

        poButtons.addAll(poButtons1);
        poButtons.addAll(poButtons2);
        poButtons.addAll(poButtonsCustom);
        poButtons.removeIf(Objects::isNull);
        return poButtons;
    }

    public void showPublicObjectivesWhenAllPassed(GenericInteractionCreateEvent event, Game activeGame, MessageChannel gameChannel) {
        String message = "All players passed. Please score objectives. " + Helper.getGamePing(event, activeGame);
        activeGame.setCurrentPhase("status");
        List<Button> poButtons = getScoreObjectiveButtons(event, activeGame);
        Button noPOScoring = Button.danger(Constants.PO_NO_SCORING, "No PO Scored");
        Button noSOScoring = Button.danger(Constants.SO_NO_SCORING, "No SO Scored");
        poButtons.add(noPOScoring);
        poButtons.add(noSOScoring);
        if (activeGame.getActionCards().size() > 130 && Helper.getPlayerFromColorOrFaction(activeGame, "hacan") != null
            && ButtonHelper.getButtonsToSwitchWithAllianceMembers(Helper.getPlayerFromColorOrFaction(activeGame, "hacan"), activeGame, false).size() > 0) {
            poButtons.add(Button.secondary("getSwapButtons_", "Swap"));
        }
        poButtons.removeIf(Objects::isNull);
        List<List<Button>> partitions = ListUtils.partition(poButtons, 5);
        List<ActionRow> actionRows = new ArrayList<>();
        for (List<Button> partition : partitions) {
            actionRows.add(ActionRow.of(partition));
        }
        MessageCreateData messageObject = new MessageCreateBuilder()
            .addContent(message)
            .addComponents(actionRows).build();

        gameChannel.sendMessage(messageObject).queue();

        // return beginning of status phase PNs
        LinkedHashMap<String, Player> players = activeGame.getPlayers();
        for (Player player : players.values()) {
            List<String> pns = new ArrayList<>(player.getPromissoryNotesInPlayArea());
            for (String pn : pns) {
                Player pnOwner = activeGame.getPNOwner(pn);
                if (!pnOwner.isRealPlayer()) {
                    continue;
                }
                PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(pn);
                if (pnModel.getText().contains("eturn this card") && (pnModel.getText().contains("start of the status phase") || pnModel.getText().contains("beginning of the status phase"))) {
                    player.removePromissoryNote(pn);
                    pnOwner.setPromissoryNote(pn);
                    PNInfo.sendPromissoryNoteInfo(activeGame, pnOwner, false);
                    PNInfo.sendPromissoryNoteInfo(activeGame, player, false);
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), pnModel.getName() + " was returned");
                }
            }
        }

        for (Player p2 : activeGame.getRealPlayers()) {
            String ms2 = getMissedSCFollowsText(activeGame, p2);
            if (ms2 != null && !"".equalsIgnoreCase(ms2)) {
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), ms2);
            }
        }

        Player arborec = Helper.getPlayerFromAbility(activeGame, "mitosis");
        if (arborec != null) {
            String mitosisMessage = Helper.getPlayerRepresentation(arborec, activeGame, event.getGuild(), true) + " reminder to do mitosis!";
            MessageHelper.sendMessageToChannelWithButtons(arborec.getCardsInfoThread(activeGame), mitosisMessage, ButtonHelperFactionSpecific.getMitosisOptions(activeGame, arborec));

        }
        Player solPlayer = Helper.getPlayerFromUnit(activeGame, "sol_flagship");

        if (solPlayer != null) {
            String colorID = Mapper.getColorID(solPlayer.getColor());
            String fsKey = colorID + "_fs.png";
            String infKey = colorID + "_gf.png";
            for (Tile tile : activeGame.getTileMap().values()) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    if (unitHolder.getUnits() != null) {
                        if (unitHolder.getUnits().get(fsKey) != null && unitHolder.getUnits().get(fsKey) > 0) {
                            unitHolder.addUnit(infKey, 1);
                            String genesisMessage = Helper.getPlayerRepresentation(solPlayer, activeGame, event.getGuild(), true)
                                + " an infantry was added to the space area of your flagship automatically.";
                            if (activeGame.isFoWMode()) {
                                MessageHelper.sendMessageToChannel(solPlayer.getPrivateChannel(), genesisMessage);
                            } else {
                                MessageHelper.sendMessageToChannel(gameChannel, genesisMessage);
                            }
                        }
                    }
                }
            }
        }
    }
}
