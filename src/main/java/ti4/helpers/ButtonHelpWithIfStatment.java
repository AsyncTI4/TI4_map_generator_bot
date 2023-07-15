package ti4.helpers;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import ti4.MapGenerator;
import ti4.MessageListener;
import ti4.buttons.ButtonListener;
import ti4.commands.agenda.PutAgendaBottom;
import ti4.commands.agenda.PutAgendaTop;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsac.ACInfo_Legacy;
import ti4.commands.cardsac.PlayAC;
import ti4.commands.cardsac.ShowAllAC;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.cardspn.ShowAllPN;
import ti4.commands.cardsso.DiscardSO;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.cardsso.ShowAllSO;
import ti4.commands.explore.ExpFrontier;
import ti4.commands.explore.ExpPlanet;
import ti4.commands.explore.SendFragments;
import ti4.commands.leaders.UnlockLeader;
import ti4.commands.planet.PlanetExhaust;
import ti4.commands.planet.PlanetExhaustAbility;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.player.SCPick;
import ti4.commands.player.SCPlay;
import ti4.commands.player.Stats;
import ti4.commands.special.KeleresHeroMentak;
import ti4.commands.special.RiseOfMessiah;
import ti4.commands.special.SleeperToken;
import ti4.commands.special.SwordsToPlowsharesTGGain;
import ti4.commands.status.Cleanup;
import ti4.commands.status.RevealStage1;
import ti4.commands.status.RevealStage2;
import ti4.commands.status.ScorePublic;
import ti4.commands.tokens.AddCC;
import ti4.commands.tokens.RemoveCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.GenerateMap;
import ti4.generator.GenerateTile;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.generator.UnitTokenPosition;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.PromissoryNoteModel;
import ti4.model.TechnologyModel;


public class ButtonHelpWithIfStatment {

