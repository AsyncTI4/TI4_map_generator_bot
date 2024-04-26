package ti4.commands.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

import ti4.buttons.Buttons;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.cardsso.SOInfo;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;

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
            MessageHelper.sendMessageToEventChannel(event, "Player/Faction/Color could not be found in map:" + activeGame.getName());
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
        if (ButtonHelper.getKyroHeroSC(activeGame) != 1000) {
            maxSC = maxSC + 1;
        }
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
        pingNextPlayer(event, activeGame, mainPlayer, false);
    }

    public static void pingNextPlayer(GenericInteractionCreateEvent event, Game activeGame, Player mainPlayer, boolean justPassed) {
        activeGame.setTemporaryPingDisable(false);
        mainPlayer.setWhetherPlayerShouldBeTenMinReminded(false);
        for (Player player : activeGame.getRealPlayers()) {
            for (Player player_ : activeGame.getRealPlayers()) {
                if (player_ == player) {
                    continue;
                }
                String key = player.getFaction() + "whisperHistoryTo" + player_.getFaction();
                if (!activeGame.getStoredValue(key).isEmpty()) {
                    activeGame.setStoredValue(key, "");
                }
            }
        }
        activeGame.setStoredValue("mahactHeroTarget", "");
        activeGame.setActiveSystem("");
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(mainPlayer.getPrivateChannel(), "_ _");
        } else {
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), mainPlayer.getRepresentation() + " ended turn");
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
            String lastTransaction = activeGame.getLatestTransactionMsg();
            try {
                if (lastTransaction != null && !"".equals(lastTransaction)) {
                    activeGame.setLatestTransactionMsg("");
                    activeGame.getMainGameChannel().deleteMessageById(lastTransaction).queue(null, e -> {
                    });
                }
            } catch (Exception e) {
                //  Block of code to handle errors
            }
        }
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(activeGame, event);
        if (isFowPrivateGame) {
            FoWHelper.pingAllPlayersWithFullStats(activeGame, event, mainPlayer, "ended turn");
        }
        ButtonHelper.checkFleetInEveryTile(mainPlayer, activeGame, event);
        if (mainPlayer != nextPlayer) {
            ButtonHelper.checkForPrePassing(activeGame, mainPlayer);
        }
        if (justPassed) {
            if (!ButtonHelperAgents.checkForEdynAgentPreset(activeGame, mainPlayer, nextPlayer, event)) {
                TurnStart.turnStart(event, activeGame, nextPlayer);
            }
        } else {
            if (!ButtonHelperAgents.checkForEdynAgentActive(activeGame, event)) {
                TurnStart.turnStart(event, activeGame, nextPlayer);
            }
        }

    }

    public static List<Button> getScoreObjectiveButtons(GenericInteractionCreateEvent event, Game activeGame) {
        return getScoreObjectiveButtons(event, activeGame, "");
    }

    public static List<Button> getScoreObjectiveButtons(GenericInteractionCreateEvent event, Game activeGame, String prefix) {
        Map<String, Integer> revealedPublicObjectives = activeGame.getRevealedPublicObjectives();
        Map<String, String> publicObjectivesState1 = Mapper.getPublicObjectivesStage1();
        Map<String, String> publicObjectivesState2 = Mapper.getPublicObjectivesStage2();
        Map<String, Integer> customPublicVP = activeGame.getCustomPublicVP();
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
                    objectiveButton = Button.success(prefix + Constants.PO_SCORING + value, "(" + value + ") " + po_name).withEmoji(Emoji.fromFormatted(Emojis.Public1alt));
                    poButtons1.add(objectiveButton);
                } else if (poStatus == 1) { //Stage 2 Objectives
                    objectiveButton = Button.primary(prefix + Constants.PO_SCORING + value, "(" + value + ") " + po_name).withEmoji(Emoji.fromFormatted(Emojis.Public2alt));
                    poButtons2.add(objectiveButton);
                } else { //Other Objectives
                    objectiveButton = Button.secondary(prefix + Constants.PO_SCORING + value, "(" + value + ") " + po_name);
                    poButtonsCustom.add(objectiveButton);
                }
            }
        }

        poButtons.addAll(poButtons1);
        poButtons.addAll(poButtons2);
        poButtons.addAll(poButtonsCustom);
        for (Player player : activeGame.getRealPlayers()) {
            if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "edyncommander") && !activeGame.isFoWMode()) {
                poButtons.add(Button.secondary("edynCommanderSODraw", "Draw SO instead of Scoring PO").withEmoji(Emoji.fromFormatted(Emojis.edyn)));
                break;
            }
        }
        poButtons.removeIf(Objects::isNull);
        return poButtons;
    }

    public static void showPublicObjectivesWhenAllPassed(GenericInteractionCreateEvent event, Game activeGame, MessageChannel gameChannel) {
        String message = "All players passed. Please score objectives. " + activeGame.getPing();

        activeGame.setCurrentPhase("status");
        for (Player player : activeGame.getRealPlayers()) {
            SOInfo.sendSecretObjectiveInfo(activeGame, player);
            List<String> relics = new ArrayList<>(player.getRelics());
            for (String relic : relics) {
                if (player.getExhaustedRelics().contains(relic) && relic.contains("axisorder")) {
                    player.removeRelic(relic);
                }
            }
        }
        Player vaden = Helper.getPlayerFromAbility(activeGame, "binding_debts");
        if (vaden != null) {
            for (Player p2 : vaden.getNeighbouringPlayers()) {
                if (p2.getTg() > 0 && vaden.getDebtTokenCount(p2.getColor()) > 0) {
                    String msg = p2.getRepresentation(true, true) + " you have the opportunity to pay off binding debts here. You can pay 1tg to get 2 debt tokens forgiven. ";
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Button.success("bindingDebtsRes_" + vaden.getFaction(), "Pay 1 tg"));
                    buttons.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), msg, buttons);
                }
            }
        }
        List<Button> poButtons = getScoreObjectiveButtons(event, activeGame);
        Button noPOScoring = Button.danger(Constants.PO_NO_SCORING, "No PO Scored");
        Button noSOScoring = Button.danger(Constants.SO_NO_SCORING, "No SO Scored");
        poButtons.add(noPOScoring);
        poButtons.add(noSOScoring);
        if (activeGame.getActionCards().size() > 130 && activeGame.getPlayerFromColorOrFaction("hacan") != null
            && ButtonHelper.getButtonsToSwitchWithAllianceMembers(activeGame.getPlayerFromColorOrFaction("hacan"), activeGame, false).size() > 0) {
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

        int maxVP = 0;
        for (Player player : activeGame.getRealPlayers()) {
            if (player.getTotalVictoryPoints() > maxVP) {
                maxVP = player.getTotalVictoryPoints();
            }
            if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "vadencommander")) {
                int numScoredSOs = player.getSoScored();
                int numScoredPos = player.getPublicVictoryPoints(false);
                if (numScoredPos + player.getCommodities() > player.getCommoditiesTotal()) {
                    numScoredPos = player.getCommoditiesTotal() - player.getCommodities();
                }
                player.setTg(player.getTg() + numScoredSOs);
                if (numScoredSOs > 0) {
                    ButtonHelperAbilities.pillageCheck(player, activeGame);
                    ButtonHelperAgents.resolveArtunoCheck(player, activeGame, numScoredSOs);
                }
                player.setCommodities(player.getCommodities() + numScoredPos);
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    player.getRepresentation(true, true) + " you gained " + numScoredSOs + " tg and " + numScoredPos + " commodities due to Vaden Commander");
            }
        }
        // if(maxVP+4 > activeGame.getVp()){
        //     String msg = "You can use these buttons to force scoring to go in iniative order";
        //     List<Button> buttons = new ArrayList<>();
        //     buttons.add(Button.primary("forceACertainScoringOrder", "Force Scoring in Order"));
        //     buttons.add(Button.danger("deleteButtons", "Decline to force order"));
        //     MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), msg, buttons);
        // }
        // return beginning of status phase PNs
        Map<String, Player> players = activeGame.getPlayers();
        for (Player player : players.values()) {
            List<String> pns = new ArrayList<>(player.getPromissoryNotesInPlayArea());
            for (String pn : pns) {
                Player pnOwner = activeGame.getPNOwner(pn);
                if (pnOwner == null || !pnOwner.isRealPlayer()) {
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
            if (player.hasTech("dsauguy") && player.getTg() > 2) {
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                    player.getRepresentation(true, true) + " you can use the button to pay 3tg and get a tech, using your Sentient Datapool technology", List.of(Buttons.GET_A_TECH));
            }
            Leader playerLeader = player.getLeader("kyrohero").orElse(null);
            if (player.hasLeader("kyrohero") && player.getLeaderByID("kyrohero").isPresent()
                && playerLeader != null && !playerLeader.isLocked()) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Button.success("kyroHeroInitiation", "Play Kyro Hero"));
                buttons.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                    player.getRepresentation()
                        + " Reminder this is the window to do Kyro Hero. You can use the buttons to start the process",
                    buttons);
            }

            if (player.getRelics() != null && (player.hasRelic("emphidia") || player.hasRelic("absol_emphidia"))) {
                for (String pl : player.getPlanets()) {
                    Tile tile = activeGame.getTile(AliasHandler.resolveTile(pl));
                    if (tile == null) {
                        continue;
                    }
                    UnitHolder unitHolder = tile.getUnitHolders().get(pl);
                    if (unitHolder != null && unitHolder.getTokenList() != null
                        && unitHolder.getTokenList().contains("attachment_tombofemphidia.png")) {

                        if (player.hasRelic("emphidia")) {
                            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                                player.getRepresentation()
                                    + "Reminder this is not the window to use Crown of Emphidia. You can purge crown of emphidia in the status homework phase, which is when buttons will appear");
                        } else {
                            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                                player.getRepresentation()
                                    + "Reminder this is the window to use Crown of Emphidia.");
                            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                                player.getRepresentation()
                                    + " You can use these buttons to resolve Crown of Emphidia",
                                ButtonHelper.getCrownButtons());
                        }

                    }
                }
            }

        }

        for (Player p2 : activeGame.getRealPlayers()) {
            String ms2 = TurnStart.getMissedSCFollowsText(activeGame, p2);
            if (ms2 != null && !"".equalsIgnoreCase(ms2)) {
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), ms2);
            }
        }

        String key2 = "queueToScorePOs";
        String key3 = "potentialScorePOBlockers";
        String key2b = "queueToScoreSOs";
        String key3b = "potentialScoreSOBlockers";

        activeGame.setStoredValue(key2, "");
        activeGame.setStoredValue(key3, "");
        activeGame.setStoredValue(key2b, "");
        activeGame.setStoredValue(key3b, "");
        if (activeGame.getHighestScore() + 4 > activeGame.getVp()) {
            activeGame.setStoredValue("forcedScoringOrder", "true");
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.danger("turnOffForcedScoring", "Turn off forced scoring order"));
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), activeGame.getPing() +
                "Players will be forced to score in order. Any preemptive scores will be queued. You can turn this off at any time by pressing this button", buttons);
            for (Player player : Helper.getInitativeOrder(activeGame)) {
                activeGame.setStoredValue(key3, activeGame.getStoredValue(key3) + player.getFaction() + "*");
                activeGame.setStoredValue(key3b, activeGame.getStoredValue(key3b) + player.getFaction() + "*");
            }
        }
        Player arborec = Helper.getPlayerFromAbility(activeGame, "mitosis");
        if (arborec != null) {
            String mitosisMessage = arborec.getRepresentation(true, true) + " reminder to do mitosis!";
            MessageHelper.sendMessageToChannelWithButtons(arborec.getCardsInfoThread(), mitosisMessage, ButtonHelperAbilities.getMitosisOptions(activeGame, arborec));
        }
        Player veldyr = Helper.getPlayerFromAbility(activeGame, "holding_company");
        if (veldyr != null) {
            ButtonHelperFactionSpecific.offerHoldingCompanyButtons(veldyr, activeGame);
        }
        Player solPlayer = Helper.getPlayerFromUnit(activeGame, "sol_flagship");

        if (solPlayer != null) {
            String colorID = Mapper.getColorID(solPlayer.getColor());
            UnitKey infKey = Mapper.getUnitKey("gf", colorID);
            for (Tile tile : activeGame.getTileMap().values()) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    if (unitHolder.getUnits() != null) {
                        if (unitHolder.getUnitCount(UnitType.Flagship, colorID) > 0) {
                            unitHolder.addUnit(infKey, 1);
                            String genesisMessage = solPlayer.getRepresentation(true, true)
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
