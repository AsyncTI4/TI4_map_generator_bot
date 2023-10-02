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

public class TurnEnd extends PlayerSubcommandData {
    public TurnEnd() {
        super(Constants.TURN_END, "End Turn");
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
        pingNextPlayer(event, activeGame, mainPlayer);
        mainPlayer.resetOlradinPolicyFlags();
    }

    public static Player findNextUnpassedPlayer(Game activeGame, Player currentPlayer) {
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

    public static void pingNextPlayer(GenericInteractionCreateEvent event, Game activeGame, Player mainPlayer) {
        activeGame.setComponentAction(false);
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(mainPlayer.getPrivateChannel(), "_ _");
        } else {
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), Helper.getPlayerRepresentation(mainPlayer, activeGame) + " ended turn");
        }
        
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
            return;
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
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(activeGame, event);
        if (isFowPrivateGame) {
            FoWHelper.pingAllPlayersWithFullStats(activeGame, event, mainPlayer, "ended turn");
        }
        TurnStart.turnStart(event, activeGame, nextPlayer);
    }

    public static List<Button> getScoreObjectiveButtons(GenericInteractionCreateEvent event, Game activeGame) {
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

    public static void showPublicObjectivesWhenAllPassed(GenericInteractionCreateEvent event, Game activeGame, MessageChannel gameChannel) {
        String message = "All players passed. Please score objectives. " + Helper.getGamePing(event, activeGame);
        activeGame.setCurrentPhase("status");
        for(Player player : activeGame.getRealPlayers()){
            List<String> relics = new ArrayList<>();
            relics.addAll(player.getRelics());
            for(String relic : relics){
                if(player.getExhaustedRelics().contains(relic)&& relic.contains("axisorder")){
                    player.removeRelic(relic);
                }
            }
        }
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
            String ms2 = TurnStart.getMissedSCFollowsText(activeGame, p2);
            if (ms2 != null && !"".equalsIgnoreCase(ms2)) {
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), ms2);
            }
        }

        Player arborec = Helper.getPlayerFromAbility(activeGame, "mitosis");
        if (arborec != null) {
            String mitosisMessage = Helper.getPlayerRepresentation(arborec, activeGame, event.getGuild(), true) + " reminder to do mitosis!";
            MessageHelper.sendMessageToChannelWithButtons(arborec.getCardsInfoThread(), mitosisMessage, ButtonHelperFactionSpecific.getMitosisOptions(activeGame, arborec));

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