    public static void doSomeOfTheIfStatement(Map activeMap, Player player, ButtonInteractionEvent event, String buttonID, String ident, String trueIdentity){
         HashMap<String, Set<Player>> playerUsedSC = new HashMap<>();
        event.deferEdit().queue();
        String id = event.getUser().getId();
        MessageListener.setActiveGame(event.getMessageChannel(), id, "button", "no sub command");
        String buttonLabel = event.getButton().getLabel();
        String lastchar = StringUtils.right(buttonLabel, 2).replace("#", "");
        String lastcharMod = StringUtils.right(buttonID, 1);
        if (buttonID == null) {
            event.getChannel().sendMessage("Button command not found").queue();
            return;
        }
        String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";

        // BotLogger.log(event, ""); //TEMPORARY LOG ALL BUTTONS

        String messageID = event.getMessage().getId();

        String gameName = event.getChannel().getName();
        gameName = gameName.replace(ACInfo_Legacy.CARDS_INFO, "");
        gameName = gameName.substring(0, gameName.indexOf("-"));
        player = Helper.getGamePlayer(activeMap, player, event.getMember(), id);
        if (player == null) {
            event.getChannel().sendMessage("You're not a player of the game").queue();
            return;
        }

        MessageChannel privateChannel = event.getChannel();
        if (activeMap.isFoWMode()) {
            if (player.getPrivateChannel() == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Private channels are not set up for this game. Messages will be suppressed.");
                privateChannel = null;
            } else {
                privateChannel = player.getPrivateChannel();
            }
        }

        MessageChannel mainGameChannel = event.getChannel();
        if (activeMap.getMainGameChannel() != null) {
            mainGameChannel = activeMap.getMainGameChannel();
        }

        MessageChannel actionsChannel = null;
        for (TextChannel textChannel_ : MapGenerator.jda.getTextChannels()) {
            if (textChannel_.getName().equals(gameName + Constants.ACTIONS_CHANNEL_SUFFIX)) {
                actionsChannel = textChannel_;
                break;
            }
        }
        if (buttonID.startsWith("SODISCARD_")) {
            String soID = buttonID.replace("SODISCARD_", "");
            MessageChannel channel = null;
            if (activeMap.isFoWMode()) {
                channel = privateChannel;
            } else if (activeMap.isCommunityMode() && activeMap.getMainGameChannel() != null) {
                channel = mainGameChannel;
            } else {
                channel = actionsChannel;
            }

            if (channel != null) {
                try {
                    int soIndex = Integer.parseInt(soID);
                    new DiscardSO().discardSO(event, player, soIndex, activeMap);
                    if (!activeMap.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), ident + " discarded an SO");
                    }
                } catch (Exception e) {
                    BotLogger.log(event, "Could not parse SO ID: " + soID, e);
                    event.getChannel().sendMessage("Could not parse SO ID: " + soID + " Please discard manually.")
                            .queue();
                    return;
                }
            } else {
                event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("get_so_score_buttons")) {
            String secretScoreMsg = "_ _\nClick a button below to score your Secret Objective";
            List<Button> soButtons = SOInfo.getUnscoredSecretObjectiveButtons(activeMap, player);
            if (soButtons != null && !soButtons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), secretScoreMsg, soButtons);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong. Please report to Fin");
            }
        } else if (buttonID.startsWith("get_so_discard_buttons")) {
            String secretScoreMsg = "_ _\nClick a button below to discard your Secret Objective";
            List<Button> soButtons = SOInfo.getUnscoredSecretObjectiveDiscardButtons(activeMap, player);
            if (soButtons != null && !soButtons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), secretScoreMsg, soButtons);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong. Please report to Fin");
            }
        } else if (buttonID.startsWith(Constants.PO_SCORING)) {
            String poID = buttonID.replace(Constants.PO_SCORING, "");
            try {
                int poIndex = Integer.parseInt(poID);
                ScorePublic.scorePO(event, privateChannel, activeMap, player, poIndex);
                new ButtonListener().addReaction(event, false, false, null, "");
            } catch (Exception e) {
                BotLogger.log(event, "Could not parse PO ID: " + poID, e);
                event.getChannel().sendMessage("Could not parse PO ID: " + poID + " Please Score manually.").queue();
                return;
            }
        } else if (buttonID.startsWith(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX)) {
            String faction = buttonID.replace(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX, "");
            if (!player.getSCs().contains(3)) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        "Only the player who played Politics can assign Speaker");
                return;
            }
            if (activeMap != null && !activeMap.isFoWMode()) {
                for (Player player_ : activeMap.getPlayers().values()) {
                    if (player_.getFaction().equals(faction)) {
                        activeMap.setSpeaker(player_.getUserID());
                        String message = Emojis.SpeakerToken + " Speaker assigned to: "
                                + Helper.getPlayerRepresentation(player_, activeMap);
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                    }
                }
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("reveal_stage")) {
            String lastC = StringUtils.right(buttonLabel, 1);
            if (lastC.equalsIgnoreCase("2")) {
                new RevealStage2().revealS2(event, event.getChannel());
            } else {
                new RevealStage1().revealS1(event, event.getChannel());
            }

            int playersWithSCs = 0;

            for (Player player2 : activeMap.getPlayers().values()) {

                if (playersWithSCs > 1) {
                    new Cleanup().runStatusCleanup(activeMap);
                    new ButtonListener().addReaction(event, false, true, "Running Status Cleanup. ", "Status Cleanup Run!");
                    playersWithSCs = -30;
                }
                if (player2.isRealPlayer()) {
                    if (player2.getSCs() != null && player2.getSCs().size() > 0
                            && !player2.getSCs().contains(Integer.valueOf(0))) {
                        playersWithSCs = playersWithSCs + 1;
                    }
                } else {
                    continue;
                }
                if (player2.hasLeader("naaluhero") && player2.getLeaderByID("naaluhero") != null
                        && !player2.getLeaderByID("naaluhero").isLocked()) {
                    MessageHelper.sendMessageToChannel((MessageChannel) player2.getCardsInfoThread(activeMap),
                            Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)
                                    + "Reminder this is the window to do Naalu Hero");
                }
                if (player2.getRelics() != null && player2.hasRelic("mawofworlds") && activeMap.isCustodiansScored()) {
                    MessageHelper.sendMessageToChannel((MessageChannel) player2.getCardsInfoThread(activeMap),
                            Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)
                                    + "Reminder this is the window to do Maw of Worlds");
                }
                if (player2.getRelics() != null && player2.hasRelic("emphidia")) {
                    for (String pl : player2.getPlanets()) {
                        Tile tile = activeMap.getTile(AliasHandler.resolveTile(pl));
                        if(tile == null){
                            continue;
                        }
                        UnitHolder unitHolder = tile.getUnitHolders().get(pl);
                        if (unitHolder.getTokenList() != null
                                && unitHolder.getTokenList().contains("attachment_tombofemphidia.png")) {
                            MessageHelper.sendMessageToChannel((MessageChannel) player2.getCardsInfoThread(activeMap),
                                    Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)
                                            + "Reminder this is the window to purge Crown of Emphidia if you want to. Command to make a new custom public for crown is /status po_add_custom public_name:");
                        }
                    }
                }
                if (player2.getActionCards() != null && player2.getActionCards().keySet().contains("summit")
                        && !activeMap.isCustodiansScored()) {
                    MessageHelper.sendMessageToChannel((MessageChannel) player2.getCardsInfoThread(activeMap),
                            Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)
                                    + "Reminder this is the window to do summit");
                }
                if (player2.getActionCards() != null && (player2.getActionCards().keySet().contains("investments")
                        && !activeMap.isCustodiansScored())) {
                    MessageHelper.sendMessageToChannel((MessageChannel) player2.getCardsInfoThread(activeMap),
                            Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)
                                    + "Reminder this is the window to do manipulate investments.");
                }
                if (player2.getActionCards() != null && player2.getActionCards().keySet().contains("stability")) {
                    MessageHelper.sendMessageToChannel((MessageChannel) player2.getCardsInfoThread(activeMap),
                            Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)
                                    + "Reminder this is the window to play political stability.");
                }
                for (String pn : player2.getPromissoryNotes().keySet()) {

                    if (!player2.ownsPromissoryNote("ce") && pn.equalsIgnoreCase("ce")) {
                        String cyberMessage = Helper.getPlayerRepresentation(player2, activeMap, event.getGuild(), true)
                                + " reminder to use cybernetic enhancements!";
                        MessageHelper.sendMessageToChannel((MessageChannel) player2.getCardsInfoThread(activeMap),
                                cyberMessage);
                    }
                }
            }

            String message2 = null;
            message2 = "Resolve status homework using the buttons. Only the Ready for [X] button is essential to hit, all others are optional. ";
            Button draw1AC = Button.success("draw_1_AC", "Draw 1 AC").withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
            Button getCCs = Button.success("redistributeCCButtons", "Redistribute, Gain, & Confirm CCs").withEmoji(Emoji.fromFormatted("🔺"));
            boolean custodiansTaken = activeMap.isCustodiansScored();
            Button passOnAbilities;

            if (custodiansTaken) {
                passOnAbilities = Button.danger("pass_on_abilities", "Ready For Agenda");
                message2 = message2
                        + " Ready for Agenda means you are done playing/passing on playing political stability, ancient burial sites, maw of worlds, Naalu hero, and crown of emphidia.";
            } else {
                passOnAbilities = Button.danger("pass_on_abilities", "Ready For Strategy Phase");
                message2 = message2
                        + " Ready for Strategy Phase means you are done playing/passing on playing political stability, summit, and manipulate investments. ";
            }
            List<Button> buttons = null;

            if (activeMap.isFoWMode()) {
                buttons = List.of(draw1AC, getCCs);
                message2 = "Resolve status homework using the buttons";
                for (Player p1 : activeMap.getPlayers().values()) {
                    if (p1 == null || p1.isDummy() || p1.getFaction() == null || p1.getPrivateChannel() == null) {
                        continue;
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(p1.getPrivateChannel(), message2, buttons);

                    }
                }
                buttons = List.of(passOnAbilities);
            } else {

                buttons = List.of(draw1AC, getCCs, passOnAbilities);
            }
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            event.getMessage().delete().queue();

        } else if (buttonID.startsWith("sc_follow_") && (!buttonID.contains("leadership"))
                && (!buttonID.contains("trade"))) {
            boolean used = new ButtonListener().addUsedSCPlayer(messageID, activeMap, player, event, "");

            if (!used) {
                ButtonHelper.resolveMuaatCommanderCheck(player, activeMap, event);
                String message = new ButtonListener().deductCC(player, event);
                int scnum = 1;
                boolean setstatus = true;
                try {
                    scnum = Integer.parseInt(lastcharMod);
                } catch (NumberFormatException e) {
                    setstatus = false;
                }
                if (setstatus) {
                    player.addFollowedSC(scnum);
                }
                new ButtonListener().addReaction(event, false, false, message, "");
            }
        } else if (buttonID.startsWith("sc_no_follow_")) {
            int scnum2 = 1;
            boolean setstatus = true;
            try {
                scnum2 = Integer.parseInt(lastcharMod);
            } catch (NumberFormatException e) {
                setstatus = false;
            }
            if (setstatus) {
                player.addFollowedSC(scnum2);
            }
            new ButtonListener().addReaction(event, false, false, "Not Following", "");
            Set<Player> players = playerUsedSC.get(messageID);
            if (players == null) {
                players = new HashSet<>();
            }
            players.remove(player);
            playerUsedSC.put(messageID, players);
        } else if (buttonID.startsWith(Constants.GENERIC_BUTTON_ID_PREFIX)) {
            new ButtonListener().addReaction(event, false, false, null, "");
        } else if (buttonID.startsWith("movedNExplored_")) {
            String bID = buttonID.replace("movedNExplored_", "");
            String[] info = bID.split("_");

            String message = "";

            new ExpPlanet().explorePlanet(event, Helper.getTileFromPlanet(info[1], activeMap), info[1], info[2], player,
                    false, activeMap, 1, false);

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("distant_suns_")) {
            String bID = buttonID.replace("distant_suns_", "");
            String[] info = bID.split("_");
            String message = "";
            if (info[0].equalsIgnoreCase("decline")) {
                message = "Rejected Distant Suns Ability";
                new ExpPlanet().explorePlanet(event, Helper.getTileFromPlanet(info[1], activeMap), info[1], info[2],
                        player, true, activeMap, 1, false);
            } else {
                message = "Exploring twice";
                new ExpPlanet().explorePlanet(event, Helper.getTileFromPlanet(info[1], activeMap), info[1], info[2],
                        player, true, activeMap, 2, false);
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("strategicAction_")) {
            int scNum = Integer.parseInt(buttonID.replace("strategicAction_", ""));
            new SCPlay().playSC(event, scNum, activeMap, mainGameChannel, player);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("resolve_explore_")) {
            String bID = buttonID.replace("resolve_explore_", "");
            String[] info = bID.split("_");
            String cardID = info[0];
            String planetName = info[1];
            Tile tile = Helper.getTileFromPlanet(planetName, activeMap);
            StringBuilder messageText = new StringBuilder();
            messageText.append(Helper.getPlayerRepresentation(player, activeMap)).append(" explored ");

            messageText.append("Planet " + Helper.getPlanetRepresentationPlusEmoji(planetName) + " *(tile "
                    + tile.getPosition() + ")*:\n");
            messageText.append("> ").append(new ExpPlanet().displayExplore(cardID));
            new ExpPlanet().resolveExplore(event, cardID, tile, planetName, messageText.toString(), false, player,
                    activeMap);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("refresh_")) {
            String planetName = buttonID.replace("refresh_", "");
            new PlanetRefresh().doAction(player, planetName, activeMap);
            List<ActionRow> actionRow2 = new ArrayList<>();
            for (ActionRow row : event.getMessage().getActionRows()) {
                List<ItemComponent> buttonRow = row.getComponents();
                int buttonIndex = buttonRow.indexOf(event.getButton());
                if (buttonIndex > -1) {
                    buttonRow.remove(buttonIndex);
                }
                if (buttonRow.size() > 0) {
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            String totalVotesSoFar = event.getMessage().getContentRaw();
            if (totalVotesSoFar.contains("Readied")) {
                totalVotesSoFar += ", "
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeMap);
            } else {
                totalVotesSoFar = ident + " Readied "
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeMap);
            }

            if (actionRow2.size() > 0) {
                event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
            }
        } else if (buttonID.startsWith("assignDamage_")) {

            String rest = "";
            rest = buttonID.replace("assignDamage_", "");
            
            String pos = rest.substring(0, rest.indexOf("_"));
            Tile tile = activeMap.getTileByPosition(pos);
            rest = rest.replace(pos + "_", "");
            int amount = Integer.parseInt(rest.charAt(0) + "");
            rest = rest.substring(1, rest.length());
            String unitkey = "";
            String planet = "";
            if (rest.contains("_")) {
                unitkey = rest.split("_")[0];
                planet = rest.split("_")[1].toLowerCase().replace(" ", "");
            } else {
                unitkey = rest;
            }
            String planetName = "";
            unitkey = unitkey.replace("damaged", "");
            planet = planet.replace("damaged", "");
            String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
            rest = rest.replace("damaged", "");
            if (planet.equalsIgnoreCase("")) {
                planetName = "space";
            } else {
                planetName = planet.toLowerCase().replace(" ", "");
                planetName = planet.replace("'", "");
                planetName = AliasHandler.resolvePlanet(planetName);
            }
            
            
            
            new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), amount, planetName, unitID, player.getColor(), false);
            
            
            String message = event.getMessage().getContentRaw();
            if(message.contains("Removed") || message.contains("Sustained")){
                message = message + "\n" + ident+" Sustained "+amount + " "+unitkey+" from "+planetName +" in tile "+tile.getRepresentationForButtons(activeMap, player);
            }else{
                message =  ident+ " Sustained "+amount + " "+unitkey+" from "+planetName+" in tile "+tile.getRepresentationForButtons(activeMap, player);
            }
            List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeMap, tile);
            event.getMessage().editMessage(message)
                        .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
            } else if (buttonID.startsWith("refreshViewOfSystem_")) {
                String pos = buttonID.replace("refreshViewOfSystem_", "");
                
                File systemWithContext = GenerateTile.getInstance().saveImage(activeMap, 0, pos, event);
                MessageHelper.sendMessageWithFile(event.getMessageChannel(), systemWithContext, "Picture of system", false);
                List<Button> buttons = new ArrayList<Button>();
                buttons.add(Button.danger("getDamageButtons_"+pos, "Assign Hits"));
                buttons.add(Button.primary("refreshViewOfSystem_"+pos, "Refresh Picture"));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "", buttons);
            } else if (buttonID.startsWith("getDamageButtons_")) {
                String pos = buttonID.replace("getDamageButtons_", "");
                List<Button> buttons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeMap, activeMap.getTileByPosition(pos));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), trueIdentity+" Use buttons to resolve", buttons);
            } else if (buttonID.startsWith("assignHits_")) {
            String rest = "";
            rest = buttonID.replace("assignHits_", "");
            String pos = rest.substring(0, rest.indexOf("_"));
            Tile tile = activeMap.getTileByPosition(pos);
            rest = rest.replace(pos + "_", "");

            if(rest.contains("All")){
                    java.util.Map<String, String> unitRepresentation = Mapper.getUnitImageSuffixes();
                    java.util.Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
                    String cID = Mapper.getColorID(player.getColor());
                    for (java.util.Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
                        String name = entry.getKey();
                        String representation = planetRepresentations.get(name);
                        if (representation == null){
                            representation = name;
                        }
                        UnitHolder unitHolder = entry.getValue();
                        HashMap<String, Integer> units1 = unitHolder.getUnits();
                        HashMap<String, Integer> units = new HashMap<String, Integer>();
                        units.putAll(units1);
                        if (unitHolder instanceof Planet planet) {
                            for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                                String key = unitEntry.getKey();
                                if (key.contains(cID)) {
                                    String unitKey = key.replace(cID+"_", "");
                                    unitKey = unitKey.replace(".png", "");
                                    unitKey = ButtonHelper.getUnitName(unitKey);
                                    int amount = unitEntry.getValue();
                                    rest = unitKey+"_"+unitHolder.getName();
                                    String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitKey), player.getColor());
                                    new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), unitEntry.getValue(), unitHolder.getName(), unitID, player.getColor(), false);

                                }
                            }
                        }
                        else{
                            for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                                String key = unitEntry.getKey();
                                for (String unitRepresentationKey : unitRepresentation.keySet()) {
                                    if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {                         
                                        String unitKey = key.replace(cID+"_", "");                    
                                        int totalUnits = unitEntry.getValue();
                                        int amount = unitEntry.getValue();
                                        unitKey  = unitKey.replace(".png", "");
                                        unitKey = ButtonHelper.getUnitName(unitKey);
                                        int damagedUnits = 0;
                                        if(unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null){
                                            damagedUnits = unitHolder.getUnitDamage().get(key);
                                        }
                                        String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitKey), player.getColor());
                                        new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), totalUnits, "space", unitID, player.getColor(), false);
                                    }
                                }         
                        }             
                    }
                }
                String message = trueIdentity + " Removed all units";
                List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeMap, tile);
                event.getMessage().editMessage(message)
                        .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
                return;
            }
            int amount = Integer.parseInt(rest.charAt(0) + "");
            rest = rest.substring(1, rest.length());
            String unitkey = "";
            String planet = "";
            if (rest.contains("_")) {
                unitkey = rest.split("_")[0];
                planet = rest.split("_")[1].toLowerCase().replace(" ", "");
            } else {
                unitkey = rest;
            }
            unitkey = unitkey.replace("damaged", "");
            planet = planet.replace("damaged", "");
            String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
            rest = rest.replace("damaged", "");
            String planetName = "";
            if (planet.equalsIgnoreCase("")) {
                planetName = "space";
            } else {
                planetName = planet.toLowerCase().replace(" ", "");
                planetName = planet.replace("'", "");
                planetName = AliasHandler.resolvePlanet(planetName);
            }
            if(buttonLabel.toLowerCase().contains("damaged")){
                new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), amount, planetName, unitID, player.getColor(), true);
            }else{
                new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), amount, planetName, unitID, player.getColor(), false);
            }
            
            String message = event.getMessage().getContentRaw();
            if(message.contains("Removed") || message.contains("Sustained")){
                message = message + "\n" + ident+" Removed "+amount + " "+unitkey+" from "+planetName +" in tile "+tile.getRepresentationForButtons(activeMap, player);
            }else{
                message =  ident+ " Removed "+amount + " "+unitkey+" from "+planetName+" in tile "+tile.getRepresentationForButtons(activeMap, player);

            }
            List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeMap, tile);
            event.getMessage().editMessage(message)
                        .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        } else if (buttonID.startsWith("biostimsReady_")) {
            ButtonHelper.bioStimsReady(activeMap, event, player, buttonID);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("refreshVotes_")) {
            String votes = buttonID.replace("refreshVotes_", "");
            List<Button> voteActionRow = Helper.getPlanetRefreshButtons(event, player, activeMap);
            Button concludeRefreshing = Button.danger(finsFactionCheckerPrefix + "votes_" + votes, "Done readying planets.");
            voteActionRow.add(concludeRefreshing);
            String voteMessage2 = "Use the buttons to ready planets. When you're done it will prompt the next person to vote.";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage2, voteActionRow);
            event.getMessage().delete().queue();
        }

        else if (buttonID.startsWith("getAllTechOfType_")) {
            String techType = buttonID.replace("getAllTechOfType_", "");
            List<TechnologyModel> techs = Helper.getAllTechOfAType(techType, player.getFaction(), player);
            List<Button> buttons = Helper.getTechButtons(techs, techType, player);

            String message = Helper.getPlayerRepresentation(player, activeMap) + " Use the buttons to get the tech you want";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
            
        } else if (buttonID.startsWith("getTech_")) {

            String tech = buttonID.replace("getTech_", "");
            String techFancy = buttonLabel;

            String message = ident + " Acquired The Tech " + Helper.getTechRepresentation(AliasHandler.resolveTech(tech));

            String message2 = trueIdentity + " Click the names of the planets you wish to exhaust. ";
            player.addTech(AliasHandler.resolveTech(tech));
            if(AliasHandler.resolveTech(tech).equalsIgnoreCase("iihq")){
                message = message + "\n Automatically added the Custodia Vigilia planet";
            }
            if(player.getLeaderIDs().contains("jolnarcommander") && !player.hasLeaderUnlocked("jolnarcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeMap, "jolnar", event);
            }
            List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeMap, player, event);
            if (player.hasTechReady("aida")) {
                        Button aiDEVButton = Button.danger("exhaustTech_aida", "Exhaust AIDEV");
                        buttons.add(aiDEVButton);
                    }
            Button DoneExhausting = Button.danger("deleteButtons_technology", "Done Exhausting Planets");
            buttons.add(DoneExhausting);
            


            if (activeMap.isFoWMode()) {
                MessageHelper.sendMessageToChannel(event.getChannel(), message);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            } else {

                String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-technology";
                
                List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                if (!activeMap.getComponentAction()) {
                    for (ThreadChannel threadChannel_ : threadChannels) {
                        if (threadChannel_.getName().equals(threadName)) {
                            MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, message);
                        }
                    }
                } else {
                    MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), message);
                }
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) player.getCardsInfoThread(activeMap),
                        message2, buttons);
            }

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("takeAC_")) {
            buttonID = buttonID.replace("takeAC_", "");

            int acNum = Integer.parseInt(buttonID.split("_")[0]);
            String faction2 = buttonID.split("_")[1];
            Player player2 = Helper.getPlayerFromColorOrFaction(activeMap, faction2);
            String ident2 = Helper.getFactionIconFromDiscord(player2.getFaction());
            String message2 = ident + " took AC #" + acNum + " from " + ident2;
            String acID = null;
            for (java.util.Map.Entry<String, Integer> so : player2.getActionCards().entrySet()) {
                if (so.getValue().equals(acNum)) {
                    acID = so.getKey();
                }
            }
            if (activeMap.isFoWMode()) {
                message2 = "Someone took AC #" + acNum + " from " + player2.getColor();
                MessageHelper.sendMessageToChannel(player.getPrivateChannel(), message2);
                MessageHelper.sendMessageToChannel(player2.getPrivateChannel(), message2);
            } else {
                MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), message2);
            }
            player2.removeActionCard(acNum);
            player.setActionCard(acID);
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(activeMap), "Acquired " + acID);
            MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(activeMap), "Lost " + acID);
            ACInfo.sendActionCardInfo(activeMap, player2);
            ACInfo.sendActionCardInfo(activeMap, player);

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("spend_")) {
            String planetName = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());

            new PlanetExhaust().doAction(player, planetName, activeMap);
            List<ActionRow> actionRow2 = new ArrayList<>();
            for (ActionRow row : event.getMessage().getActionRows()) {
                List<ItemComponent> buttonRow = row.getComponents();
                int buttonIndex = buttonRow.indexOf(event.getButton());
                if (buttonIndex > -1) {
                    buttonRow.remove(buttonIndex);
                }
                if (buttonRow.size() > 0) {
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            String exhaustedMessage = event.getMessage().getContentRaw();
            if (!exhaustedMessage.contains("Click the names")) {
                exhaustedMessage = exhaustedMessage + ", exhausted "
                        + Helper.getPlanetRepresentation(planetName, activeMap);
            } else {
                exhaustedMessage = ident + " exhausted "
                        + Helper.getPlanetRepresentation(planetName, activeMap);
            }
            event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
           

        } else if (buttonID.startsWith("finishTransaction_")) {
            String player2Color = buttonID.split("_")[1];
            Player player2 = Helper.getPlayerFromColorOrFaction(activeMap, player2Color);
            ButtonHelper.pillageCheck(player, activeMap);
            ButtonHelper.pillageCheck(player2, activeMap);
            event.getMessage().delete().queue();

        } else if (buttonID.startsWith("sabotage_")) {
            String typeNName = buttonID.replace("sabotage_", "");
            String type = typeNName.substring(0, typeNName.indexOf("_"));
            String acName = typeNName.replace(type + "_", "");
            String message = "Cancelling the AC \"" + acName + "\" using ";
            String addMessage = "An AC has been cancelled!";
            boolean sendReact = true;
            if (type.equalsIgnoreCase("empy")) {
                message = message + "a Watcher mech! The Watcher should be removed now by the owner.";
                event.getMessage().delete().queue();
            } else if (type.equalsIgnoreCase("xxcha")) {
                message = message
                        + "the \"Instinct Training\" tech! The tech has been exhausted and a strategy CC removed.";
                if (player.hasTech(AliasHandler.resolveTech("Instinct Training"))) {
                    player.exhaustTech(AliasHandler.resolveTech("Instinct Training"));
                    if (player.getStrategicCC() > 0) {
                        player.setStrategicCC(player.getStrategicCC() - 1);
                    }
                    event.getMessage().delete().queue();
                } else {
                    sendReact = false;
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                            "Someone clicked the Instinct Training button but did not have the tech.");
                }
            } else if (type.equalsIgnoreCase("ac")) {
                message = message + "A Sabotage!";
                boolean hasSabo = false;
                String saboID = "3";
                for (String AC : player.getActionCards().keySet()) {
                    if (AC.contains("sabo")) {
                        hasSabo = true;
                        saboID = "" + player.getActionCards().get(AC);
                        break;
                    }
                }
                if (hasSabo) {
                    PlayAC.playAC(event, activeMap, player, saboID, activeMap.getActionsChannel(),
                            activeMap.getGuild());
                } else {
                    addMessage = "";
                    message = "Tried to play a sabo but found none in hand.";
                    sendReact = false;
                    MessageHelper.sendMessageToChannel(activeMap.getActionsChannel(),
                            "Someone clicked the AC sabo button but did not have a sabo in hand.");
                }

            }

            if (sendReact) {
                MessageHelper.sendMessageToChannel(activeMap.getActionsChannel(),
                        message + "\n" + Helper.getGamePing(activeMap.getGuild(), activeMap) + addMessage);

            }

        } else if (buttonID.startsWith("reduceTG_")) {

            int tgLoss = Integer.parseInt(buttonID.replace("reduceTG_", ""));
            String message = ident + " reduced tgs by " + tgLoss + " (" + player.getTg() + "->"
                    + (player.getTg() - tgLoss) + ")";

            if (tgLoss > player.getTg()) {
                message = "You dont have " + tgLoss + " tgs. No change made.";
            } else {
                player.setTg(player.getTg() - tgLoss);
            }
            String editedMessage = event.getMessage().getContentRaw() + " " + message;

            if (editedMessage.contains("Click the names")) {
                editedMessage = message;
            }
            event.getMessage().editMessage(editedMessage).queue();

            // MessageHelper.sendMessageToChannel(event.getChannel(), message);
        } else if (buttonID.startsWith("reduceComm_")) {

            int tgLoss = Integer.parseInt(buttonID.replace("reduceComm_", ""));
            String message = ident + " reduced comms by " + tgLoss + " (" + player.getCommodities() + "->"
                    + (player.getCommodities() - tgLoss) + ")";

            if (tgLoss > player.getCommodities()) {
                message = "You dont have " + tgLoss + " comms. No change made.";
            } else {
                player.setCommodities(player.getCommodities() - tgLoss);
            }
            String editedMessage = event.getMessage().getContentRaw() + " " + message;

            if (editedMessage.contains("Click the names")) {
                editedMessage = message;
            }
            Leader playerLeader = player.getLeader("keleresagent");
		
		
                
            if(!playerLeader.isExhausted() ){
                playerLeader.setExhausted(true);
                StringBuilder messageText = new StringBuilder(Helper.getPlayerRepresentation(player, activeMap))
                        .append(" exhausted ").append(Helper.getLeaderFullRepresentation(playerLeader));
                if(activeMap.isFoWMode()){
                    MessageHelper.sendMessageToChannel(player.getPrivateChannel(), messageText.toString());
                }else{
                    MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), messageText.toString());
                }
            }
                    event.getMessage().editMessage(editedMessage).queue();

            // MessageHelper.sendMessageToChannel(event.getChannel(), message);
         } else if (buttonID.startsWith("pillage_")) {
            buttonID = buttonID.replace("pillage_", "");
            String colorPlayer = buttonID.split("_")[0];
            String checkedStatus = buttonID.split("_")[1];
            Player pillaged = Helper.getPlayerFromColorOrFaction(activeMap, colorPlayer);
            if(checkedStatus.contains("unchecked")){
                 List<Button> buttons = new ArrayList<Button>();
                String message2 =  "Please confirm this is a valid pillage opportunity and that you wish to pillage.";
                buttons.add(Button.danger(finsFactionCheckerPrefix+"pillage_"+pillaged.getColor()+"_checked","Pillage"));
                buttons.add(Button.success(finsFactionCheckerPrefix+"deleteButtons","Delete these buttons"));
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            }else{
                MessageChannel channel1 = activeMap.getMainGameChannel();
                MessageChannel channel2 = activeMap.getMainGameChannel();
                String pillagerMessage = Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true) + " you pillaged, your tgs have gone from "+player.getTg() +" to "+(player.getTg()+1) +".";
                String pillagedMessage = Helper.getPlayerRepresentation(pillaged, activeMap, activeMap.getGuild(), true) + " you have been pillaged";
                if(activeMap.isFoWMode()){
                    channel1 = pillaged.getPrivateChannel();
                    channel2 = player.getPrivateChannel();
                }
                if(pillaged.getCommodities()>0){
                    pillagedMessage = pillagedMessage+ ", your comms have gone from "+pillaged.getCommodities() +" to "+(pillaged.getCommodities()-1) +".";
                    pillaged.setCommodities(pillaged.getCommodities()-1);

                } else {
                    pillagedMessage = pillagedMessage+ ", your tgs have gone from "+pillaged.getTg() +" to "+(pillaged.getTg()-1) +".";
                    pillaged.setTg(pillaged.getTg()-1);
                }
                player.setTg(player.getTg()+1);
                MessageHelper.sendMessageToChannel(channel2, pillagerMessage);
                MessageHelper.sendMessageToChannel(channel1, pillagedMessage);
            }
            event.getMessage().delete().queue();

        } else if (buttonID.startsWith("exhaust_")) {
            String planetName = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            String votes = buttonLabel.substring(buttonLabel.indexOf("(") + 1, buttonLabel.indexOf(")"));
            if (!buttonID.contains("argent") && !buttonID.contains("blood") && !buttonID.contains("predictive")
                    && !buttonID.contains("everything")) {
                new PlanetExhaust().doAction(player, planetName, activeMap);
            }
            if (buttonID.contains("everything")) {
                for (String planet : player.getPlanets()) {
                    player.exhaustPlanet(planet);
                }
            }

            // List<ItemComponent> actionRow =
            // event.getMessage().getActionRows().get(0).getComponents();
            List<ActionRow> actionRow2 = new ArrayList<>();
            for (ActionRow row : event.getMessage().getActionRows()) {
                List<ItemComponent> buttonRow = row.getComponents();
                int buttonIndex = buttonRow.indexOf(event.getButton());
                if (buttonIndex > -1) {
                    buttonRow.remove(buttonIndex);
                }
                if (buttonRow.size() > 0) {
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            String totalVotesSoFar = event.getMessage().getContentRaw();
            if (!buttonID.contains("argent") && !buttonID.contains("blood") && !buttonID.contains("predictive")
                    && !buttonID.contains("everything")) {

                if (totalVotesSoFar == null || totalVotesSoFar.equalsIgnoreCase("")) {
                    totalVotesSoFar = "Total votes exhausted so far: " + votes + "\n Planets exhausted so far are: "
                            + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeMap);
                } else {
                    int totalVotes = Integer.parseInt(
                            totalVotesSoFar.substring(totalVotesSoFar.indexOf(":") + 2, totalVotesSoFar.indexOf("\n")))
                            + Integer.parseInt(votes);
                    totalVotesSoFar = totalVotesSoFar.substring(0, totalVotesSoFar.indexOf(":") + 2) + totalVotes
                            + totalVotesSoFar.substring(totalVotesSoFar.indexOf("\n"), totalVotesSoFar.length())
                            + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeMap);
                }
                if (actionRow2.size() > 0) {
                    event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
                }
                // addReaction(event, true, false,"Exhausted
                // "+Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName,
                // activeMap) + " as "+ votes + " votes", "");
            } else {
                if (totalVotesSoFar == null || totalVotesSoFar.equalsIgnoreCase("")) {
                    totalVotesSoFar = "Total votes exhausted so far: " + votes
                            + "\n Planets exhausted so far are: all planets";
                } else {
                    int totalVotes = Integer.parseInt(
                            totalVotesSoFar.substring(totalVotesSoFar.indexOf(":") + 2, totalVotesSoFar.indexOf("\n")))
                            + Integer.parseInt(votes);
                    totalVotesSoFar = totalVotesSoFar.substring(0, totalVotesSoFar.indexOf(":") + 2) + totalVotes
                            + totalVotesSoFar.substring(totalVotesSoFar.indexOf("\n"), totalVotesSoFar.length());
                }
                if (actionRow2.size() > 0) {
                    event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
                }
                if (buttonID.contains("everything")) {
                    // addReaction(event, true, false,"Exhausted
                    // "+Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName,
                    // activeMap) + " as "+ votes + " votes", "");
                    new ButtonListener().addReaction(event, true, false, "Exhausted all planets for " + votes + " votes", "");
                } else {
                    new ButtonListener().addReaction(event, true, false, "Used ability for " + votes + " votes", "");
                }
            }

        } else if (buttonID.startsWith("diplo_")) {
            String planet = buttonID.replace("diplo_", "");
            String tileID = AliasHandler.resolveTile(planet.toLowerCase());
            Tile tile = activeMap.getTile(tileID);
            if (tile == null) {
                tile = activeMap.getTileByPosition(tileID);
            }
            if (tile == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Could not resolve tileID:  `" + tileID + "`. Tile not found");
                return;
            }

            for (Player player_ : activeMap.getPlayers().values()) {
                if (player_ != player) {
                    String color = player_.getColor();
                    if (Mapper.isColorValid(color)) {
                        AddCC.addCC(event, color, tile);
                        Helper.isCCCountCorrect(event, activeMap, color);
                    }
                }
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), ident + " chose to diplo the system containing "
                    + Helper.getPlanetRepresentation(planet, activeMap));
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("doneWithOneSystem_")) {
            String pos = buttonID.replace("doneWithOneSystem_", "");
            Tile tile = activeMap.getTileByPosition(pos);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "From system "
                    + tile.getRepresentationForButtons(activeMap, player) + "\n" + event.getMessage().getContentRaw());
            String message = "Choose a different system to move from, or finalize movement.";
            activeMap.resetCurrentMovedUnitsFrom1System();
            List<Button> systemButtons = ButtonHelper.getTilesToMoveFrom(player, activeMap, event);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("delete_buttons_")) {
            boolean resolveTime = false;
            String winner = "";
            String votes = buttonID.substring(buttonID.lastIndexOf("_") + 1, buttonID.length());
            if (!buttonID.contains("outcomeTie*")) {
                if (votes.equalsIgnoreCase("0")) {

                    String pfaction2 = null;
                    if (player != null) {
                        pfaction2 = player.getFaction();
                    }
                    if (pfaction2 != null) {
                        new ButtonListener().addReaction(event, true, true, "Abstained.", "");
                        event.getMessage().delete().queue();
                    } 

                } else {
                    String identifier = "";
                    String outcome = activeMap.getLatestOutcomeVotedFor();
                    if (activeMap.isFoWMode()) {
                        identifier = player.getColor();
                    } else {
                        identifier = player.getFaction();
                    }
                    HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();
                    String existingData = outcomes.getOrDefault(outcome, "empty");
                    if (existingData.equalsIgnoreCase("empty")) {

                        existingData = identifier + "_" + votes;
                    } else {
                        existingData = existingData + ";" + identifier + "_" + votes;
                    }
                    activeMap.setCurrentAgendaVote(outcome, existingData);
                    new ButtonListener().addReaction(event, true, true,
                            "Voted " + votes + " votes for " + StringUtils.capitalize(outcome) + "!", "");
                }

                String pFaction1 = StringUtils.capitalize(player.getFaction());
                Button EraseVote = Button.danger("FFCC_" + pFaction1 + "_eraseMyVote",
                        pFaction1 + " Erase Any Of Your Previous Votes");
                List<Button> EraseButton = List.of(EraseVote);
                String mErase = "Use this button if you want to erase your previous votes and vote again";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), mErase, EraseButton);
                String message = " up to vote! Resolve using buttons.";

                Player nextInLine = AgendaHelper.getNextInLine(player, AgendaHelper.getVotingOrder(activeMap),
                        activeMap);
                String realIdentity2 = Helper.getPlayerRepresentation(nextInLine, activeMap, event.getGuild(), true);

                int[] voteInfo = AgendaHelper.getVoteTotal(event, nextInLine, activeMap);

                while (voteInfo[0] < 1 && !nextInLine.getColor().equalsIgnoreCase(player.getColor())) {
                    String skippedMessage = realIdentity2
                            + "You are being skipped because you either have 0 votes or have ridered";
                    if (activeMap.isFoWMode()) {
                        MessageHelper.sendPrivateMessageToPlayer(nextInLine, activeMap, skippedMessage);
                    } else {
                        MessageHelper.sendMessageToChannel(event.getChannel(), skippedMessage);
                    }
                    player = nextInLine;
                    nextInLine = AgendaHelper.getNextInLine(nextInLine, AgendaHelper.getVotingOrder(activeMap),
                            activeMap);
                    realIdentity2 = Helper.getPlayerRepresentation(nextInLine, activeMap, event.getGuild(), true);
                    voteInfo = AgendaHelper.getVoteTotal(event, nextInLine, activeMap);
                }

                if (!nextInLine.getColor().equalsIgnoreCase(player.getColor())) {
                    String realIdentity = "";
                    realIdentity = Helper.getPlayerRepresentation(nextInLine, activeMap, event.getGuild(), true);
                    String pFaction = StringUtils.capitalize(nextInLine.getFaction());
                    
                    Button Vote = Button.success("vote", pFaction + " Choose To Vote");
                    Button Abstain = Button.danger("delete_buttons_0", pFaction + " Choose To Abstain");
                    activeMap.updateActivePlayer(nextInLine);
                    List<Button> buttons = List.of(Vote, Abstain);
                    if (activeMap.isFoWMode()) {
                        if (nextInLine.getPrivateChannel() != null) {
                            MessageHelper.sendMessageToChannel(nextInLine.getPrivateChannel(),AgendaHelper.getSummaryOfVotes(activeMap, true) + "\n ");
                            MessageHelper.sendMessageToChannelWithButtons(nextInLine.getPrivateChannel(), "\n " + realIdentity+message,
                                    buttons);
                            event.getChannel().sendMessage("Notified next in line").queue();
                        }
                    } else {
                        message = AgendaHelper.getSummaryOfVotes(activeMap, true) + "\n \n " + realIdentity + message;
                        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
                    }
                } else {
                    String summary = AgendaHelper.getSummaryOfVotes(activeMap, false);
                    winner = AgendaHelper.getWinner(summary);
                    if (winner != null && !winner.contains("*")) {
                        resolveTime = true;
                    } else {
                        Player speaker = null;
                        if (activeMap.getPlayer(activeMap.getSpeaker()) != null) {
                            speaker = activeMap.getPlayers().get(activeMap.getSpeaker());
                        } else {
                            speaker = null;
                        }

                        List<Button> tiedWinners = new ArrayList<Button>();
                        if (winner != null) {
                            StringTokenizer winnerInfo = new StringTokenizer(winner, "*");
                            while (winnerInfo.hasMoreTokens()) {
                                String tiedWinner = winnerInfo.nextToken();
                                Button button = Button.primary("delete_buttons_outcomeTie* " + tiedWinner, tiedWinner);
                                tiedWinners.add(button);
                            }
                        } else {

                            tiedWinners = AgendaHelper.getAgendaButtons(null, activeMap, "delete_buttons_outcomeTie*");
                        }
                        if (!tiedWinners.isEmpty()) {
                            MessageChannel channel = null;
                            if (activeMap.isFoWMode()) {
                                channel = speaker == null ? null : speaker.getPrivateChannel();
                                if (channel == null) {
                                    MessageHelper.sendMessageToChannel(event.getChannel(),
                                            "Speaker is not assigned for some reason. Please decide the winner.");
                                }
                            } else {
                                channel = event.getChannel();
                            }
                            MessageHelper.sendMessageToChannelWithButtons(channel,
                                    Helper.getPlayerRepresentation(speaker, activeMap, event.getGuild(), true)
                                            + " please decide the winner.",
                                    tiedWinners);
                        }
                    }
                }
            } else {
                resolveTime = true;
                winner = buttonID.substring(buttonID.lastIndexOf("*") + 2, buttonID.length());
            }
            if (resolveTime) {
                List<Player> losers = AgendaHelper.getLosers(winner, activeMap);
                String summary2 = AgendaHelper.getSummaryOfVotes(activeMap, true);
                MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), summary2 + "\n \n");
                activeMap.setCurrentPhase("agendaEnd");
                activeMap.setActivePlayer(null);
                String resMessage = "Please hold while people resolve shenanigans. " + losers.size()
                        + " players have the opportunity to play deadly plot.";
                if ((!activeMap.isACInDiscard("Bribery") || !activeMap.isACInDiscard("Deadly Plot"))
                        && (losers.size() > 0 || activeMap.isAbsolMode())) {
                    Button noDeadly = Button.primary("genericReact1", "No Deadly Plot");
                    Button noBribery = Button.primary("genericReact2", "No Bribery");
                    List<Button> deadlyActionRow = List.of(noBribery, noDeadly);

                    MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(), resMessage,
                            deadlyActionRow);
                    if (!activeMap.isFoWMode()) {
                        String loseMessage = "";
                        for (Player los : losers) {
                            if (los != null) {
                                loseMessage = loseMessage
                                        + Helper.getPlayerRepresentation(los, activeMap, event.getGuild(), true);
                            }
                        }
                        event.getChannel().sendMessage(loseMessage + " Please respond to bribery/deadly plot window")
                                .queue();
                    } else {
                        MessageHelper.privatelyPingPlayerList(losers, activeMap,
                                "Please respond to bribery/deadly plot window");
                    }
                } else {
                    String messageShen = "Either both bribery and deadly plot were in the discard or noone could legally play them.";
                    
                    if (activeMap.getCurrentAgendaInfo().contains("Elect Player")&& (!activeMap.isACInDiscard("Confounding") || !activeMap.isACInDiscard("Confusing"))){

                    } else{
                        messageShen = messageShen + " There are no shenanigans possible. Please resolve the agenda. ";
                    }
                    activeMap.getMainGameChannel().sendMessage(messageShen).queue();
                }
                if (activeMap.getCurrentAgendaInfo().contains("Elect Player")
                        && (!activeMap.isACInDiscard("Confounding") || !activeMap.isACInDiscard("Confusing"))) {
                    String resMessage2 = Helper.getGamePing(activeMap.getGuild(), activeMap)
                            + " please react to no confusing/confounding";
                    Button noConfounding = Button.primary("genericReact3", "Refuse Confounding Legal Text");
                    Button noConfusing = Button.primary("genericReact4", "Refuse Confusing Legal Text");
                    List<Button> buttons = List.of(noConfounding, noConfusing);
                    MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(), resMessage2, buttons);

                } else {
                    if (activeMap.getCurrentAgendaInfo().contains("Elect Player")) {
                        activeMap.getMainGameChannel()
                                .sendMessage("Both confounding and confusing are in the discard pile. ").queue();

                    }
                }

                String resMessage3 = "Current winner is " + StringUtils.capitalize(winner) + ". "
                        + Helper.getGamePing(activeMap.getGuild(), activeMap)
                        + "When shenanigans have concluded, please confirm resolution or discard the result and manually resolve it yourselves.";
                Button autoResolve = Button.primary("agendaResolution_" + winner, "Resolve with current winner");
                Button manualResolve = Button.danger("autoresolve_manual", "Resolve it Manually");
                List<Button> deadlyActionRow3 = List.of(autoResolve, manualResolve);
                MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(), resMessage3,
                        deadlyActionRow3);

            }
            if (!votes.equalsIgnoreCase("0")) {
                event.getMessage().delete().queue();
            }
            MapSaveLoadManager.saveMap(activeMap, event);

        } else if (buttonID.startsWith("fixerVotes_")) {
            String voteMessage = "Thank you for specifying, please select from the available buttons to vote.";
            String vote = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            int votes = Integer.parseInt(vote);
            List<Button> voteActionRow = AgendaHelper.getVoteButtons(votes - 9, votes);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("exhaustTech_")) {
            String tech = buttonID.replace("exhaustTech_", "");
            if (!tech.equals("st")) {
                if(tech.equals("bs")){
                    ButtonHelper.sendAllTechsNTechSkipPlanetsToReady(activeMap, event, player);
                }
                if(tech.equals("aida") || tech.equals("sar")){
                    if(!activeMap.isFoWMode() && event.getMessageChannel() != activeMap.getActionsChannel()){
                       String msg =  " exhausted tech: " +Helper.getTechRepresentation(tech); 
                        String exhaustedMessage = event.getMessage().getContentRaw();
                        List<ActionRow> actionRow2 = new ArrayList<>();
                        for (ActionRow row : event.getMessage().getActionRows()) {
                            List<ItemComponent> buttonRow = row.getComponents();
                            int buttonIndex = buttonRow.indexOf(event.getButton());
                            if (buttonIndex > -1) {
                                buttonRow.remove(buttonIndex);
                            }
                            if (buttonRow.size() > 0) {
                                actionRow2.add(ActionRow.of(buttonRow));
                            }
                        }
                        if (!exhaustedMessage.contains("Click the names")) {
                            exhaustedMessage = exhaustedMessage + ", "+msg;
                        } else {
                            exhaustedMessage = ident + msg;
                        }
                        event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
                    }
                }
                player.exhaustTech(tech);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        (Helper.getPlayerRepresentation(player, activeMap) + " exhausted tech: "
                                + Helper.getTechRepresentation(tech)));
                if(tech.equals("pi")){
                    List<Button> redistributeButton = new ArrayList<Button>();
                    Button redistribute = Button.success("redistributeCCButtons", "Redistribute CCs");
                    Button deleButton= Button.danger("FFCC_"+player.getFaction()+"_"+"deleteButtons", "Delete These Buttons");
                    redistributeButton.add(redistribute);
                    redistributeButton.add(deleButton);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), false) +" use buttons to redistribute", redistributeButton);
                }
            } else {
                String msg =   " used tech: "
                                + Helper.getTechRepresentation(tech);
                    String exhaustedMessage = event.getMessage().getContentRaw();
                     List<ActionRow> actionRow2 = new ArrayList<>();
                    for (ActionRow row : event.getMessage().getActionRows()) {
                        List<ItemComponent> buttonRow = row.getComponents();
                        int buttonIndex = buttonRow.indexOf(event.getButton());
                        if (buttonIndex > -1) {
                            buttonRow.remove(buttonIndex);
                        }
                        if (buttonRow.size() > 0) {
                            actionRow2.add(ActionRow.of(buttonRow));
                        }
                    }
                    if (!exhaustedMessage.contains("Click the names")) {
                        exhaustedMessage = exhaustedMessage + ", "+msg;
                    } else {
                        exhaustedMessage = ident + msg;
                    }
                    event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
            }
        } else if (buttonID.startsWith("planetOutcomes_")) {
            String factionOrColor = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            Player planetOwner = Helper.getPlayerFromColorOrFaction(activeMap, factionOrColor);
            String voteMessage = "Chose to vote for one of " + factionOrColor
                    + "'s planets. Click buttons for which outcome to vote for.";
            List<Button> outcomeActionRow = null;
            outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, activeMap, "outcome", null);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("tiedPlanets_")) {
            buttonID = buttonID.replace("tiedPlanets_", "");
            buttonID = buttonID.replace("delete_buttons_outcomeTie*_", "");
            String factionOrColor = buttonID.substring(0, buttonID.length());
            Player planetOwner = Helper.getPlayerFromColorOrFaction(activeMap, factionOrColor);
            String voteMessage = "Chose to break tie for one of " + factionOrColor
                    + "'s planets. Use buttons to select which one.";
            List<Button> outcomeActionRow = null;
            outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, activeMap,
                    "delete_buttons_outcomeTie*", null);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
            event.getMessage().delete().queue();
        }

        else if (buttonID.startsWith("planetRider_")) {
            buttonID = buttonID.replace("planetRider_", "");
            String factionOrColor = buttonID.substring(0, buttonID.indexOf("_"));
            Player planetOwner = Helper.getPlayerFromColorOrFaction(activeMap, factionOrColor);
            String voteMessage = "Chose to rider for one of " + factionOrColor
                    + "'s planets. Use buttons to select which one.";
            List<Button> outcomeActionRow = null;
            buttonID = buttonID.replace(factionOrColor + "_", "");
            outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, activeMap,
                    finsFactionCheckerPrefix, buttonID);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
            event.getMessage().delete().queue();
        }

        else if (buttonID.startsWith("distinguished_")) {
            String voteMessage = "You added 5 votes to your total. Please select from the available buttons to vote.";
            String vote = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            int votes = Integer.parseInt(vote);
            List<Button> voteActionRow = AgendaHelper.getVoteButtons(votes, votes + 5);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("outcome_")) {
            String outcome = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            String voteMessage = "Chose to vote for " + StringUtils.capitalize(outcome)
                    + ". Click buttons for amount of votes";
            activeMap.setLatestOutcomeVotedFor(outcome);

            int[] voteArray = AgendaHelper.getVoteTotal(event, player, activeMap);

            int minvote = 1;
            if (player.hasAbility("zeal")) {
                int numPlayers = 0;
                for (Player player_ : activeMap.getPlayers().values()) {
                    if (player_.isRealPlayer())
                        numPlayers++;
                }
                minvote = minvote + numPlayers;
                
            }
            if (activeMap.getLaws() != null && (activeMap.getLaws().keySet().contains("rep_govt") || activeMap.getLaws().keySet().contains("absol_government"))) {
                    minvote = 1;
                    voteArray[0] = 1;
            }
            if (voteArray[0] - minvote > 20) {
                voteMessage = "Chose to vote for " + StringUtils.capitalize(outcome)
                        + ". You have more votes than discord has buttons. Please further specify your desired vote count by clicking the button which contains your desired vote amount (or largest button).";
            }
            List<Button> voteActionRow = AgendaHelper.getVoteButtons(minvote, voteArray[0]);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("votes_")) {
            String votes = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            String voteMessage = "Chose to vote  " + votes + " votes for "
                    + StringUtils.capitalize(activeMap.getLatestOutcomeVotedFor())
                    + ". Click buttons to choose which planets to exhaust for votes";

            List<Button> voteActionRow = AgendaHelper.getPlanetButtons(event, player, activeMap);
            int allVotes = AgendaHelper.getVoteTotal(event, player, activeMap)[0];
            Button exhausteverything = Button.danger("exhaust_everything_" + allVotes,
                    "Exhaust everything (" + allVotes + ")");
            Button concludeExhausting = Button.danger(finsFactionCheckerPrefix + "delete_buttons_" + votes,
                    "Done exhausting planets.");
            Button OopsMistake = Button.success("refreshVotes_" + votes,
                    "I made a mistake and want to ready some planets");
            voteActionRow.add(exhausteverything);
            voteActionRow.add(concludeExhausting);
            voteActionRow.add(OopsMistake);
            String voteMessage2 = "";
            MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);

            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage2, voteActionRow);

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("autoresolve_")) {
            String result = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            if (result.equalsIgnoreCase("manual")) {
                String resMessage3 = "Please select the winner.";
                List<Button> deadlyActionRow3 = AgendaHelper.getAgendaButtons(null, activeMap, "agendaResolution");
                deadlyActionRow3.add(Button.danger("deleteButtons", "Resolve with no result"));
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), resMessage3, deadlyActionRow3);
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("deleteButtons")) {
            buttonID = buttonID.replace("deleteButtons_", "");
            String editedMessage = event.getMessage().getContentRaw();

            if ((buttonLabel.equalsIgnoreCase("Done Gaining CCs")
                    || buttonLabel.equalsIgnoreCase("Done Redistributing CCs")) && editedMessage.contains("CCs have gone from") ) {

                String playerRep = Helper.getPlayerRepresentation(player, activeMap);
                String finalCCs = player.getTacticalCC() + "/" + player.getFleetCC() + "/" + player.getStrategicCC();
                String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "), editedMessage.length());
                shortCCs = shortCCs.replace("CCs have gone from ", "");
                shortCCs = shortCCs.substring(0,shortCCs.indexOf(" "));
                if (event.getMessage().getContentRaw().contains("Net gain")) {
                    
                    int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                    finalCCs = finalCCs + ". Net CC gain was " + netGain;
                }
                if (!activeMap.isFoWMode()) {
                    if (buttonLabel.equalsIgnoreCase("Done Redistributing CCs")) {
                        MessageHelper.sendMessageToChannel(actionsChannel,
                                playerRep + " Initial CCs were "+shortCCs+". Final CC Allocation Is " + finalCCs);
                    } else {
                        if (buttonID.equalsIgnoreCase("leadership")) {
                            String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-leadership";
                            List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                            for (ThreadChannel threadChannel_ : threadChannels) {
                                if (threadChannel_.getName().equals(threadName)) {
                                    MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_,
                                            playerRep + " Initial CCs were "+shortCCs+". Final CC Allocation Is " + finalCCs);
                                }
                            }

                        } else {
                            MessageHelper.sendMessageToChannel(actionsChannel,
                                    playerRep + " Final CC Allocation Is " + finalCCs);
                        }

                    }

                } else {
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                            playerRep + " Final CC Allocation Is " + finalCCs);
                }

            }
            if ((buttonLabel.equalsIgnoreCase("Done Exhausting Planets")
                    || buttonLabel.equalsIgnoreCase("Done Producing Units"))
                    && !event.getMessage().getContentRaw().contains("Click the names of the planets you wish")) {
                
                if (activeMap.isFoWMode()) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), editedMessage);
                } else {
                    if (buttonID.equalsIgnoreCase("warfare")) {
                        String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-warfare";
                        List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().equals(threadName)) {
                                MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, editedMessage);
                            }
                        }
                    } else if (buttonID.equalsIgnoreCase("leadership")) {
                        String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-leadership";
                        List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().equals(threadName)) {
                                MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, editedMessage);
                            }
                        }

                    } else if (buttonID.equalsIgnoreCase("construction")) {
                        String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-construction";
                        List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().equals(threadName)) {
                                MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, editedMessage);
                            }
                        }
                    } else if (buttonID.equalsIgnoreCase("technology")) {
                        String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-technology";
                        List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();

                        if (!activeMap.getComponentAction()) {
                            for (ThreadChannel threadChannel_ : threadChannels) {
                                if (threadChannel_.getName().equals(threadName)) {
                                    MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, editedMessage);
                                }
                            }
                        } else {
                            MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), editedMessage);

                        }
                    } else {
                        MessageHelper.sendMessageToChannel(event.getChannel(), editedMessage);
                    }
                }

                if (buttonLabel.equalsIgnoreCase("Done Producing Units")) {
                    String message2 = trueIdentity + " Click the names of the planets you wish to exhaust.";

                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeMap, player, event);
                    if (player.hasTechReady("sar")) {
                        Button sar = Button.danger("exhaustTech_sar", "Exhaust Self Assembly Routines");
                        buttons.add(sar);
                    }
                    if (activeMap.playerHasLeaderUnlockedOrAlliance(player, "titanscommander")) {
                        Button sar2 = Button.success("titansCommanderUsage", "Use Titans Commander To Gain a TG");
                        buttons.add(sar2);
                    }
                    if (player.hasTechReady("aida")) {
                        Button aiDEVButton = Button.danger("exhaustTech_aida", "Exhaust AIDEV");
                        buttons.add(aiDEVButton);
                    }
                    if (player.hasTechReady("st")) {
                        Button sarweenButton = Button.danger("exhaustTech_st", "Use Sarween");
                        buttons.add(sarweenButton);
                    }
                    Button DoneExhausting = null;
                    if (!buttonID.contains("deleteButtons")) {
                        DoneExhausting = Button.danger("deleteButtons_" + buttonID, "Done Exhausting Planets");
                    } else {
                        DoneExhausting = Button.danger("deleteButtons", "Done Exhausting Planets");
                    }
                    ButtonHelper.updateMap(activeMap, event);
                    buttons.add(DoneExhausting);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);

                }
            }
            if (buttonLabel.equalsIgnoreCase("Done Exhausting Planets")) {
                if (buttonID.contains("tacticalAction")) {
                    ButtonHelper.exploreDET(player, activeMap, event);
                    String message = "Use buttons to end turn or do another action.";
                    List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(player, activeMap, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                }
            }

            if (buttonID.equalsIgnoreCase("diplomacy")) {
                

                if (!activeMap.isFoWMode()) {
                    String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-diplomacy";
                    List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                    for (ThreadChannel threadChannel_ : threadChannels) {
                        if (threadChannel_.getName().equals(threadName)) {
                            MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, editedMessage);
                        }
                    }
                } else {
                    MessageHelper.sendMessageToChannel(event.getChannel(), editedMessage);
                }
            }

            event.getMessage().delete().queue();
        }

        else if (buttonID.startsWith("reverse_")) {
            String choice = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            String voteMessage = "Chose to reverse latest rider on " + choice;

            HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();
            String existingData = outcomes.getOrDefault(choice, "empty");
            if (existingData.equalsIgnoreCase("empty")) {
                voteMessage = "Something went wrong. Ping Fin and continue onwards.";
            } else {
                int lastSemicolon = existingData.lastIndexOf(";");
                if (lastSemicolon < 0) {
                    existingData = "";
                } else {
                    existingData = existingData.substring(0, lastSemicolon);
                }

            }
            activeMap.setCurrentAgendaVote(choice, existingData);
            event.getChannel().sendMessage(voteMessage).queue();
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("rider_")) {
            String[] choiceParams = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.lastIndexOf("_")).split(";");
            String choiceType = choiceParams[0];
            String choice = choiceParams[1];

            String rider = buttonID.substring(buttonID.lastIndexOf("_") + 1, buttonID.length());
            String agendaDetails = activeMap.getCurrentAgendaInfo();
            agendaDetails = agendaDetails.substring(agendaDetails.indexOf("_")+1, agendaDetails.length());
           // if(activeMap)
           String cleanedChoice = choice;
           if(agendaDetails.contains("Planet") || agendaDetails.contains("planet")){
                cleanedChoice = Helper.getPlanetRepresentation(choice, activeMap);
           }
            String voteMessage = "Chose to put a " + rider + " on " + StringUtils.capitalize(cleanedChoice);
            String identifier = "";
            if (activeMap.isFoWMode()) {
                identifier = player.getColor();
            } else {
                identifier = player.getFaction();
            }
            HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();
            String existingData = outcomes.getOrDefault(choice, "empty");
            if (existingData.equalsIgnoreCase("empty")) {
                existingData = identifier + "_" + rider;
            } else {
                existingData = existingData + ";" + identifier + "_" + rider;
            }
            activeMap.setCurrentAgendaVote(choice, existingData);

            if (!rider.equalsIgnoreCase("Non-AC Rider") && !rider.equalsIgnoreCase("Keleres Rider")
                    && !rider.equalsIgnoreCase("Keleres Xxcha Hero")
                    && !rider.equalsIgnoreCase("Galactic Threat Rider")) {
                List<Button> voteActionRow = new ArrayList<Button>();
                Button concludeExhausting = Button.danger("reverse_" + choice, "Click this if the rider is sabod");
                voteActionRow.add(concludeExhausting);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);
            }

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("construction_")) {
            player.addFollowedSC(4);
            new ButtonListener().addReaction(event, false, false, "", "");
            String unit = buttonID.replace("construction_", "");
            String message = trueIdentity + " Click the name of the planet you wish to put your unit on";

            List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, activeMap, unit, "place");
            if (!activeMap.isFoWMode()) {
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) player.getCardsInfoThread(activeMap),
                        message, buttons);
            } else {
                MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
            }

        } else if (buttonID.startsWith("produceOneUnitInTile_")) {
            buttonID = buttonID.replace("produceOneUnitInTile_", "");
            String type = buttonID.split("_")[1];
            String pos = buttonID.split("_")[0];
            List<Button> buttons = new ArrayList<Button>();
            buttons = Helper.getPlaceUnitButtons(event, player, activeMap,
                    activeMap.getTileByPosition(pos), type, "placeOneNDone_dontskip");
            String message = Helper.getPlayerRepresentation(player, activeMap) + " Use the buttons to produce 1 unit. "
                    + ButtonHelper.getListOfStuffAvailableToSpend(player, activeMap);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("genericReact")) {
            String message = activeMap.isFoWMode() ? "Turned down window" : null;
            new ButtonListener().addReaction(event, false, false, message, "");
        } else if (buttonID.startsWith("placeOneNDone_")) {
            String unitNPlanet = buttonID.replace("placeOneNDone_", "");
            String skipbuild= unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
            unitNPlanet = unitNPlanet.replace(skipbuild+"_","");
            String unitLong = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
            String planetName = unitNPlanet.replace(unitLong + "_", "");
            String unit = AliasHandler.resolveUnit(unitLong);
            String producedOrPlaced = "Produced";
             if(skipbuild.equalsIgnoreCase("skipbuild")){
                producedOrPlaced = "Placed";
            }
            String successMessage = "";
            String playerRep = Helper.getPlayerRepresentation(player, activeMap);
            if (unit.equalsIgnoreCase("sd")) {
                if (player.ownsUnit("saar_spacedock") || player.ownsUnit("saar_spacedock2")) {
                    new AddUnits().unitParsing(event, player.getColor(),
                            activeMap.getTile(AliasHandler.resolveTile(planetName)), unit, activeMap);
                    successMessage = "Placed a space dock in the space area of the "
                            + Helper.getPlanetRepresentation(planetName, activeMap) + " system.";
                } else if (player.ownsUnit("cabal_spacedock") || player.ownsUnit("cabal_spacedock2")) {
                    new AddUnits().unitParsing(event, player.getColor(),
                            activeMap.getTile(AliasHandler.resolveTile(planetName)), "csd " + planetName, activeMap);
                    successMessage = "Placed a cabal space dock on "
                            + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                } else {
                    new AddUnits().unitParsing(event, player.getColor(),
                            activeMap.getTile(AliasHandler.resolveTile(planetName)), unit + " " + planetName,
                            activeMap);
                    successMessage = "Placed a " + Helper.getEmojiFromDiscord("spacedock") + " on "
                            + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                }
            } else if (unitLong.equalsIgnoreCase("pds")) {
                new AddUnits().unitParsing(event, player.getColor(),
                        activeMap.getTile(AliasHandler.resolveTile(planetName)), unitLong + " " + planetName,
                        activeMap);
                successMessage = "Placed a " + Helper.getEmojiFromDiscord("pds") + " on "
                        + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
            } else {
                Tile tile = null;
                if (unit.equalsIgnoreCase("gf") || unit.equalsIgnoreCase("mf") || unitLong.equalsIgnoreCase("2gf")) {
                    if (unitLong.equalsIgnoreCase("2gf")) {
                        if(!planetName.contains("space")){
                            new AddUnits().unitParsing(event, player.getColor(),
                                activeMap.getTile(AliasHandler.resolveTile(planetName)), "2 gf " + planetName,
                                activeMap);
                            successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("infantry") + " on "+ Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                        }else {
                            tile = activeMap.getTileByPosition(planetName.replace("space",""));
                            new AddUnits().unitParsing(event, player.getColor(),
                                tile, "2 gf ",
                                activeMap);
                            successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("infantry") + " in space.";
                        }
                    } else {
                       
                        if(!planetName.contains("space")){
                            tile = activeMap.getTile(AliasHandler.resolveTile(planetName));
                            new AddUnits().unitParsing(event, player.getColor(),
                                tile, unit + " " + planetName,
                                activeMap);
                            successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " on "+ Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                        } else {
                            tile = activeMap.getTileByPosition(planetName.replace("space",""));
                            new AddUnits().unitParsing(event, player.getColor(),
                                tile, unit,
                                activeMap);
                            successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " in space.";
                        }
                        
                    }
                } else {
                    if (unitLong.equalsIgnoreCase("2ff")) {
                        new AddUnits().unitParsing(event, player.getColor(), activeMap.getTileByPosition(planetName),
                                "2 ff", activeMap);
                        successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("fighter") + " in tile "
                                + AliasHandler.resolveTile(planetName) + ".";
                    } else {
                        new AddUnits().unitParsing(event, player.getColor(), activeMap.getTileByPosition(planetName),
                                unit, activeMap);
                        successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " in tile "
                                + AliasHandler.resolveTile(planetName) + ".";
                    }

                }
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), playerRep + " " + successMessage);

            String message2 = trueIdentity + " Click the names of the planets you wish to exhaust.";

            List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeMap, player, event);


            Button DoneExhausting = null;
            if (!buttonID.contains("deleteButtons")) {
                DoneExhausting = Button.danger("deleteButtons_" + buttonID, "Done Exhausting Planets");
            } else {
                DoneExhausting = Button.danger("deleteButtons", "Done Exhausting Planets");
            }
            buttons.add(DoneExhausting);
            if(!skipbuild.equalsIgnoreCase("skipbuild")){
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            }
            

            event.getMessage().delete().queue();

        } else if (buttonID.startsWith("place_")) {
            String unitNPlanet = buttonID.replace("place_", "");
            String unitLong = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
            String planetName = unitNPlanet.replace(unitLong + "_", "");
            String unit = AliasHandler.resolveUnit(unitLong);

            String successMessage = "";
            String playerRep = Helper.getPlayerRepresentation(player, activeMap);
            if (unit.equalsIgnoreCase("sd")) {
                if (player.ownsUnit("saar_spacedock") || player.ownsUnit("saar_spacedock2")) {
                    new AddUnits().unitParsing(event, player.getColor(),
                            activeMap.getTile(AliasHandler.resolveTile(planetName)), unit, activeMap);
                    successMessage = "Placed a space dock in the space area of the "
                            + Helper.getPlanetRepresentation(planetName, activeMap) + " system.";
                } else if (player.ownsUnit("cabal_spacedock") || player.ownsUnit("cabal_spacedock2")) {
                    new AddUnits().unitParsing(event, player.getColor(),
                            activeMap.getTile(AliasHandler.resolveTile(planetName)), "csd " + planetName, activeMap);
                    successMessage = "Placed a cabal space dock on "
                            + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                } else {
                    new AddUnits().unitParsing(event, player.getColor(),
                            activeMap.getTile(AliasHandler.resolveTile(planetName)), unit + " " + planetName,
                            activeMap);
                    successMessage = "Placed a " + Helper.getEmojiFromDiscord("spacedock") + " on "
                            + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                }
            } else if (unitLong.equalsIgnoreCase("pds")) {
                new AddUnits().unitParsing(event, player.getColor(),
                        activeMap.getTile(AliasHandler.resolveTile(planetName)), unitLong + " " + planetName,
                        activeMap);
                successMessage = "Placed a " + Helper.getEmojiFromDiscord("pds") + " on "
                        + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
            } else {
                 Tile tile = null;
                 String producedOrPlaced = "Produced";
                if (unit.equalsIgnoreCase("gf") || unit.equalsIgnoreCase("mf") || unitLong.equalsIgnoreCase("2gf")) {
                    if (unitLong.equalsIgnoreCase("2gf")) {
                        if(!planetName.contains("space")){
                            new AddUnits().unitParsing(event, player.getColor(),
                                activeMap.getTile(AliasHandler.resolveTile(planetName)), "2 gf " + planetName,
                                activeMap);
                            successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("infantry") + " on "+ Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                        }else {
                            tile = activeMap.getTileByPosition(planetName.replace("space",""));
                            new AddUnits().unitParsing(event, player.getColor(),
                                tile, "2 gf",
                                activeMap);
                            successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("infantry") + " in space.";
                        }
                    } else {
                       
                        if(!planetName.contains("space")){
                            tile = activeMap.getTile(AliasHandler.resolveTile(planetName));
                            new AddUnits().unitParsing(event, player.getColor(),
                                tile, unit + " " + planetName,
                                activeMap);
                            successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " on "+ Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                        } else {
                            tile = activeMap.getTileByPosition(planetName.replace("space",""));
                            new AddUnits().unitParsing(event, player.getColor(),
                                tile, unit,
                                activeMap);
                            successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " in space.";
                        }
                        
                    }
                } else {
                    if (unitLong.equalsIgnoreCase("2ff")) {
                        new AddUnits().unitParsing(event, player.getColor(), activeMap.getTileByPosition(planetName),
                                "2 ff", activeMap);
                        successMessage = "Produced 2 " + Helper.getEmojiFromDiscord("fighter") + " in tile "
                                + AliasHandler.resolveTile(planetName) + ".";
                    } else {
                        new AddUnits().unitParsing(event, player.getColor(), activeMap.getTileByPosition(planetName),
                                unit, activeMap);
                        successMessage = "Produced a " + Helper.getEmojiFromDiscord(unitLong) + " in tile "
                                + AliasHandler.resolveTile(planetName) + ".";
                    }

                }
            }
            if (unit.equalsIgnoreCase("sd") || unitLong.equalsIgnoreCase("pds")) {
                if (activeMap.isFoWMode()) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), playerRep + " " + successMessage);
                } else {
                    List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                    if (threadChannels == null)
                        return;
                    String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-construction";
                    // SEARCH FOR EXISTING OPEN THREAD
                    for (ThreadChannel threadChannel_ : threadChannels) {
                        if (threadChannel_.getName().equals(threadName)) {
                            MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_,
                                    playerRep + " " + successMessage);
                        }
                    }
                }
                if(player.hasLeader("mahactagent")){
                    String message = playerRep + " Would you like to put a cc from reinforcements in the same system?";
                    Button placeCCInSystem = Button.success(
                            finsFactionCheckerPrefix + "reinforcements_cc_placement_" + planetName,
                            "Place A CC From Reinforcements In The System.");
                    Button NoDontWantTo = Button.primary(finsFactionCheckerPrefix + "deleteButtons",
                            "Don't Place A CC In The System.");
                    List<Button> buttons = List.of(placeCCInSystem, NoDontWantTo);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
                }else{
                    if(!player.getSCs().contains(Integer.parseInt("4"))){
                        String color = player.getColor();
                        String tileID = AliasHandler.resolveTile(planetName.toLowerCase());
                        Tile tile = activeMap.getTile(tileID);
                        if (tile == null) {
                            tile = activeMap.getTileByPosition(tileID);
                        }
                        if (Mapper.isColorValid(color)) {
                            AddCC.addCC(event, color, tile);
                        }

                        if (activeMap.isFoWMode()) {
                            MessageHelper.sendMessageToChannel(event.getChannel(),
                                    playerRep + " Placed A CC From Reinforcements In The "
                                            + Helper.getPlanetRepresentation(planetName, activeMap) + " system");
                        } else {
                            List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                            if (threadChannels == null)
                                return;
                            String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-construction";
                            // SEARCH FOR EXISTING OPEN THREAD
                            for (ThreadChannel threadChannel_ : threadChannels) {
                                if (threadChannel_.getName().equals(threadName)) {
                                    MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_,
                                            playerRep + " Placed A CC From Reinforcements In The "
                                                    + Helper.getPlanetRepresentation(planetName, activeMap) + " system");
                                }
                            }
                        }
                    }
                }
                
                event.getMessage().delete().queue();

            } else {
                String editedMessage = event.getMessage().getContentRaw();
                if (editedMessage.contains("Produced")) {
                    editedMessage = editedMessage + "\n " + successMessage;
                } else {
                    editedMessage = playerRep + " " + successMessage;
                }

                if (editedMessage.contains("place 2 infantry")) {
                    successMessage = "Placed 2 " + Helper.getEmojiFromDiscord("infantry") + " on "
                            + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                    event.getMessage().delete().queue();
                } else {
                    event.getMessage().editMessage(editedMessage).queue();
                }

            }

            if(player.getLeaderIDs().contains("titanscommander") && !player.hasLeaderUnlocked("titanscommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "titans", event);
            }
            if(player.getLeaderIDs().contains("saarcommander") && !player.hasLeaderUnlocked("saarcommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "saar", event);
            }
            if(player.getLeaderIDs().contains("mentakcommander") && !player.hasLeaderUnlocked("mentakcommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "mentak", event);
            }
            if(player.getLeaderIDs().contains("l1z1xcommander") && !player.hasLeaderUnlocked("l1z1xcommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "l1z1x", event);
            }
            if(player.getLeaderIDs().contains("muaatcommander") && !player.hasLeaderUnlocked("muaatcommander") && unitLong.equalsIgnoreCase("warsun")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "muaat", event);
            }
            if(player.getLeaderIDs().contains("argentcommander") && !player.hasLeaderUnlocked("argentcommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "argent", event);
            }
            if(player.getLeaderIDs().contains("naazcommander") && !player.hasLeaderUnlocked("naazcommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "naaz", event);
            }
            if(player.getLeaderIDs().contains("arboreccommander") && !player.hasLeaderUnlocked("arboreccommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "arborec", event);
            }

        } else if (buttonID.startsWith("yssarilcommander_")) {

            buttonID = buttonID.replace("yssarilcommander_", "");
            String enemyFaction = buttonID.split("_")[1];
            Player enemy = Helper.getPlayerFromColorOrFaction(activeMap, enemyFaction);
            String message = "";
            String type = buttonID.split("_")[0];
            if(type.equalsIgnoreCase("ac")){
                ShowAllAC.showAll(enemy, player, activeMap);
                message = "Yssaril commander used to look at ACs";
            }
            if(type.equalsIgnoreCase("so")){
                new ShowAllSO().showAll(enemy, player, activeMap);
                message = "Yssaril commander used to look at SOs";
            }
            if(type.equalsIgnoreCase("pn")){
                new ShowAllPN().showAll(enemy, player, activeMap, false);
                message = "Yssaril commander used to look at PNs";
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            if(activeMap.isFoWMode()){
                MessageHelper.sendMessageToChannel(enemy.getPrivateChannel(), message);
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("freelancersBuild_")) {
            String planet = buttonID.replace("freelancersBuild_", "");
            List<Button> buttons = new ArrayList<Button>();
            Tile tile = activeMap.getTile(AliasHandler.resolveTile(planet));
            if(tile == null){
                tile = activeMap.getTileByPosition(planet);
            }
            buttons = Helper.getPlaceUnitButtons(event, player, activeMap,
                    tile, "freelancers", "placeOneNDone_dontskip");
            String message = Helper.getPlayerRepresentation(player, activeMap) + " Use the buttons to produce 1 unit. "
                    + ButtonHelper.getListOfStuffAvailableToSpend(player, activeMap);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("tacticalActionBuild_")) {
            String pos = buttonID.replace("tacticalActionBuild_", "");
            List<Button> buttons = new ArrayList<Button>();
            buttons = Helper.getPlaceUnitButtons(event, player, activeMap,
                    activeMap.getTileByPosition(pos), "tacticalAction", "place");
            String message = Helper.getPlayerRepresentation(player, activeMap) + " Use the buttons to produce units. "
                    + ButtonHelper.getListOfStuffAvailableToSpend(player, activeMap);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
         } else if (buttonID.startsWith("getModifyTiles")) {
            List<Button> buttons = new ArrayList<Button>();
            buttons = ButtonHelper.getTilesToModify(player, activeMap, event);
            String message = Helper.getPlayerRepresentation(player, activeMap) + " Use the buttons to select the tile in which you wish to modify units. ";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        } else if (buttonID.startsWith("genericModify_")) {
            String pos = buttonID.replace("genericModify_", "");
            Tile tile = activeMap.getTileByPosition(pos);
            ButtonHelper.offerBuildOrRemove(player, activeMap, event, tile);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("genericBuild_")) {
            String pos = buttonID.replace("genericBuild_", "");
            activeMap.resetCurrentMovedUnitsFrom1TacticalAction();
            List<Button> buttons = new ArrayList<Button>();
            buttons = Helper.getPlaceUnitButtons(event, player, activeMap,
                    activeMap.getTileByPosition(pos), "genericBuild", "place");
            String message = Helper.getPlayerRepresentation(player, activeMap) + " Use the buttons to produce units. ";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("starforgeTile_")) {
            String pos = buttonID.replace("starforgeTile_", "");

            List<Button> buttons = new ArrayList<Button>();
            Button starforgerStroter = Button.danger("starforge_destroyer_" + pos, "Starforge Destroyer")
                    .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("destroyer")));
            buttons.add(starforgerStroter);
            Button starforgerFighters = Button.danger("starforge_fighters_" + pos, "Starforge 2 Fighters")
                    .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("fighter")));
            buttons.add(starforgerFighters);
            String message = "Use the buttons to select what you would like to starforge.";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("starforge_")) {
            String unitNPlace = buttonID.replace("starforge_", "");
            String unit = unitNPlace.split("_")[0];
            String pos = unitNPlace.split("_")[1];
            Tile tile = activeMap.getTileByPosition(pos);
            String successMessage = "Reduced strategy pool CCs by 1 (" + (player.getStrategicCC()) + "->"
                    + (player.getStrategicCC() - 1) + ")";
            player.setStrategicCC(player.getStrategicCC() - 1);
            ButtonHelper.resolveMuaatCommanderCheck(player, activeMap, event);
            MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
            List<Button> buttons = ButtonHelper.getStartOfTurnButtons(player, activeMap, true, event);
            if (unit.equals("destroyer")) {
                new AddUnits().unitParsing(event, player.getColor(), tile, "1 destroyer", activeMap);
                successMessage = "Produced 1 " + Helper.getEmojiFromDiscord("destroyer") + " in tile "
                        + tile.getRepresentationForButtons(activeMap, player) + ".";

            } else {
                new AddUnits().unitParsing(event, player.getColor(), tile, "2 ff", activeMap);
                successMessage = "Produced 2 " + Helper.getEmojiFromDiscord("fighter") + " in tile "
                        + tile.getRepresentationForButtons(activeMap, player) + ".";
            }
            successMessage = ButtonHelper.putInfWithMechsForStarforge(pos, successMessage, activeMap, player, event);

            MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
            String message = "Use buttons to end turn or do another action";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("planetAbilityExhaust_")) {
            String planet = buttonID.replace("planetAbilityExhaust_", "");
            new PlanetExhaustAbility().doAction(player, planet, activeMap);
        } else if (buttonID.startsWith("scPick_")) {
            Stats stats = new Stats();
            String num = buttonID.replace("scPick_", "");
            int scpick = Integer.parseInt(num);
            boolean pickSuccessful = stats.secondHalfOfPickSC((GenericInteractionCreateEvent) event, activeMap, player,
                    scpick);
            if (pickSuccessful) {
                new SCPick().secondHalfOfSCPick((GenericInteractionCreateEvent) event, player, activeMap, scpick);
                event.getMessage().delete().queue();
            }

        } else if (buttonID.startsWith("milty_")) {

//            System.out.println("MILTY");
        } else if (buttonID.startsWith("ring_")) {
            List<Button> ringButtons = ButtonHelper.getTileInARing(player, activeMap, buttonID, event);
            String num = buttonID.replace("ring_", "");
            String message = "";
            if (!num.equalsIgnoreCase("corners")) {
                int ring = Integer.parseInt(num.charAt(0) + "");
                if (ring > 4 && !num.contains("left") && !num.contains("right")) {
                    message = "That ring is very large. Specify if your tile is on the left or right side of the map (center will be counted in both).";
                } else {
                    message = "Click the tile that you want to activate.";
                }
            } else {
                message = "Click the tile that you want to activate.";
            }

            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("getACFrom_")) {
            String faction = buttonID.replace("getACFrom_", "");
            Player victim = Helper.getPlayerFromColorOrFaction(activeMap, faction);
            List<Button> buttons = ButtonHelper.getButtonsToTakeSomeonesAC(activeMap, player, victim);
            ShowAllAC.showAll(victim, player, activeMap);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeMap),
                    Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true)
                            + " Select which AC you would like to steal",
                    buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("doActivation_")) {
            String pos = buttonID.replace("doActivation_", "");
            ButtonHelper.resolveOnActivationEnemyAbilities(activeMap, activeMap.getTileByPosition(pos), player, false);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("ringTile_")) {
            String pos = buttonID.replace("ringTile_", "");
            List<Button> systemButtons = ButtonHelper.getTilesToMoveFrom(player, activeMap, event);
            activeMap.setActiveSystem(pos);
            MessageHelper.sendMessageToChannel(event.getChannel(), trueIdentity + " activated "
                    + activeMap.getTileByPosition(pos).getRepresentationForButtons(activeMap, player));
            
            List<Player> playersWithPds2 = new ArrayList<Player>();
            if(!activeMap.isFoWMode()){
                playersWithPds2 = ButtonHelper.tileHasPDS2Cover(player, activeMap, pos);
                int abilities = ButtonHelper.resolveOnActivationEnemyAbilities(activeMap, activeMap.getTileByPosition(pos), player, true);
                if(abilities > 0){
                    List<Button> buttons = new ArrayList<Button>();
                    buttons.add(Button.success(finsFactionCheckerPrefix+"doActivation_"+pos, "Confirm"));
                    buttons.add(Button.danger(finsFactionCheckerPrefix+"deleteButtons", "This activation was a mistake"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), ident+" You are about to automatically trigger some abilities by activating this system, are you sure you want to proceed?", buttons);
                }
            }else{
                 List<Player> playersAdj = FoWHelper.getAdjacentPlayers(activeMap, pos, true);
                for (Player player_ : playersAdj) {
                    String playerMessage = Helper.getPlayerRepresentation(player_, activeMap, event.getGuild(), true) + " - System " + pos + " has been activated ";
                    boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, playerMessage);
                }
                ButtonHelper.resolveOnActivationEnemyAbilities(activeMap, activeMap.getTileByPosition(pos), player, false);
            }
            if (!activeMap.isFoWMode() && playersWithPds2.size() > 0) {
                String pdsMessage = trueIdentity + " this is a courtesy notice that the selected system is in range of deep space cannon units owned by";

                for(Player playerWithPds : playersWithPds2){
                    pdsMessage = pdsMessage + " "+Helper.getPlayerRepresentation(playerWithPds, activeMap, activeMap.getGuild(), false);
                }
                MessageHelper.sendMessageToChannel(event.getChannel(), pdsMessage);
            }
            List<Button> button2 = ButtonHelper.scanlinkResolution(player, activeMap, event);
            if (player.getTechs().contains("sdn") && !button2.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Please resolve scanlink",
                        button2);
                if(player.hasAbility("awaken")){
                    ButtonHelper.resolveTitanShenanigansOnActivation(player, activeMap, activeMap.getTileByPosition(pos), event);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                        "\n\nUse buttons to select the first system you want to move from", systemButtons);
            } else {
                if(player.hasAbility("awaken")){
                    ButtonHelper.resolveTitanShenanigansOnActivation(player, activeMap, activeMap.getTileByPosition(pos), event);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                        "Use buttons to select the first system you want to move from", systemButtons);
            }
            event.getMessage().delete().queue();
         } else if (buttonID.startsWith("genericRemove_")) {
            String pos = buttonID.replace("genericRemove_", "");
            activeMap.resetCurrentMovedUnitsFrom1System();
            activeMap.resetCurrentMovedUnitsFrom1TacticalAction();
            List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeMap,
                    activeMap.getTileByPosition(pos), "Remove");
            activeMap.resetCurrentMovedUnitsFrom1System();
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Chose to remove units from "
                            + activeMap.getTileByPosition(pos).getRepresentationForButtons(activeMap, player));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),"Use buttons to select the units you want to remove.",systemButtons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("tacticalMoveFrom_")) {
            String pos = buttonID.replace("tacticalMoveFrom_", "");
            List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeMap,
                    activeMap.getTileByPosition(pos), "Move");
            activeMap.resetCurrentMovedUnitsFrom1System();
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    "Chose to move from "
                            + activeMap.getTileByPosition(pos).getRepresentationForButtons(activeMap, player)
                            + ". Use buttons to select the units you want to move.",
                    systemButtons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("purge_Frags_")) {
            String typeNAmount = buttonID.replace("purge_Frags_", "");
            String type = typeNAmount.split("_")[0];
            int count = Integer.parseInt(typeNAmount.split("_")[1]);
            List<String> fragmentsToPurge = new ArrayList<String>();
            ArrayList<String> playerFragments = player.getFragments();
            for (String fragid : playerFragments) {
                if (fragid.contains(type.toLowerCase())) {
                    fragmentsToPurge.add(fragid);
                }
            }
            while (fragmentsToPurge.size() > count) {
                fragmentsToPurge.remove(0);
            }

            for (String fragid : fragmentsToPurge) {
                player.removeFragment(fragid);
            }
            String message = Helper.getPlayerRepresentation(player, activeMap) + " purged fragments: "
                    + fragmentsToPurge.toString();
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        }

        else if (buttonID.startsWith("unitTactical")) {
            String remove = "Move";
             HashMap<String, Integer> currentSystem = activeMap.getCurrentMovedUnitsFrom1System();
            HashMap<String, Integer> currentActivation = activeMap.getMovedUnitsFromCurrentActivation();
            String rest = "";
            if(buttonID.contains("Remove")){
                remove = "Remove";
                rest = buttonID.replace("unitTacticalRemove_", "");
            }else{
                rest = buttonID.replace("unitTacticalMove_", "");
            }
            String pos = rest.substring(0, rest.indexOf("_"));
            Tile tile = activeMap.getTileByPosition(pos);
            rest = rest.replace(pos + "_", "");

            if(rest.contains("All")){
               
                if(rest.contains("reverse"))
                {
                    for(String unit : currentSystem.keySet()){
                        
                        String unitkey = "";
                        String planet = "";
                        String origUnit = unit;
                        String damagedMsg = "";
                        int amount = currentSystem.get(unit);
                        if(unit.contains("_"))
                        {
                            unitkey =unit.split("_")[0];
                            planet =unit.split("_")[1];
                        }else{
                            unitkey = unit;
                        }
                        if (currentActivation.containsKey(unitkey)) {
                            activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitkey,
                                    currentActivation.get(unitkey) - amount);
                        }
                        if(unitkey.contains("damaged")){
                            unitkey = unitkey.replace("damaged", "");
                            damagedMsg = " damaged ";
                        }
                        new AddUnits().unitParsing(event, player.getColor(),
                        activeMap.getTileByPosition(pos), (amount) + " " + unitkey + " " + planet, activeMap);
                        if(damagedMsg.contains("damaged")){
                            if (planet.equalsIgnoreCase("")) {
                                planet = "space";
                            }
                            String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
                            activeMap.getTileByPosition(pos).addUnitDamage(planet, unitID, (amount));
                        }
                    }
                   
                    activeMap.resetCurrentMovedUnitsFrom1System();
                }else{
                    java.util.Map<String, String> unitRepresentation = Mapper.getUnitImageSuffixes();
                    java.util.Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
                    String cID = Mapper.getColorID(player.getColor());
                    for (java.util.Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
                        String name = entry.getKey();
                        String representation = planetRepresentations.get(name);
                        if (representation == null){
                            representation = name;
                        }
                        UnitHolder unitHolder = entry.getValue();
                        HashMap<String, Integer> units1 = unitHolder.getUnits();
                        HashMap<String, Integer> units = new HashMap<String, Integer>();
                        units.putAll(units1);
                    
                        if (unitHolder instanceof Planet planet) {
                            for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                                String key = unitEntry.getKey();
                                if ((key.endsWith("gf.png") || key.endsWith("mf.png")) &&key.contains(cID)) {
                                    String unitKey = key.replace(cID+"_", "");
                                    unitKey = unitKey.replace(".png", "");
                                    unitKey = ButtonHelper.getUnitName(unitKey);
                                    int amount = unitEntry.getValue();
                                    rest = unitKey+"_"+unitHolder.getName();
                                    if (currentSystem.containsKey(rest)) {
                                        activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
                                    } else {
                                        activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                                    }
                                    if (currentActivation.containsKey(unitKey)) {
                                        activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey,
                                                currentActivation.get(unitKey) + amount);
                                    } else {
                                        activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey, amount);
                                    }
                                    String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitKey), player.getColor());

                                    new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), unitEntry.getValue(), unitHolder.getName(), unitID, player.getColor(), false);

                                  //  validTile2 = Button.danger(finChecker+"unitTactical"+moveOrRemove+"_"+tile.getPosition()+"_"+x+unitKey+"_"+representation, moveOrRemove+" "+x+" Infantry from "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("infantry")));
                                }
                            }
                        }
                        else{
                            for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {

                                String key = unitEntry.getKey();
                                for (String unitRepresentationKey : unitRepresentation.keySet()) {
                                    if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {
                                        
                                        String unitKey = key.replace(cID+"_", "");
                                        
                                        int totalUnits = unitEntry.getValue();
                                        int amount = unitEntry.getValue();
                                        unitKey  = unitKey.replace(".png", "");
                                        unitKey = ButtonHelper.getUnitName(unitKey);
                                        int damagedUnits = 0;
                                        if(unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null){
                                            damagedUnits = unitHolder.getUnitDamage().get(key);
                                        }
                                        String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitKey), player.getColor());

                                        new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), totalUnits, "space", unitID, player.getColor(), false);
                                        if(damagedUnits > 0){
                                            rest = unitKey+"damaged";
                                            amount = damagedUnits;
                                            if (currentSystem.containsKey(rest)) {
                                                activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
                                            } else {
                                                activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                                            }
                                            if (currentActivation.containsKey(unitKey)) {
                                                activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey,
                                                        currentActivation.get(unitKey) + amount);
                                            } else {
                                                activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey, amount);
                                            }
                                        }
                                        rest = unitKey;
                                        amount = totalUnits - damagedUnits;
                                        if(amount > 0){
                                            if (currentSystem.containsKey(rest)) {
                                                activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
                                            } else {
                                                activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                                            }
                                            if (currentActivation.containsKey(unitKey)) {
                                                activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey,
                                                        currentActivation.get(unitKey) + amount);
                                            } else {
                                                activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey, amount);
                                            }
                                        }
                                        
                                        
                                    }
                                }
                            }
                        }             
                    }
                }
                String message = ButtonHelper.buildMessageFromDisplacedUnits(activeMap, false, player, remove);
                List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeMap,
                        activeMap.getTileByPosition(pos), remove);
                event.getMessage().editMessage(message)
                        .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
                return;
            }
            int amount = Integer.parseInt(rest.charAt(0) + "");
            if (rest.contains("_reverse")) {
                amount = amount * -1;
                rest = rest.replace("_reverse", "");
            }
            rest = rest.substring(1, rest.length());
            String unitkey = "";
            String planet = "";

            if (rest.contains("_")) {
                unitkey = rest.split("_")[0];
                planet = rest.split("_")[1].toLowerCase().replace(" ", "");
            } else {
                unitkey = rest;
            }
            unitkey = unitkey.replace("damaged", "");
            planet = planet.replace("damaged", "");
            String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
            rest = rest.replace("damaged", "");
            if (amount < 0) {
                
                new AddUnits().unitParsing(event, player.getColor(),
                        activeMap.getTileByPosition(pos), (amount * -1) + " " + unitkey + " " + planet, activeMap);
                if(buttonLabel.toLowerCase().contains("damaged")){
                    if (planet.equalsIgnoreCase("")) {
                        planet = "space";
                    }
                    activeMap.getTileByPosition(pos).addUnitDamage(planet, unitID, (amount * -1));
                }
            } else {
                String planetName = "";
                if (planet.equalsIgnoreCase("")) {
                    planetName = "space";
                } else {

                    planetName = planet.toLowerCase().replace(" ", "");
                    planetName = planet.replace("'", "");
                    planetName = AliasHandler.resolvePlanet(planetName);

                }
                
                if(buttonLabel.toLowerCase().contains("damaged")){
                    new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), amount, planetName, unitID, player.getColor(), true);
                }else{
                    new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), amount, planetName, unitID, player.getColor(), false);
                }
               // String key = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
                //activeMap.getTileByPosition(pos).removeUnit(planetName, key, amount);
            }
            if(buttonLabel.toLowerCase().contains("damaged")){ 
                unitkey = unitkey + "damaged";
                rest = rest+"damaged";
            }
            if (currentSystem.containsKey(rest)) {
                activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
            } else {
                activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
            }
            if(currentSystem.get(rest) == 0){
                currentSystem.remove(rest);
            }
            if (currentActivation.containsKey(unitkey)) {
                activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitkey,
                        currentActivation.get(unitkey) + amount);
            } else {
                activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitkey, amount);
            }
            String message = ButtonHelper.buildMessageFromDisplacedUnits(activeMap, false, player, remove);
            List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeMap,
                    activeMap.getTileByPosition(pos), remove);
            event.getMessage().editMessage(message)
                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();

        } else if (buttonID.startsWith("landUnits_")) {
            String rest = buttonID.replace("landUnits_", "");
            String pos = rest.substring(0, rest.indexOf("_"));
            rest = rest.replace(pos + "_", "");
            int amount = Integer.parseInt(rest.charAt(0) + "");
            rest = rest.substring(1, rest.length());
            String unitkey = "";
            String planet = "";
            if (rest.contains("_")) {
                unitkey = rest.split("_")[0];
                planet = rest.split("_")[1].replace(" ", "").toLowerCase();
            } else {
                unitkey = rest;
            }
            if(buttonLabel.toLowerCase().contains("damaged")){
                rest = rest+"damaged";
            }
            HashMap<String, Integer> currentSystem = activeMap.getCurrentMovedUnitsFrom1System();
            if (currentSystem.containsKey(rest)) {
                activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
            } else {
                activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
            }
            String message = ButtonHelper.buildMessageFromDisplacedUnits(activeMap, true, player, "Move");
            List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeMap, event);
            event.getMessage().editMessage(message)
                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        }

        else if (buttonID.startsWith("reinforcements_cc_placement_")) {
            String playerRep = Helper.getPlayerRepresentation(player, activeMap);
            String planet = buttonID.replace("reinforcements_cc_placement_", "");
            String tileID = AliasHandler.resolveTile(planet.toLowerCase());
            Tile tile = activeMap.getTile(tileID);
            if (tile == null) {
                tile = activeMap.getTileByPosition(tileID);
            }
            if (tile == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Could not resolve tileID:  `" + tileID + "`. Tile not found");
                return;
            }
            String color = player.getColor();
            if (Mapper.isColorValid(color)) {
                AddCC.addCC(event, color, tile);
            }

            if (activeMap.isFoWMode()) {
                MessageHelper.sendMessageToChannel(event.getChannel(),
                        playerRep + " Placed A CC From Reinforcements In The "
                                + Helper.getPlanetRepresentation(planet, activeMap) + " system");
            } else {
                List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                if (threadChannels == null)
                    return;
                String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-construction";
                // SEARCH FOR EXISTING OPEN THREAD
                for (ThreadChannel threadChannel_ : threadChannels) {
                    if (threadChannel_.getName().equals(threadName)) {
                        MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_,
                                playerRep + " Placed A CC From Reinforcements In The "
                                        + Helper.getPlanetRepresentation(planet, activeMap) + " system");
                    }
                }
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("transactWith_")) {
            String faction = buttonID.replace("transactWith_", "");
            Player p2 = Helper.getPlayerFromColorOrFaction(activeMap, faction);
            List<Button> buttons = new ArrayList<Button>();
            buttons = ButtonHelper.getStuffToTransButtons(activeMap, player, p2);
            String message = Helper.getPlayerRepresentation(player, activeMap)
                    + " Use the buttons to select what you want to transact";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("transact_")) {
            ButtonHelper.resolveSpecificTransButtons(activeMap, player, buttonID, event);
            event.getMessage().delete().queue();

        } else if (buttonID.startsWith("play_after_")) {
            String riderName = buttonID.replace("play_after_", "");
            new ButtonListener().addReaction(event, true, true, "Playing " + riderName, riderName + " Played");
            List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, activeMap,
                    finsFactionCheckerPrefix);
            MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel,
                    "Please select your rider target", activeMap, player, riderButtons);
            List<Button> afterButtons = AgendaHelper.getAfterButtons(activeMap);
            if (riderName.equalsIgnoreCase("Keleres Rider")) {
                ButtonHelper.resolvePNPlay(AliasHandler.resolvePromissory(riderName), player, activeMap, event);
            }
            if (riderName.equalsIgnoreCase("Keleres Xxcha Hero")) {
                Leader playerLeader = player.getLeader("keleresheroodlynn");
                StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(player, activeMap))
                        .append(" played ").append(Helper.getLeaderFullRepresentation(playerLeader));
                boolean purged = player.removeLeader(playerLeader);
                if (purged) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            message.toString() + " - Leader Oodlynn has been purged");
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            "Leader was not purged - something went wrong");
                }
            }
            MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel,
                    "Please indicate no afters again.", activeMap, afterButtons, "after");
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("componentActionRes_")) {
            ButtonHelper.resolvePressedCompButton(activeMap, player, event, buttonID);
            event.getMessage().delete().queue();
         } else if (buttonID.startsWith("addIonStorm_")) {
            String pos = buttonID.substring(buttonID.lastIndexOf("_")+1, buttonID.length());
            Tile tile = activeMap.getTileByPosition(pos);
            if(buttonID.contains("alpha")){
                String tokenFilename = Mapper.getTokenID("ionalpha");
                tile.addToken(tokenFilename, Constants.SPACE);
                MessageHelper.sendMessageToChannel(event.getChannel(), "Added ionstorm alpha to "+tile.getRepresentation());

            }else{
                String tokenFilename = Mapper.getTokenID("ionbeta");
                tile.addToken(tokenFilename, Constants.SPACE);
                MessageHelper.sendMessageToChannel(event.getChannel(), "Added ionstorm beta to "+tile.getRepresentation());
            }

            event.getMessage().delete().queue();
         } else if (buttonID.startsWith("terraformPlanet_")) {
            String planet = buttonID.replace("terraformPlanet_","");
            UnitHolder unitHolder = activeMap.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            planetReal.addToken(Constants.ATTACHMENT_TITANSPN_PNG);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Attached terraform to "+Helper.getPlanetRepresentation(planet, activeMap));
            event.getMessage().delete().queue();
         } else if (buttonID.startsWith("nanoforgePlanet_")) {
            String planet = buttonID.replace("nanoforgePlanet_","");
            UnitHolder unitHolder = activeMap.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            planetReal.addToken("attachment_nanoforge.png");
            MessageHelper.sendMessageToChannel(event.getChannel(), "Attached nanoforge to "+Helper.getPlanetRepresentation(planet, activeMap));
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("resolvePNPlay_")) {
            String pnID = buttonID.replace("resolvePNPlay_", "");
            ButtonHelper.resolvePNPlay(pnID, player, activeMap, event);
            if(!pnID.equalsIgnoreCase("bmf")){
                event.getMessage().delete().queue();
            }
            
        } else if (buttonID.startsWith("send_")) {
            ButtonHelper.resolveSpecificTransButtonPress(activeMap, player, buttonID, event);
            event.getMessage().delete().queue();
         } else if (buttonID.startsWith("replacePDSWithFS_")) {
            buttonID = buttonID.replace("replacePDSWithFS_", "");
            String planet = buttonID;
            String message = ident + " replaced "+ Helper.getEmojiFromDiscord("pds") +" on " + Helper.getPlanetRepresentation(planet,activeMap)+ " with a "+ Helper.getEmojiFromDiscord("flagship");
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            new AddUnits().unitParsing(event, player.getColor(),activeMap.getTile(AliasHandler.resolveTile(planet)), "flagship",activeMap);
            String key = Mapper.getUnitID(AliasHandler.resolveUnit("pds"), player.getColor());
            activeMap.getTile(AliasHandler.resolveTile(planet)).removeUnit(planet,key, 1);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("putSleeperOnPlanet_")) {
            buttonID = buttonID.replace("putSleeperOnPlanet_", "");
            String planet = buttonID;
            String message = ident+" put a sleeper on " + Helper.getPlanetRepresentation(planet,activeMap);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            new SleeperToken().addOrRemoveSleeper(event, activeMap, planet, player);
            event.getMessage().delete().queue();
         } else if (buttonID.startsWith("removeSleeperFromPlanet_")) {
            buttonID = buttonID.replace("removeSleeperFromPlanet_", "");
            String planet = buttonID;
            String message = ident + " removed a sleeper from " + planet;
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            new SleeperToken().addOrRemoveSleeper(event, activeMap, planet, player);
            event.getMessage().delete().queue();
         } else if (buttonID.startsWith("replaceSleeperWith_")) {
            buttonID = buttonID.replace("replaceSleeperWith_", "");
            String planetName = buttonID.split("_")[1];
            String unit = buttonID.split("_")[0];
            String message = "";
            new SleeperToken().addOrRemoveSleeper(event, activeMap, planetName, player);
            if(unit.equalsIgnoreCase("mech")){
                new AddUnits().unitParsing(event, player.getColor(),activeMap.getTile(AliasHandler.resolveTile(planetName)), "mech " + planetName + ", inf "+planetName,activeMap);
                message = ident + " replaced a sleeper on " + Helper.getPlanetRepresentation(planetName,activeMap) + " with a "+ Helper.getEmojiFromDiscord("mech") +" and "+ Helper.getEmojiFromDiscord("infantry");
            }else{
                new AddUnits().unitParsing(event, player.getColor(),activeMap.getTile(AliasHandler.resolveTile(planetName)), "pds " + planetName,activeMap);
                message = ident + " replaced a sleeper on " + Helper.getPlanetRepresentation(planetName,activeMap) + " with a "+ Helper.getEmojiFromDiscord("pds");
            }
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("topAgenda_")) {
            String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            new PutAgendaTop().putTop((GenericInteractionCreateEvent) event, Integer.parseInt(agendaNumID), activeMap);
            MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Put " + agendaNumID + " on the top of the agenda deck.");
        } else if (buttonID.startsWith("primaryOfWarfare")) {
            List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, activeMap, event, "warfare");
            MessageChannel channel = event.getMessageChannel();
            if(activeMap.isFoWMode()){
                channel = player.getPrivateChannel();
            }
            MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token.", buttons);
        } else if (buttonID.startsWith("mahactCommander")) {
            List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, activeMap, event, "mahactCommander");
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to remove token.", buttons);
            event.getMessage().delete().queue();
         } else if (buttonID.startsWith("useTA_")) {
            String ta = buttonID.replace("useTA_", "") + "_ta";
            ButtonHelper.resolvePNPlay(ta,  player,  activeMap,  event);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("removeCCFromBoard_")) {
            ButtonHelper.resolveRemovingYourCC(player, activeMap, event, buttonID);
            event.getMessage().delete().queue();

        } else if (buttonID.startsWith("bottomAgenda_")) {
            String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            new PutAgendaBottom().putBottom((GenericInteractionCreateEvent) event, Integer.parseInt(agendaNumID),
                    activeMap);
            MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Put " + agendaNumID + " on the bottom of the agenda deck.");
        } else if (buttonID.startsWith("agendaResolution_")) {
            String winner = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            String agendaid = activeMap.getCurrentAgendaInfo().substring(
                    activeMap.getCurrentAgendaInfo().lastIndexOf("_") + 1, activeMap.getCurrentAgendaInfo().length());
            int aID = 0;
            if (agendaid.equalsIgnoreCase("CL")) {
                String id2 = activeMap.revealAgenda(false);
                LinkedHashMap<String, Integer> discardAgendas = activeMap.getDiscardAgendas();
                AgendaModel agendaDetails = Mapper.getAgenda(id2);
                String agendaName = agendaDetails.getName();
                MessageHelper.sendMessageToChannel(actionsChannel, "The hidden agenda was " + agendaName
                        + "! You can find it added as a law or in the discard.");
                Integer uniqueID = discardAgendas.get(id2);
                aID = uniqueID;
            } else {
                aID = Integer.parseInt(agendaid);
            }

            if (activeMap.getCurrentAgendaInfo().startsWith("Law")) {
                if (activeMap.getCurrentAgendaInfo().contains("Player")) {
                    Player player2 = Helper.getPlayerFromColorOrFaction(activeMap, winner);
                    if (player2 != null) {
                        activeMap.addLaw(aID, winner);
                    }
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                            "Added Law with " + winner + " as the elected!");
                } else {
                    if (winner.equalsIgnoreCase("for")) {
                        activeMap.addLaw(aID, null);
                        MessageHelper.sendMessageToChannel(event.getChannel(), "Added law to map!");
                    }
                    if (activeMap.getCurrentAgendaInfo().contains("Secret")) {
                        activeMap.addLaw(aID, winner);
                        int soID = 0;
                        String soName = winner;
                        Player playerWithSO = null;

                        for (java.util.Map.Entry<String, Player> playerEntry : activeMap.getPlayers().entrySet()) {
                            Player player_ = playerEntry.getValue();
                            LinkedHashMap<String, Integer> secretsScored = new LinkedHashMap<>(
                                    player_.getSecretsScored());
                            for (java.util.Map.Entry<String, Integer> soEntry : secretsScored.entrySet()) {
                                if (soEntry.getKey().equals(soName)) {
                                    soID = soEntry.getValue();
                                    playerWithSO = player_;
                                    break;
                                }
                            }
                        }

                        if (playerWithSO == null) {
                            MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found");
                            return;
                        }
                        if (soName.isEmpty()) {
                            MessageHelper.sendMessageToChannel(event.getChannel(), "Can make just Scored SO to Public");
                            return;
                        }
                        activeMap.addToSoToPoList(soName);
                        Integer poIndex = activeMap.addCustomPO(soName, 1);
                        activeMap.scorePublicObjective(playerWithSO.getUserID(), poIndex);

                        String sb = "**Public Objective added from Secret:**" + "\n" +
                                "(" + poIndex + ") " + "\n" +
                                Mapper.getSecretObjectivesJustNames().get(soName) + "\n";
                        MessageHelper.sendMessageToChannel(event.getChannel(), sb);

                        SOInfo.sendSecretObjectiveInfo(activeMap, playerWithSO, event);

                    }
                }
            } else {
                 LinkedHashMap<String, Integer> discardAgendas = activeMap.getDiscardAgendas();
                String agID ="";
                for (java.util.Map.Entry<String, Integer> agendas : discardAgendas.entrySet()) {
                    if (agendas.getValue().equals(aID)) {
                        agID = agendas.getKey();
                        break;
                    }
                }
                
                if(agID.equalsIgnoreCase("mutiny")){
                    List<Player> winOrLose = null;
                    StringBuilder message = new StringBuilder("");
                    Integer poIndex = 5;
                    if (winner.equalsIgnoreCase("for")) {
                        winOrLose = AgendaHelper.getWinningVoters(winner, activeMap);
                        poIndex = activeMap.addCustomPO("Mutiny", 1);

                    }else{
                        winOrLose = AgendaHelper.getLosingVoters(winner, activeMap);
                        poIndex = activeMap.addCustomPO("Mutiny", -1);
                    }
                    message.append("Custom PO 'Mutiny' has been added.\n");
                    for(Player playerWL : winOrLose){
                        activeMap.scorePublicObjective(playerWL.getUserID(), poIndex);
                        message.append(Helper.getPlayerRepresentation(playerWL, activeMap)).append(" scored 'Mutiny'\n");
                    }
                    MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), message.toString());     
                }
                if(agID.equalsIgnoreCase("plowshares")){
                    if (winner.equalsIgnoreCase("for")) {
                        for(Player playerB : activeMap.getRealPlayers()){
                            new SwordsToPlowsharesTGGain().doSwords(playerB, event, activeMap);
                        }
                    }else{
                        for(Player playerB : activeMap.getRealPlayers()){
                            new RiseOfMessiah().doRise(playerB, event, activeMap);
                        }
                    }   
                }
                if(agID.equalsIgnoreCase("economic_equality")){
                    int tg = 0;
                    if (winner.equalsIgnoreCase("for")) {
                        for(Player playerB : activeMap.getRealPlayers()){
                            playerB.setTg(5);
                            ButtonHelper.pillageCheck(playerB, activeMap);
                        }
                        tg = 5;
                    }else{
                         for(Player playerB : activeMap.getRealPlayers()){
                             playerB.setTg(0);
                        }
                    }   
                    MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), "Set everyone's tgs to "+tg);
                }
                if (activeMap.getCurrentAgendaInfo().contains("Law")) {
                    // Figure out law
                }
            }
            String summary = AgendaHelper.getSummaryOfVotes(activeMap, false);
            List<Player> riders = AgendaHelper.getWinningRiders(summary, winner, activeMap);
            String ridSum = " has a rider to resolve.";
            for (Player rid : riders) {
                String rep = Helper.getPlayerRepresentation(rid, activeMap, event.getGuild(), true);
                if (rid != null) {
                    String message = "";
                    if (rid.hasAbility("future_sight")) {
                        message = rep
                                + "You have a rider to resolve or you voted for the correct outcome. Either way a tg has been added to your total due to your future sight ability. ("
                                + rid.getTg() + "-->" + (rid.getTg() + 1) + ")";
                        rid.setTg(rid.getTg() + 1);
                        ButtonHelper.pillageCheck(rid, activeMap);
                    } else {
                        message = rep + "You have a rider to resolve";
                    }
                    if (activeMap.isFoWMode()) {
                        MessageHelper.sendPrivateMessageToPlayer(rid, activeMap, message);
                    } else {
                        MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), message);
                    }
                }
            }
            if (activeMap.isFoWMode()) {
                MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(),
                        "Sent pings to all those who ridered");
            } else {
                if (riders.size() > 0) {
                    MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), ridSum);
                }

            }
            String voteMessage = "Resolving vote for " + StringUtils.capitalize(winner)
                    + ". Click the buttons for next steps after you're done resolving riders.";

            Button flipNextAgenda = Button.primary("flip_agenda", "Flip Agenda");
            Button proceedToStrategyPhase = Button.success("proceed_to_strategy",
                    "Proceed to Strategy Phase (will run agenda cleanup and ping speaker)");
            List<Button> resActionRow = List.of(flipNextAgenda, proceedToStrategyPhase);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, resActionRow);

            event.getMessage().delete().queue();

        }
    }
}