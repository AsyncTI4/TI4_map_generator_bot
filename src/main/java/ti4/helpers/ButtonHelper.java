package ti4.helpers;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import software.amazon.awssdk.utils.StringUtils;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;

import ti4.buttons.ButtonListener;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.explore.ExpFrontier;
import ti4.commands.explore.ExploreAndDiscard;
import ti4.commands.explore.ExploreSubcommandData;
import ti4.commands.explore.SendFragments;
import ti4.commands.leaders.UnlockLeader;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.special.CombatRoll;
import ti4.commands.special.KeleresHeroMentak;
import ti4.commands.status.Cleanup;
import ti4.commands.status.ListTurnOrder;
import ti4.commands.tokens.AddCC;
import ti4.commands.tokens.AddFrontierTokens;
import ti4.commands.tokens.RemoveCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.GenerateMap;
import ti4.generator.GenerateTile;
import ti4.generator.Mapper;
import ti4.helpers.DiceHelper.Die;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;
import ti4.model.PromissoryNoteModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;


public class ButtonHelper {
    
    public static void resolveInfantryDeath(Map activeMap, Player player, int amount){
        for(int x = 0; x < amount; x++){
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap), rollInfantryRevival(activeMap, player));
        }
    }

    public static List<Button> getDacxiveButtons(Map activeMap, Player player, String planet){
        List<Button> buttons = new ArrayList<Button>();
        buttons.add(Button.success("dacxive_"+planet, "Resolve Dacxive"));
        buttons.add(Button.danger("deleteButtons", "No Dacxive"));
        return buttons;
    }

    public static void checkTransactionLegality(Map activeMap, Player player, Player player2){
        if(player == player2 || !activeMap.getCurrentPhase().equalsIgnoreCase("action") || player.hasAbility("guild_ships") ||player.getPromissoryNotes().keySet().contains("convoys") ||player2.getPromissoryNotes().keySet().contains("convoys") || player2.hasAbility("guild_ships") || Helper.getNeighbouringPlayers(activeMap, player2).contains(player)|| Helper.getNeighbouringPlayers(activeMap, player).contains(player2)){
            return;
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap), Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true) + " this is a friendly reminder that you are not neighbors with "+player2.getColor());
    }
    public static void riftUnitsButton(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        Tile tile = activeMap.getTileByPosition(buttonID.replace("getRiftButtons_",""));
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeMap), ident+ " Use buttons to rift units", ButtonHelper.getButtonsForRiftingUnitsInSystem(player, activeMap, tile));
    }

    public static void riftUnitButton(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
       String rest = buttonID.replace("riftUnit_", "").toLowerCase();
       String pos = rest.substring(0, rest.indexOf("_"));
       Tile tile = activeMap.getTileByPosition(pos);
       rest = rest.replace(pos + "_", "");
       int amount = Integer.parseInt(rest.charAt(0) + "");
       rest = rest.substring(1, rest.length());
       String unit = rest;
        for(int x = 0; x < amount; x++){
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap),ident + " "+ButtonHelper.riftUnit(unit, tile, activeMap, event, player, null));
        }
       String message = event.getMessage().getContentRaw();
       List<Button> systemButtons = ButtonHelper.getButtonsForRiftingUnitsInSystem(player, activeMap,tile);
       event.getMessage().editMessage(message)
               .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }
     public static void arboAgentOnButton(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
       String rest = buttonID.replace("arboAgentOn_", "").toLowerCase();
       String pos = rest.substring(0, rest.indexOf("_"));
       Tile tile = activeMap.getTileByPosition(pos);
       rest = rest.replace(pos + "_", "");
       int amount = Integer.parseInt(rest.charAt(0) + "");
       rest = rest.substring(1, rest.length());
       String unit = rest;
        for(int x = 0; x < amount; x++){
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap),ident + " "+ButtonHelper.riftUnit(unit, tile, activeMap, event, player, null));
        }
       event.getMessage().delete().queue();
    }
    public static void riftAllUnitsButton(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        String pos = buttonID.replace("riftAllUnits_", "").toLowerCase();
        riftAllUnitsInASystem(pos, event, activeMap, player, ident, null);
    }

    public static void riftAllUnitsInASystem(String pos, ButtonInteractionEvent event, Map activeMap, Player player, String ident, Player cabal){
       Tile tile = activeMap.getTileByPosition(pos);
       
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
            HashMap<String, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet planet) {
                continue;
            }
            else{
                java.util.Map<String, Integer> tileUnits = new HashMap<>();
                tileUnits.putAll(units);
                for (java.util.Map.Entry<String, Integer> unitEntry : tileUnits.entrySet()) {
                    String key = unitEntry.getKey();
                    if (key.endsWith("gf.png") || key.endsWith("mf.png") || ((!player.hasFF2Tech() && key.endsWith("ff.png"))   || (cabal != null && key.endsWith("ff.png"))  )) {
                        continue;
                    }
                    for (String unitRepresentationKey : unitRepresentation.keySet()) {
                        if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {
                            String unitKey = key.replace(cID+"_", "");
                            int totalUnits = unitEntry.getValue();
                            unitKey  = unitKey.replace(".png", "");
                            unitKey = ButtonHelper.getUnitName(unitKey);
                            int damagedUnits = 0;
                            if(unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null){
                                damagedUnits = unitHolder.getUnitDamage().get(key);
                            }
                            for(int x = 1; x < damagedUnits +1; x++){
                                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap),ident + " "+ButtonHelper.riftUnit(unitKey+"damaged", tile, activeMap, event, player, cabal));
                            }
                            totalUnits = totalUnits-damagedUnits;
                            for(int x = 1; x < totalUnits +1; x++){
                                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap),ident + " "+ButtonHelper.riftUnit(unitKey, tile, activeMap, event, player, cabal));
                            }
                        }
                    }
                }
            }
        }
        if(cabal == null){
            String message = event.getMessage().getContentRaw();
            List<Button> systemButtons = ButtonHelper.getButtonsForRiftingUnitsInSystem(player, activeMap,tile);
            event.getMessage().editMessage(message)
                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        }else{
            List<ActionRow> actionRow2 = new ArrayList<>();
           String exhaustedMessage = event.getMessage().getContentRaw();
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
            if(exhaustedMessage == null || exhaustedMessage.equalsIgnoreCase("")){
                exhaustedMessage = "Rift";
            }
            if(actionRow2.size() > 0 ){
                 event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
            }else{
                event.getMessage().delete().queue();
            }
        }
       
    }

    public static String riftUnit(String unit, Tile tile, Map activeMap, GenericInteractionCreateEvent event, Player player, Player cabal){
        boolean damaged = false;
        if(unit.contains("damaged")){
                unit = unit.replace("damaged", "");
                damaged = true;
        }
        Die d1 = new Die(4);
        String msg = Helper.getEmojiFromDiscord(unit.toLowerCase()) +" rolled a "+d1.getResult();
        if(damaged){
            msg = "A damaged " + msg;
        }
        if(d1.isSuccess()){
            msg = msg + " and survived. May you always be so lucky.";
        }else{
            String key = Mapper.getUnitID(AliasHandler.resolveUnit(unit), player.getColor());
            new RemoveUnits().removeStuff(event,tile, 1, "space", key, player.getColor(),damaged);
            msg = msg + " and failed. Condolences for your loss.";
            if(cabal != null && cabal != player && !ButtonHelperFactionSpecific.isCabalBlockadedByPlayer(player, activeMap, cabal)){
                ButtonHelperFactionSpecific.cabalEatsUnit(player, activeMap, cabal, 1, unit, event);
            }
        }
        
        return msg;
    }
    public static String rollInfantryRevival(Map activeMap, Player player){
       
        Die d1 = new Die(6);
        if(player.hasTech("so2")){
            d1 = new Die(5);
        }
        String msg = Helper.getEmojiFromDiscord("infantry")+ " rolled a "+d1.getResult();
        if(player.hasTech("cl2")){
            msg = Helper.getEmojiFromDiscord("infantry") + " died";
           
        }
        if(d1.isSuccess() || player.hasTech("cl2")){
            msg = msg + " and revived. You will be prompted to place them on a planet in your HS at the start of your next turn.";
            player.setStasisInfantry(player.getStasisInfantry()+1);
        }else{
            msg = msg + " and failed. No revival";
        }
        return ButtonHelper.getIdent(player)+" "+msg;
    }

    public static void placeInfantryFromRevival(Map activeMap, ButtonInteractionEvent event, Player player, String buttonID){
        String planet = buttonID.split("_")[1];
        Tile tile = Helper.getTileFromPlanet(planet, activeMap);
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 inf "+planet, activeMap);
        player.setStasisInfantry(player.getStasisInfantry()-1);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap), ButtonHelper.getIdent(player)+" Placed 1 infantry on "+Helper.getPlanetRepresentation(planet, activeMap)+". You have "+player.getStasisInfantry() + " infantry left to revive.");
        if(player.getStasisInfantry() == 0){
            event.getMessage().delete().queue();
        }
    }


    public static MessageChannel getSCFollowChannel(Map activeMap, Player player, int scNum){
        String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-";
        switch (scNum) {
            case 1 -> threadName = threadName + "leadership";
            case 2 -> threadName = threadName + "diplomacy";
            case 3 -> threadName = threadName + "politics";
            case 4 -> threadName = threadName + "construction";
            case 5 -> threadName = threadName + "trade";
            case 6 -> threadName = threadName + "warfare";
            case 7 -> threadName = threadName + "technology";
            case 8 -> threadName = threadName + "imperial";
            default -> {
                return ButtonHelper.getCorrectChannel(player, activeMap);
            }
        };
        List<ThreadChannel> threadChannels = activeMap.getMainGameChannel().getThreadChannels();
        for (ThreadChannel threadChannel_ : threadChannels) {
            if (threadChannel_.getName().equals(threadName)) {
                return (MessageChannel) threadChannel_;
            }
        }
        return ButtonHelper.getCorrectChannel(player, activeMap);
    }
    public static List<String> getTypesOfPlanetPlayerHas(Map activeMap, Player player){
        List<String> types = new ArrayList<String>();
        for(String planet : player.getPlanets()){
            UnitHolder unitHolder = activeMap.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = false;
            if (planetReal != null && planetReal.getOriginalPlanetType() != null && (planetReal.getOriginalPlanetType().equalsIgnoreCase("industrial") || planetReal.getOriginalPlanetType().equalsIgnoreCase("cultural") || planetReal.getOriginalPlanetType().equalsIgnoreCase("hazardous"))) {
                oneOfThree = true;
            }
            if (oneOfThree && !types.contains(planetReal.getOriginalPlanetType())) {
                types.add(planetReal.getOriginalPlanetType());
            }
        }
        return types;
    }
    public static List<Button> getPlaceStatusInfButtons(Map activeMap, Player player){
        List<Button> buttons = new ArrayList<Button>();

        Tile tile = activeMap.getTile(AliasHandler.resolveTile(player.getFaction()));
        if(tile == null)
        {
            tile = ButtonHelper.getTileOfPlanetWithNoTrait(player, activeMap);
        }
        for(UnitHolder unitHolder: tile.getUnitHolders().values()){
            if(unitHolder instanceof Planet){
                buttons.add(Button.success("statusInfRevival_"+unitHolder.getName(), "Place 1 infantry on "+Helper.getPlanetRepresentation(unitHolder.getName(), activeMap)));
            }
        }
        return buttons;

    }
    public static List<Button> getArcExpButtons(Map activeMap, Player player){
        List<Button> buttons = new ArrayList<Button>();
        List<String> types = ButtonHelper.getTypesOfPlanetPlayerHas(activeMap, player);
        for(String type : types){
            if(type.equals("industrial")){
                buttons.add(Button.success("arcExp_industrial", "Explore Industrials X 3"));
            }
            if(type.equals("cultural")){
                buttons.add(Button.primary("arcExp_cultural", "Explore Culturals X 3"));
            }
            if(type.equals("hazardous")){
                buttons.add(Button.danger("arcExp_hazardous", "Explore Hazardous X 3"));
            }
        }
        return buttons;
    }
    public static void resolveArcExpButtons(Map activeMap, Player player, String buttonID, ButtonInteractionEvent event, String trueIdentity){
        String type = buttonID.replace("arcExp_", "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            String cardID = activeMap.drawExplore(type);
            sb.append(((ExploreSubcommandData) new ExploreAndDiscard()).displayExplore(cardID)).append(System.lineSeparator());
            String card = Mapper.getExplore(cardID);
            String[] cardInfo = card.split(";");
            String cardType = cardInfo[3];
            if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
                sb.append(trueIdentity+" Gained relic fragment\n");
                player.addFragment(cardID);
                activeMap.purgeExplore(cardID);
            }
        }
        MessageChannel channel = ButtonHelper.getCorrectChannel(player, activeMap);
        MessageHelper.sendMessageToChannel(channel, sb.toString());
        event.getMessage().delete().queue();
    }
    public static List<Button> getExhaustButtonsWithTG(Map activeMap, Player player, GenericInteractionCreateEvent event){
            List<Button> buttons = Helper.getPlanetExhaustButtons(event, player, activeMap);
            if (player.getTg() > 0) {
                Button lost1TG = Button.danger("reduceTG_1", "Spend 1 TG");
                buttons.add(lost1TG);
            }
            if (player.getTg() > 1) {
                Button lost2TG = Button.danger("reduceTG_2", "Spend 2 TGs");
                buttons.add(lost2TG);
            }
            if (player.getTg() > 2) {
                Button lost3TG = Button.danger("reduceTG_3", "Spend 3 TGs");
                buttons.add(lost3TG);
            }
            if (player.hasLeader("keleresagent", activeMap)&&!player.getLeader("keleresagent").isExhausted()&&player.getCommodities() > 0) {
                Button lost1C = Button.danger("reduceComm_1", "Spend 1 comm");
                buttons.add(lost1C);
            }
            if (player.hasLeader("keleresagent", activeMap)&&!player.getLeader("keleresagent").isExhausted()&&player.getCommodities() > 1) {
                Button lost2C = Button.danger("reduceComm_2", "Spend 2 comms");
                buttons.add(lost2C);
            }
            
            return buttons;
    }
    public static List<Player> getPlayersWhoHaveNoSC(Player player, Map activeMap){
        List<Player> playersWhoDontHaveSC = new ArrayList<Player>();
        for(Player p2 : activeMap.getRealPlayers()){
            if(p2.getSCs().size() > 0 || p2 == player){
                continue;
            }
            playersWhoDontHaveSC.add(p2);
        }
        if(playersWhoDontHaveSC.isEmpty()){
            playersWhoDontHaveSC.add(player);
        }
        return playersWhoDontHaveSC;
    }
    public static List<Player> getPlayersWhoHaventReacted(String messageId, Map activeMap){
        List<Player> playersWhoAreMissed = new ArrayList<Player>();
        if(messageId == null || messageId.equalsIgnoreCase("")){
            return playersWhoAreMissed;
        }
        TextChannel mainGameChannel = activeMap.getMainGameChannel();
        if (mainGameChannel == null){
            return playersWhoAreMissed;
        }
        Message mainMessage = mainGameChannel.retrieveMessageById(messageId).completeAfter(500,
                TimeUnit.MILLISECONDS);
        for (Player player : activeMap.getPlayers().values()) {
            if (!player.isRealPlayer()) {
                continue;
            }

            String faction = player.getFaction();
            if (faction == null || faction.isEmpty() || faction.equals("null")){
                continue;
            }

            Emoji reactionEmoji = Emoji.fromFormatted(Helper.getFactionIconFromDiscord(faction));
            if (activeMap.isFoWMode()) {
                int index = 0;
                for (Player player_ : activeMap.getPlayers().values()) {
                    if (player_ == player)
                        break;
                    index++;
                }
                reactionEmoji = Emoji.fromFormatted(Helper.getRandomizedEmoji(index, messageId));
            }
            MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
            if (reaction == null){
                playersWhoAreMissed.add(player);
            }
        }
        return playersWhoAreMissed;
    }

    public static boolean canIBuildGFInSpace(Map activeMap, Player player, Tile tile, String kindOfBuild) {
         HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();

         if(kindOfBuild.equalsIgnoreCase("freelancers") || kindOfBuild.equalsIgnoreCase("genericBuild")){
            return true;
         }
        String colorID = Mapper.getColorID(player.getColor());
        String mechKey = colorID + "_mf.png";
        for (UnitHolder unitHolder : unitHolders.values()) {
            if(unitHolder instanceof Planet){
                continue;
            }
            if (unitHolder.getUnits() == null || unitHolder.getUnits().isEmpty()) continue;
            mechKey = colorID + "_sd.png";
            if (unitHolder.getUnits().get(mechKey) != null) {
                return true;
            }
            mechKey = colorID + "_gf.png";
            if (unitHolder.getUnits().get(mechKey) != null && player.getFaction().equalsIgnoreCase("arborec")) {
                return true;
            }
            mechKey = colorID + "_mf.png";
            if (unitHolder.getUnits().get(mechKey) != null && player.getFaction().equalsIgnoreCase("arborec")) {
                return true;
            }
        }
        
        if(player.getTechs().contains("mr") && (tile.getRepresentation().equalsIgnoreCase("Supernova") || tile.getRepresentation().equalsIgnoreCase("Nova Seed"))){
            return true;
        }
        boolean canBuildGFInSpace = false;
        

        return canBuildGFInSpace;
    }
    public static void resolveTACheck(Map activeMap, Player player, GenericInteractionCreateEvent event) {
        for(Player p2 : activeMap.getRealPlayers()){
            if(p2.getFaction().equalsIgnoreCase(player.getFaction())){
                continue;
            }
            if(p2.getPromissoryNotes().containsKey(player.getColor()+"_ta")){
                List<Button> buttons = new ArrayList<Button>();
                buttons.add(Button.success("useTA_"+player.getColor(), "Use TA"));
                buttons.add(Button.danger("deleteButtons", "Decline to use TA"));
                String message = Helper.getPlayerRepresentation(p2, activeMap, activeMap.getGuild(), true) +" a player who's TA you hold has refreshed their comms, would you like to play the TA?";
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(activeMap), message, buttons);
            }
        }
    }
    public static void drawStatusACs(Map activeMap, Player player, ButtonInteractionEvent event){
        if(activeMap.getCurrentAgendaInfo().contains(player.getFaction())){
            ButtonHelper.addReaction(event, true, false, "The bot thinks you already drew ACs this status phase. As such, it will not deal you more. Please draw manually if this is a mistake and let Fin know.", "");
            return;
        }
        String message = "";
        int amount = 1;
        activeMap.drawActionCard(player.getUserID());
        if(player.hasTech("nm")){
            message = " Neural motivator has been accounted for.";
            activeMap.drawActionCard(player.getUserID());
            amount = 2;
        }
        if(player.hasAbility("scheming")){
            message = message + " Scheming has been accounted for, please use blue button inside your card info thread to discard 1 AC.";
            activeMap.drawActionCard(player.getUserID());
            amount = amount + 1;
        }
        if(player.getRelics().contains("absol_codex")){
            amount = amount + 1;
            activeMap.drawActionCard(player.getUserID());
            message = message + " Absol Codex has been accounted for.";
        }
        
        for(String law : activeMap.getLaws().keySet()){
            if(law.equalsIgnoreCase("minister_policy")){
                if(activeMap.getLawsInfo().get(law).equalsIgnoreCase(player.getFaction()) && !player.hasAbility("scheming")){
                    message = message + " Minister of Policy has been accounted for. If this AC is political stability, you cannot play it at this time. ";
                    activeMap.drawActionCard(player.getUserID());
                    amount = amount + 1;
                }
            }
        }
        message = "Drew "+amount +" AC." + message;
        ACInfo.sendActionCardInfo(activeMap, player, event);
        if(player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeMap, "yssaril", event);
        }
        ButtonHelper.addReaction(event, true, false, message, "");
         ButtonHelper.checkACLimit(activeMap, event, player);
        activeMap.setCurrentAgendaInfo(activeMap.getCurrentAgendaInfo()+"_"+player.getFaction());
    }

    public static void resolveMinisterOfCommerceCheck(Map activeMap, Player player, GenericInteractionCreateEvent event) {
        resolveTACheck(activeMap, player, event);
        for(String law : activeMap.getLaws().keySet()){
            if(law.equalsIgnoreCase("minister_commrece") || law.equalsIgnoreCase("absol_minscomm")){
                if(activeMap.getLawsInfo().get(law).equalsIgnoreCase(player.getFaction())){
                    MessageChannel channel = event.getMessageChannel();
                    if(activeMap.isFoWMode()){
                        channel = player.getPrivateChannel();
                    }
                    int numOfNeighbors = Helper.getNeighbourCount(activeMap,player);
                    String message = Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true)+" Minister of Commerce triggered, your tgs have increased due to your "+numOfNeighbors+" neighbors ("+player.getTg()+"->"+(player.getTg()+numOfNeighbors)+")";
                    player.setTg(numOfNeighbors+player.getTg());
                    MessageHelper.sendMessageToChannel(channel, message);
                    ButtonHelperFactionSpecific.pillageCheck(player, activeMap);
                    
                }
            }
        }
    }

    


    public static int resolveOnActivationEnemyAbilities(Map activeMap, Tile activeSystem, Player player, boolean justChecking) {
        int numberOfAbilities = 0;
        
        String activePlayerident = Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true);
        MessageChannel channel = activeMap.getActionsChannel();
        if(justChecking){
            Player ghostPlayer = Helper.getPlayerFromColorOrFaction(activeMap,"ghost");
            if(ghostPlayer != null && ghostPlayer != player && ButtonHelper.getNumberOfUnitsOnTheBoard(activeMap, ghostPlayer, "mech") > 0){
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(activeMap), "This is a reminder that if you are moving via creuss wormhole, you should first pause and check if the creuss player wants to use their mech to move that wormhole. ");
            }
        }
        for(Player nonActivePlayer : activeMap.getPlayers().values()){
            
            if(!nonActivePlayer.isRealPlayer() || nonActivePlayer.isPlayerMemberOfAlliance(player) || nonActivePlayer.getFaction().equalsIgnoreCase(player.getFaction())){
                continue;
            }
            if(activeMap.isFoWMode()){
                channel = nonActivePlayer.getPrivateChannel();
            }
            String fincheckerForNonActive = "FFCC_"+nonActivePlayer.getFaction() + "_";
            String ident = Helper.getPlayerRepresentation(nonActivePlayer, activeMap, activeMap.getGuild(), true);
            //eres
            if(nonActivePlayer.getTechs().contains("ers") && FoWHelper.playerHasShipsInSystem(nonActivePlayer, activeSystem)){
                if(justChecking){
                        if(!activeMap.isFoWMode()){
                            MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger eres.");
                        }
                        numberOfAbilities++;
                }else{
                    int cTG = nonActivePlayer.getTg();
                    nonActivePlayer.setTg(cTG+4);
                    MessageHelper.sendMessageToChannel(channel, ident + " gained 4 tg ("+cTG+"->"+nonActivePlayer.getTg()+")");
                    ButtonHelperFactionSpecific.pillageCheck(nonActivePlayer, activeMap);
                }
            }
            //neuroglaive
            if(nonActivePlayer.getTechs().contains("ng") && FoWHelper.playerHasShipsInSystem(nonActivePlayer, activeSystem)){
                if(justChecking){
                        if(!activeMap.isFoWMode()){
                            MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger neuroglaive");
                        }
                        numberOfAbilities++;
                    }else{
                    int cTG = player.getFleetCC();
                    player.setFleetCC(cTG-1);
                    if(activeMap.isFoWMode()){
                        MessageHelper.sendMessageToChannel(channel, ident + " you triggered neuroglaive");
                        channel = player.getPrivateChannel();
                    }
                    MessageHelper.sendMessageToChannel(channel, activePlayerident + " lost 1 fleet cc due to neuroglaive ("+cTG+"->"+player.getFleetCC()+")");
                }
            }
            if(activeMap.playerHasLeaderUnlockedOrAlliance(nonActivePlayer, "arboreccommander") && Helper.playerHasProductionUnitInSystem(activeSystem, activeMap, nonActivePlayer)){
                if(justChecking){
                        if(!activeMap.isFoWMode()){
                            MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger the arborec commander");
                        }
                        numberOfAbilities++;
                    }else{
                    Button gainTG= Button.success(fincheckerForNonActive+"freelancersBuild_"+activeSystem.getPosition(), "Build 1 Unit");
                    Button Decline2 = Button.danger(fincheckerForNonActive+"deleteButtons", "Decline Commander");
                    List<Button> buttons = List.of(gainTG,Decline2);
                    MessageHelper.sendMessageToChannelWithButtons(channel, ident + " use buttons to resolve Arborec commander ", buttons);
                }
            }
            if(activeMap.playerHasLeaderUnlockedOrAlliance(nonActivePlayer, "yssarilcommander") && FoWHelper.playerHasUnitsInSystem(nonActivePlayer, activeSystem)){
                if(justChecking){
                        if(!activeMap.isFoWMode()){
                            MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger yssaril commander");
                        }
                        numberOfAbilities++;
                    }else{
                        Button lookAtACs= Button.success(fincheckerForNonActive+"yssarilcommander_ac_"+player.getFaction(), "Look at ACs");
                        Button lookAtPNs= Button.success(fincheckerForNonActive+"yssarilcommander_pn_"+player.getFaction(), "Look at PNs");
                        Button lookAtSOs= Button.success(fincheckerForNonActive+"yssarilcommander_so_"+player.getFaction(), "Look at SOs");
                        Button Decline2 = Button.danger(fincheckerForNonActive+"deleteButtons", "Decline Commander");
                        List<Button> buttons = List.of(lookAtACs, lookAtPNs, lookAtSOs,Decline2);
                        MessageHelper.sendMessageToChannelWithButtons(channel, ident + " use buttons to resolve Yssaril commander ", buttons);
                    }
            }
            List<String> pns = new ArrayList<String>();
            pns.addAll(player.getPromissoryNotesInPlayArea());
            for(String pn: pns){
                Player pnOwner = activeMap.getPNOwner(pn);
                if(!pnOwner.isRealPlayer() || !pnOwner.getFaction().equalsIgnoreCase(nonActivePlayer.getFaction())){
                    continue;
                }
                PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(pn);
                if(pnModel.getText().contains("return this card") && pnModel.getText().contains("you activate a system that contains") && FoWHelper.playerHasUnitsInSystem(pnOwner, activeSystem)){
                    if(justChecking){
                        if(!activeMap.isFoWMode()){
                            MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger the return of a PN");
                        }
                        numberOfAbilities++;
                    }else{
                        player.removePromissoryNote(pn);
                        nonActivePlayer.setPromissoryNote(pn);  
                        PNInfo.sendPromissoryNoteInfo(activeMap, nonActivePlayer, false);
		                PNInfo.sendPromissoryNoteInfo(activeMap, player, false);
                        MessageHelper.sendMessageToChannel(channel, pnModel.getName() + " was returned");
                    }

                }
            }
        }
        return numberOfAbilities;
    }
    public static boolean checkForTechSkipAttachments(Map activeMap, String planetName) {
        boolean techPresent = false;
        if(planetName.equalsIgnoreCase("custodiavigilia")){
            return false;
        }
        Tile tile = activeMap.getTile(AliasHandler.resolveTile(planetName));
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        Set<String> tokenList = unitHolder.getTokenList();
        if (CollectionUtils.containsAny(tokenList, "attachment_warfare.png", "attachment_cybernetic.png", "attachment_biotic.png", "attachment_propulsion.png")) {
            techPresent = true;
        }
        return techPresent;
    }
     public static  List<Button> getXxchaAgentReadyButtons(Map activeMap, Player player) {
        List<Button> buttons = new ArrayList<Button>();
        for(String planet : player.getExhaustedPlanets()){
            buttons.add(Button.success("refresh_"+planet, "Ready "+Helper.getPlanetRepresentation(planet, activeMap)));
        }
        buttons.add(Button.danger("deleteButtons_spitItOut", "Delete These Buttons"));
        return buttons;
     }
    public static void sendAllTechsNTechSkipPlanetsToReady(Map activeMap, GenericInteractionCreateEvent event, Player player) {
        List<Button> buttons = new ArrayList<Button>();
        for(String tech : player.getExhaustedTechs()){
            buttons.add(Button.success("biostimsReady_tech_"+tech, "Ready "+Mapper.getTechs().get(tech).getName()));
        }
        for(String planet : player.getExhaustedPlanets()){
            if((Mapper.getPlanet(planet).getTechSpecialties() != null && Mapper.getPlanet(planet).getTechSpecialties().size() > 0) || ButtonHelper.checkForTechSkipAttachments(activeMap, planet)){
                buttons.add(Button.success("biostimsReady_planet_"+planet, "Ready "+Helper.getPlanetRepresentation(planet, activeMap)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to select a planet or tech to ready", buttons);
    }
    public static void bioStimsReady(Map activeMap, GenericInteractionCreateEvent event, Player player, String buttonID) {
       buttonID = buttonID.replace("biostimsReady_", "");
       String last = buttonID.substring(buttonID.lastIndexOf("_")+1, buttonID.length());
       if(buttonID.contains("tech_")){
            player.refreshTech(last);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), Helper.getPlayerRepresentation(player, activeMap) + " readied tech: " + Helper.getTechRepresentation(last));
       }else{
            player.refreshPlanet(last);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), Helper.getPlayerRepresentation(player, activeMap) + " readied planet: " + Helper.getPlanetRepresentation(last, activeMap));
       }
    }

    public static void checkACLimit(Map activeMap, GenericInteractionCreateEvent event, Player player) {
        if(player.hasAbility("crafty")){
            return;
        }
        int limit = 7;
        if(activeMap.getLaws().containsKey("sanctions") && !activeMap.isAbsolMode()){
            limit = 3;
        }
        if(activeMap.getLaws().containsKey("absol_sanctions")){
            limit = 3;
            if(activeMap.getLawsInfo().get("absol_sanctions").equalsIgnoreCase(player.getFaction())){
                limit = 5;
            }
        }
        if(player.getRelics().contains("absol_codex")){
            limit = limit+5;
        }
        if(player.getAc() > limit){
            MessageChannel channel = activeMap.getMainGameChannel();
            if(activeMap.isFoWMode()){
                channel = player.getPrivateChannel();
            }
            String ident = Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true);
            MessageHelper.sendMessageToChannel(channel, ident+ " you are exceeding the AC hand limit of "+limit+". Please discard down to the limit. Check your cards info thread for the blue discard buttons. ");
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeMap), ident+" use buttons to discard", ACInfo.getDiscardActionCardButtons(activeMap, player, false));
        }
    }
    public static void updateMap(Map activeMap, GenericInteractionCreateEvent event) {
        String threadName = activeMap.getName() + "-bot-map-updates";
        List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
        boolean foundsomething = false;
        File file = GenerateMap.getInstance().saveImage(activeMap, DisplayType.all, event);
        if (!activeMap.isFoWMode()) {
            for (ThreadChannel threadChannel_ : threadChannels) {
                if (threadChannel_.getName().equals(threadName)) {
                    foundsomething = true;
                    if(activeMap.isFoWMode()){
                        MessageHelper.sendFileToChannel((MessageChannel) threadChannel_, file);
                    }else{
                        List<Button> buttonsWeb = new ArrayList<Button>();
                        Button linkToWebsite = Button.link("https://ti4.westaddisonheavyindustries.com/game/"+activeMap.getName(),"Website View");
                        buttonsWeb.add(linkToWebsite);
                        buttonsWeb.add(Button.success("cardsInfo","Cards Info"));
                        buttonsWeb.add(Button.secondary("showGameAgain","Show Game")); 
                        MessageHelper.sendFileToChannelWithButtonsAfter((MessageChannel) threadChannel_, file, "",buttonsWeb);
                    }
                }
            }
        } else {
            MessageHelper.sendFileToChannel(event.getMessageChannel(), file);
            foundsomething = true;
        }
        if (!foundsomething) {
            if(activeMap.isFoWMode()){
                MessageHelper.sendFileToChannel(event.getMessageChannel(), file);
            }else{
                List<Button> buttonsWeb = new ArrayList<Button>();
                Button linkToWebsite = Button.link("https://ti4.westaddisonheavyindustries.com/game/"+activeMap.getName(),"Website View");
                buttonsWeb.add(linkToWebsite);
                buttonsWeb.add(Button.success("cardsInfo","Cards Info"));
                buttonsWeb.add(Button.secondary("showGameAgain","Show Game"));
                MessageHelper.sendFileToChannelWithButtonsAfter(event.getMessageChannel(), file, "",buttonsWeb);
            }
        }

    }

     public static boolean NomadHeroAndDomOrbCheck(Player player, Map activeMap, Tile tile) {
        boolean isFSThere = false;
        if(activeMap.getDominusOrbStatus()){
            return true;
        }

        if(player.hasLeader("nomadhero")){
            Leader playerLeader = player.getLeader("nomadhero");
            if(playerLeader.isActive()){
                return true;
                
            }
        }

        return isFSThere;


        

     }

     public static int getAllTilesWithAlphaNBetaNUnits(Player player, Map activeMap){
        int count = 0;
        for(Tile tile : activeMap.getTileMap().values()){
            if(FoWHelper.playerHasUnitsInSystem(player, tile) && FoWHelper.doesTileHaveAlphaOrBeta(activeMap, tile.getPosition(), player)){
                count = count +1;
            }
        }
        return count;
     }

    public static void commanderUnlockCheck(Player player, Map activeMap, String faction, GenericInteractionCreateEvent event) {

        boolean shouldBeUnlocked = false;
        switch(faction){
            case "yssaril" -> {
                if(player.getActionCards().size() > 7 || (player.getExhaustedTechs().contains("mi") && player.getActionCards().size() > 6)){
                    shouldBeUnlocked = true;
                }
            }
            case "letnev" -> {
                shouldBeUnlocked = true;
            }
            case "hacan" -> {
                if(player.getTg() > 9){
                    shouldBeUnlocked = true;
                }
            }
            case "sardakk" -> {
                if(player.getPlanets().size() > 6){
                    shouldBeUnlocked = true;
                }
            }
            case "ghost" -> {
                if(ButtonHelper.getAllTilesWithAlphaNBetaNUnits(player, activeMap) > 2){
                    shouldBeUnlocked = true;
                }
            }
            case "sol" -> {
                int resources = 0;
                for(String planet : player.getPlanets()){
                    resources = resources + Helper.getPlanetResources(planet, activeMap);
                }
                if(resources > 11){
                    shouldBeUnlocked = true;
                }
            }
            case "xxcha" -> {
                int resources = 0;
                for(String planet : player.getPlanets()){
                    resources = resources + Helper.getPlanetInfluence(planet, activeMap);
                }
                if(resources > 11){
                    shouldBeUnlocked = true;
                }
            }
            case "mentak" -> {
                if(ButtonHelper.getNumberOfUnitsOnTheBoard(activeMap, player, "cruiser") > 3){
                    shouldBeUnlocked = true;
                }
            }
            case "l1z1x" -> {
                if(ButtonHelper.getNumberOfUnitsOnTheBoard(activeMap, player, "dreadnought") > 3){
                    shouldBeUnlocked = true;
                }
            }
            case "argent" -> {
                int num = ButtonHelper.getNumberOfUnitsOnTheBoard(activeMap, player, "pds") + ButtonHelper.getNumberOfUnitsOnTheBoard(activeMap, player, "dreadnought") + ButtonHelper.getNumberOfUnitsOnTheBoard(activeMap, player, "destroyer");
                if(num> 5){
                    shouldBeUnlocked = true;
                }
            }
            case "titans" -> {
                int num = ButtonHelper.getNumberOfUnitsOnTheBoard(activeMap, player, "pds") +  ButtonHelper.getNumberOfUnitsOnTheBoard(activeMap, player, "spacedock");
                if(num> 4){
                    shouldBeUnlocked = true;
                }
            }
            case "cabal" -> {
                int num = ButtonHelper.getNumberOfUnitsOnTheBoard(activeMap, player, "csd");
                if(num> 2){
                    shouldBeUnlocked = true;
                }
            }
            case "nekro" -> {
                if(player.getTechs().size()> 4){
                    shouldBeUnlocked = true;
                }
            }
            case "jolnar" -> {
                if(player.getTechs().size()> 7){
                    shouldBeUnlocked = true;
                }
            }
            case "saar" -> {
                if(ButtonHelper.getNumberOfUnitsOnTheBoard(activeMap, player, "spacedock") > 2){
                    shouldBeUnlocked = true;
                }
            }
            case "naaz" -> {
                if(ButtonHelper.getTilesOfPlayersSpecificUnit(activeMap, player, "mech").size() > 2){
                    shouldBeUnlocked = true;
                }
            }
            case "nomad" -> {
                if(player.getSoScored() > 0){
                    shouldBeUnlocked = true;
                }
            }
            case "mahact" -> {
                if(player.getMahactCC().size() > 1){
                    shouldBeUnlocked = true;
                }
            }
            case "empyrean" -> {
                if(Helper.getNeighbourCount(activeMap, player) > (activeMap.getRealPlayers().size()-2)){
                    shouldBeUnlocked = true;
                }
            }
            case "muaat" -> {
                shouldBeUnlocked = true;
            }
            case "winnu" -> {
                shouldBeUnlocked = true;
            }
            case "keleres" -> {
                shouldBeUnlocked = true;
            }
            case "arborec" -> {
                int num = ButtonHelper.getAmountOfSpecificUnitsOnPlanets(player, activeMap, "infantry") +  ButtonHelper.getAmountOfSpecificUnitsOnPlanets(player, activeMap, "mech");
                if(num> 11){
                    shouldBeUnlocked = true;
                }
            }
            // missing: yin, ghost, cabal, naalu,letnev
        }
        if(shouldBeUnlocked){
            new UnlockLeader().unlockLeader(event, faction + "commander", activeMap, player);
        }
    }
    public static List<String> getPlanetsWithSleeperTokens(Player player, Map activeMap, Tile tile) {
        List<String> planetsWithSleepers = new ArrayList<String>();
        for(UnitHolder unitHolder :tile.getUnitHolders().values()){
            if(unitHolder instanceof Planet planet){
                if(planet.getTokenList().contains(Constants.TOKEN_SLEEPER_PNG)){
                    planetsWithSleepers.add(planet.getName());
                }
            }
        }
        return planetsWithSleepers;
    }
    public static int getAmountOfSpecificUnitsOnPlanets(Player player, Map activeMap, String unit) {
        int num = 0;
        for(Tile tile : activeMap.getTileMap().values()){
            for(UnitHolder unitHolder :tile.getUnitHolders().values()){
                if(unitHolder instanceof Planet planet){
                    String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unit), player.getColor());
                    if(planet.getUnits().keySet().contains(unitID)){
                        num = num + planet.getUnits().get(unitID);
                    }
                }
            }
        }
        return num;
    }
    
    
    public static List<String> getPlanetsWithSpecificUnit(Player player, Map activeMap, Tile tile,String unit) {
        List<String> planetsWithUnit = new ArrayList<String>();
        for(UnitHolder unitHolder :tile.getUnitHolders().values()){
            if(unitHolder instanceof Planet planet){
                if(planet.getUnits().keySet().contains(Mapper.getUnitID(AliasHandler.resolveUnit(unit), player.getColor()))){
                    planetsWithUnit.add(planet.getName());
                }
            }
        }
        return planetsWithUnit;
    }
    public static List<String> getAllPlanetsWithSleeperTokens(Player player, Map activeMap) {
        List<String> planetsWithSleepers = new ArrayList<String>();
        for(Tile tile :activeMap.getTileMap().values()){
            planetsWithSleepers.addAll(getPlanetsWithSleeperTokens(player, activeMap, tile));
        }
        return planetsWithSleepers;
    }
    public static void doButtonsForSleepers(Player player, Map activeMap, Tile tile, ButtonInteractionEvent event) {
         String finChecker = "FFCC_"+player.getFaction() + "_";
       
        for(String planet : ButtonHelper.getPlanetsWithSleeperTokens(player, activeMap, tile)){
            List<Button> planetsWithSleepers = new ArrayList<Button>();
            planetsWithSleepers.add(Button.success(finChecker+"replaceSleeperWith_pds_"+planet, "Replace sleeper on "+planet+ " with a pds."));
            if(ButtonHelper.getNumberOfUnitsOnTheBoard(activeMap, player, "mech") < 4){
                planetsWithSleepers.add(Button.success(finChecker+"replaceSleeperWith_mech_"+planet, "Replace sleeper on "+planet+ " with a mech and an infantry."));
            }
            planetsWithSleepers.add(Button.danger("deleteButtons", "Delete these buttons"));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to resolve sleeper", planetsWithSleepers);
        }
        
        
    }
    public static List<Button> getButtonsForTurningPDSIntoFS(Player player, Map activeMap, Tile tile) {
         String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> planetsWithPDS = new ArrayList<Button>();
        for(String planet : ButtonHelper.getPlanetsWithSpecificUnit(player, activeMap, tile, "pds")){
            planetsWithPDS.add(Button.success(finChecker+"replacePDSWithFS_"+planet, "Replace pds on "+planet+ " with your flagship."));
        }
        planetsWithPDS.add(Button.danger("deleteButtons", "Delete these buttons"));
        return planetsWithPDS;
    }
    public static List<Button> getButtonsForRemovingASleeper(Player player, Map activeMap) {
         String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> planetsWithSleepers = new ArrayList<Button>();
        for(String planet : ButtonHelper.getAllPlanetsWithSleeperTokens(player, activeMap)){
            planetsWithSleepers.add(Button.success(finChecker+"removeSleeperFromPlanet_"+planet, "Remove the sleeper on "+planet+ "."));
        }
        planetsWithSleepers.add(Button.danger("deleteButtons", "Delete these buttons"));
        return planetsWithSleepers;
    }
    public static void resolveTitanShenanigansOnActivation(Player player, Map activeMap, Tile tile, ButtonInteractionEvent event) {
        List<Button> buttons = ButtonHelper.getButtonsForTurningPDSIntoFS(player, activeMap, tile);
        if(buttons.size() > 1){
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to decide which pds to replace with your flagship", buttons);
        }
       ButtonHelper.doButtonsForSleepers(player, activeMap, tile, event);
    }
    public static List<Player> getOtherPlayersWithShipsInTheSystem(Player player, Map activeMap, Tile tile) {
        List<Player> playersWithShips = new ArrayList<>();
        for(Player p2 : activeMap.getPlayers().values()){
            if(p2 == player || !p2.isRealPlayer()){
                continue;
            }
            if(FoWHelper.playerHasShipsInSystem(p2, tile)){
                playersWithShips.add(p2);
            }
        }
        return playersWithShips;
    }
    public static List<Player> getPlayersWithUnitsOnAPlanet(Map activeMap, Tile tile, String planet) {
        List<Player> playersWithShips = new ArrayList<>();
        for(Player p2 : activeMap.getPlayers().values()){
            if(FoWHelper.playerHasUnitsOnPlanet(p2, tile, planet)){
                playersWithShips.add(p2);
            }
        }
        return playersWithShips;
    }

    public static List<Tile> getTilesWithYourCC(Player player, Map activeMap, GenericInteractionCreateEvent event) {
        List<Tile> tilesWithCC = new ArrayList<>();
        for (java.util.Map.Entry<String, Tile> tileEntry : new HashMap<>(activeMap.getTileMap()).entrySet()) {
			if (AddCC.hasCC(event, player.getColor(), tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                tilesWithCC.add(tile);
			}
		}
        return tilesWithCC;
    }
    public static void resolveRemovingYourCC(Player player, Map activeMap, GenericInteractionCreateEvent event, String buttonID) {
        buttonID = buttonID.replace("removeCCFromBoard_","");
        String whatIsItFor = buttonID.split("_")[0];
        String pos = buttonID.split("_")[1];
        Tile tile = activeMap.getTileByPosition(pos);
        String tileRep = tile.getRepresentationForButtons(activeMap, player);
        String ident = Helper.getFactionIconFromDiscord(player.getFaction());
        String msg = ident+" removed CC from "+tileRep;
        if(whatIsItFor.contains("mahactAgent")){
            String faction = whatIsItFor.replace("mahactAgent", "");
            player = Helper.getPlayerFromColorOrFaction(activeMap, faction);
            msg =  ButtonHelper.getTrueIdentity(player, activeMap) + " " +msg + " using Mahact agent";
            
            
            
        }
        RemoveCC.removeCC(event, player.getColor(), tile, activeMap);
         
        
        String finChecker = "FFCC_"+player.getFaction() + "_";
        if(whatIsItFor.equalsIgnoreCase("mahactCommander")){
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident+ "reduced their tactic CCs from " + player.getTacticalCC() +" to "+ (player.getTacticalCC()-1));
            player.setTacticalCC(player.getTacticalCC()-1);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            List<Button> conclusionButtons = new ArrayList<Button>();
            Button endTurn = Button.danger(finChecker+"turnEnd", "End Turn");
            conclusionButtons.add(endTurn);
            if(ButtonHelper.getEndOfTurnAbilities(player, activeMap).size()> 1){
                conclusionButtons.add(Button.primary("endOfTurnAbilities", "Do End Of Turn Ability ("+(ButtonHelper.getEndOfTurnAbilities(player, activeMap).size()- 1)+")"));
            }
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use the buttons to end turn.", conclusionButtons);
        }else{
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap), msg);
        }
        if(whatIsItFor.equalsIgnoreCase("warfare")){
             List<Button> redistributeButton = new ArrayList<Button>();
            Button redistribute= Button.success("FFCC_"+player.getFaction()+"_"+"redistributeCCButtons", "Redistribute & Gain CCs");
            Button deleButton= Button.danger("FFCC_"+player.getFaction()+"_"+"deleteButtons", "Delete These Buttons");
            redistributeButton.add(redistribute);
            redistributeButton.add(deleButton);
            MessageHelper.sendMessageToChannelWithButtons((MessageChannel)player.getCardsInfoThread(activeMap), Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), false) +" click this after picking up a CC.", redistributeButton);
        }

    }

    public static int checkNetGain(Player player, String ccs) {
        int netgain = 0;
        int oldTactic = Integer.parseInt(ccs.substring(0, ccs.indexOf("/")));
        ccs = ccs.substring(ccs.indexOf("/")+1, ccs.length());
        int oldFleet = Integer.parseInt(ccs.substring(0, ccs.indexOf("/")));
        ccs = ccs.substring(ccs.indexOf("/")+1, ccs.length());
        int oldStrat = Integer.parseInt(ccs.substring(0, ccs.length()));

        netgain = (player.getTacticalCC()-oldTactic) + (player.getFleetCC()-oldFleet) + (player.getStrategicCC() - oldStrat);
        return netgain;
    }


    public static List<Button> getButtonsToRemoveYourCC(Player player, Map activeMap, GenericInteractionCreateEvent event, String whatIsItFor) {
        List<Button> buttonsToRemoveCC = new ArrayList<>();
        String finChecker = "FFCC_"+player.getFaction() + "_";
        for (Tile tile : ButtonHelper.getTilesWithYourCC(player, activeMap, event)) {
			buttonsToRemoveCC.add(Button.success(finChecker+"removeCCFromBoard_"+whatIsItFor+"_"+tile.getPosition(), "Remove CC from "+tile.getRepresentationForButtons(activeMap, player)));
		}
        return buttonsToRemoveCC;
    }
    public static List<Button> getButtonsToSwitchWithAllianceMembers(Player player, Map activeMap, boolean fromButton) {
        List<Button> buttonsToRemoveCC = new ArrayList<Button>();
        for(Player player2 : activeMap.getRealPlayers()){
            if(player.getAllianceMembers().contains(player2.getFaction())){
                buttonsToRemoveCC.add(Button.success("swapToFaction_"+player2.getFaction(), "Swap to "+player2.getFaction()).withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord(player2.getFaction()))));
            }
        }
        if(fromButton){
            buttonsToRemoveCC.add(Button.danger("deleteButtons", "Delete These Buttons"));
        }
        
        return buttonsToRemoveCC;
    }

    
    public static List<Button> getButtonsToExploreAllPlanets(Player player, Map activeMap){
        List<Button> buttons = new ArrayList<Button>();
        for(String plan : player.getPlanets()){
            UnitHolder planetUnit = activeMap.getPlanetsInfo().get(plan);
            Planet planetReal =  (Planet) planetUnit;    
            if (planetReal != null && planetReal.getOriginalPlanetType() != null) {
                List<Button> planetButtons = getPlanetExplorationButtons(activeMap, planetReal);
                buttons.addAll(planetButtons);
            }
        }
        return buttons;
    }
    public static List<Button> getButtonsForAgentSelection(Map activeMap, String agent){
        List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(activeMap, null, "exhaustAgent_"+agent, null);
        return buttons;
    }
    public static void makeACombatThread(Map activeMap, MessageChannel channel, Player p1, Player player2, String threadName, Tile tile, GenericInteractionCreateEvent event, String spaceOrGround){
        TextChannel textChannel = (TextChannel)channel;
        
        MessageCreateBuilder baseMessageObject = new MessageCreateBuilder().addContent("Resolve combat");
        channel.sendMessage(baseMessageObject.build()).queue(message_ -> {
            ThreadChannelAction threadChannel;
            boolean foundsomething = false;
            String longMsg = " Please resolve the interaction here. The first step is any pds fire or playing of experimental battle station. Then the playing of any start of combat or start of a combat round abilities (includes skilled retreat). Then the rolling of anti-fighter-barrage. Then the declaration of retreats (includes the playing of rout). Then the rolling of dice.";
            if(spaceOrGround.equalsIgnoreCase("ground")){
                longMsg = " Please resolve the interaction here. The first step is any start of invasion abilities like tekklar, blitz, bunker, or disable. Then its bombardment, then committing of ground forces, followed by a parley and ghost squad window, then start of combat/start of a round of combat abilities.";
            }

            for (ThreadChannel threadChannel_ : textChannel.getThreadChannels()) {
                if (threadChannel_.getName().equals(threadName)) {
                    foundsomething = true;
                    if(activeMap.isFoWMode()){
                        longMsg = Helper.getPlayerRepresentation(p1, activeMap, activeMap.getGuild(), true) + longMsg;
                    }else{
                        longMsg = Helper.getPlayerRepresentation(p1, activeMap, activeMap.getGuild(), true) + Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), false) + longMsg;
                    }
                    MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, longMsg);
                    List<Player> playersWithPds2 = null;
                    if(activeMap.isFoWMode() || spaceOrGround.equalsIgnoreCase("ground")){
                        playersWithPds2 = new ArrayList<Player>();
                    }else{
                        playersWithPds2 =  ButtonHelper.tileHasPDS2Cover(p1, activeMap, tile.getPosition());
                    }
                    int context = 0;
                    if(playersWithPds2.size()> 0){
                        context =1;
                    }
                    File systemWithContext = GenerateTile.getInstance().saveImage(activeMap, context, tile.getPosition(), event, p1);
                    MessageHelper.sendMessageWithFile((MessageChannel) threadChannel_, systemWithContext, "Picture of system", false);
                    List<Button> buttons = ButtonHelper.getButtonsForPictureCombats(activeMap,  tile.getPosition(), p1, player2, spaceOrGround);
                    MessageHelper.sendMessageToChannelWithButtons((MessageChannel) threadChannel_, "", buttons);
                    if (playersWithPds2.size() > 0 && !activeMap.isFoWMode() && spaceOrGround.equalsIgnoreCase("space")) {
                        String pdsMessage = "The following players have pds2 cover in the region:";
                        for(Player playerWithPds : playersWithPds2){
                            pdsMessage = pdsMessage + " "+Helper.getPlayerRepresentation(playerWithPds, activeMap, activeMap.getGuild(), false);
                        }
                        MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, pdsMessage);
                    }else{
                        if(activeMap.isFoWMode()){
                            MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, "In fog, it is the players responsibility to check for pds2");
                        }
                    }
                    break;
                }
                
            }

            if(!foundsomething){
                threadChannel = textChannel.createThreadChannel(threadName, message_.getId());
                if(activeMap.isFoWMode()){
                    threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_3_DAYS);
                }else{
                    threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR);
                }
                
                threadChannel.queue(m5 -> {
                List<ThreadChannel> threadChannels = textChannel.getThreadChannels();
                if (threadChannels != null) {
                    for (ThreadChannel threadChannel_ : threadChannels) {
                        if (threadChannel_.getName().equals(threadName)) {
                            String longMsg2 = " Please resolve the interaction here. The first step is any pds fire or playing of experimental battle station. Then the playing of any start of combat or start of a combat round abilities (includes skilled retreat). Then the rolling of anti-fighter-barrage. Then the declaration of retreats (includes the playing of rout). Then the rolling of dice.";
                            if(spaceOrGround.equalsIgnoreCase("ground")){
                                longMsg2 = " Please resolve the interaction here. The first step is any start of invasion abilities like tekklar, blitz, bunker, or disable. Then its bombardment, then committing of ground forces, followed by a parley and ghost squad window, then start of combat/start of a round of combat abilities.";
                            }
                            if(activeMap.isFoWMode()){
                                longMsg2 = Helper.getPlayerRepresentation(p1, activeMap, activeMap.getGuild(), true) + longMsg2;
                            }else{
                                longMsg2 = Helper.getPlayerRepresentation(p1, activeMap, activeMap.getGuild(), true) + Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), false) + longMsg2;
                            }
                            MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, longMsg2);
                            List<Player> playersWithPds2 = null;
                            if(activeMap.isFoWMode() || spaceOrGround.equalsIgnoreCase("ground")){
                                playersWithPds2 = new ArrayList<Player>();
                            }else{
                                playersWithPds2 =  ButtonHelper.tileHasPDS2Cover(p1, activeMap, tile.getPosition());
                            }
                            int context = 0;
                            if(playersWithPds2.size()> 0){
                                context =1;
                            }
                            File systemWithContext = GenerateTile.getInstance().saveImage(activeMap, context, tile.getPosition(), event, p1);
                            MessageHelper.sendMessageWithFile((MessageChannel) threadChannel_, systemWithContext, "Picture of system", false);
                            List<Button> buttons = ButtonHelper.getButtonsForPictureCombats(activeMap,  tile.getPosition(), p1, player2, spaceOrGround);
                            MessageHelper.sendMessageToChannelWithButtons((MessageChannel) threadChannel_, " ", buttons);
                            if (playersWithPds2.size() > 0 && !activeMap.isFoWMode() && spaceOrGround.equalsIgnoreCase("space")) {
                                String pdsMessage = "The following players have pds2 cover in the region:";
                                for(Player playerWithPds : playersWithPds2){
                                    pdsMessage = pdsMessage + " "+Helper.getPlayerRepresentation(playerWithPds, activeMap, activeMap.getGuild(), false);
                                }
                                MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, pdsMessage);
                            }else{
                                if(activeMap.isFoWMode()){
                                    MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, "In fog, it is the players responsibility to check for pds2");
                                }
                            }
                        }
                    }
                }
            });
            }
        });
    }
    public static List<Button> getButtonsForPictureCombats(Map activeMap, String pos, Player p1, Player p2, String groundOrSpace){
        Tile tile = activeMap.getTileByPosition(pos);
        List<Button> buttons = new ArrayList<>();
        
        if(groundOrSpace.equalsIgnoreCase("justPicture")){
            buttons.add(Button.primary("refreshViewOfSystem_"+pos+"_"+p1.getFaction()+"_"+p2.getFaction()+"_"+groundOrSpace, "Refresh Picture"));
            return buttons;
        }
        buttons.add(Button.danger("getDamageButtons_"+pos, "Assign Hits"));
        if(ButtonHelper.getButtonsForRepairingUnitsInASystem(p1, activeMap, tile).size()> 1 || ButtonHelper.getButtonsForRepairingUnitsInASystem(p2, activeMap, tile).size()> 1){
            buttons.add(Button.success("getRepairButtons_"+pos, "Repair Damage"));
        }
        buttons.add(Button.primary("refreshViewOfSystem_"+pos+"_"+p1.getFaction()+"_"+p2.getFaction()+"_"+groundOrSpace, "Refresh Picture"));
        Player titans = Helper.getPlayerFromUnlockedLeader(activeMap, "titansagent");
        if(!activeMap.isFoWMode() && titans != null && !titans.getLeaderByID("titansagent").isExhausted()){
            String finChecker = "FFCC_"+titans.getFaction() + "_";
            buttons.add(Button.secondary(finChecker+"exhaustAgent_titansagent", "Use Titans Agent").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("titans"))));
        }
        Player sol = Helper.getPlayerFromUnlockedLeader(activeMap, "solagent");
        
        Player letnev = Helper.getPlayerFromUnlockedLeader(activeMap, "letnevagent");
        if((!activeMap.isFoWMode() || letnev == p1) &&letnev != null && !letnev.getLeaderByID("letnevagent").isExhausted()&& groundOrSpace.equalsIgnoreCase("space")){
            String finChecker = "FFCC_"+letnev.getFaction() + "_";
            buttons.add(Button.secondary(finChecker+"exhaustAgent_letnevagent", "Use Letnev Agent").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("letnev"))));
        }
        Player nomad = Helper.getPlayerFromUnlockedLeader(activeMap, "nomadagentthundarian");
        if((!activeMap.isFoWMode() || nomad ==p1) &&nomad != null && !nomad.getLeaderByID("nomadagentthundarian").isExhausted()){
            String finChecker = "FFCC_"+nomad.getFaction() + "_";
            buttons.add(Button.secondary(finChecker+"exhaustAgent_nomadagentthundarian", "Use Thundarian").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("nomad"))));
        }
        Player yin = Helper.getPlayerFromUnlockedLeader(activeMap, "yinagent");
        if((!activeMap.isFoWMode() || yin == p1) &&yin != null && !yin.getLeaderByID("yinagent").isExhausted()){
            String finChecker = "FFCC_"+yin.getFaction() + "_";
            buttons.add(Button.secondary(finChecker+"yinagent_"+pos, "Use Yin Agent").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("yin"))));
        }
        if(p1.hasAbility("technological_singularity")){
            String finChecker = "FFCC_"+p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker+"nekroStealTech_"+p2.getFaction(), "Steal Tech").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("nekro"))));
        }
        if(p2.hasAbility("technological_singularity") && !activeMap.isFoWMode()){
            String finChecker = "FFCC_"+p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker+"nekroStealTech_"+p1.getFaction(), "Steal Tech").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("nekro"))));
        }
        if((p2.hasAbility("edict") || p2.hasAbility("imperia"))  && !activeMap.isFoWMode()){
            String finChecker = "FFCC_"+p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker+"mahactStealCC_"+p1.getColor(), "Add Opponent CC to Fleet").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("mahact"))));
        }
        if(p1.hasAbility("edict") || p1.hasAbility("imperia")){
            String finChecker = "FFCC_"+p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker+"mahactStealCC_"+p2.getColor(), "Add Opponent CC to Fleet").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("mahact"))));
        }
        if(!activeMap.isFoWMode() &&sol != null && !sol.getLeaderByID("solagent").isExhausted() && groundOrSpace.equalsIgnoreCase("ground")){
            String finChecker = "FFCC_"+sol.getFaction() + "_";
            buttons.add(Button.secondary(finChecker+"exhaustAgent_solagent", "Use Sol Agent").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("sol"))));
        }
        if(groundOrSpace.equalsIgnoreCase("space")){
            buttons.add(Button.danger("retreat_"+pos, "Retreat"));
        }
        for(UnitHolder unitH : tile.getUnitHolders().values()){
            String nameOfHolder = "Space";
            if(unitH instanceof Planet){
                nameOfHolder  = Helper.getPlanetRepresentation(unitH.getName(), activeMap);
            }
             buttons.add(Button.secondary("combatRoll_"+pos+"_"+unitH.getName(), "Roll Basic Combat ("+nameOfHolder+")"));
        }


        
        return buttons;
    }
     public static void checkFleetAndCapacity(Player player, Map activeMap, Tile tile, GenericInteractionCreateEvent event) {
        
        int armadaValue = 0;
        if(player.hasAbility("armada")){
            armadaValue = 2;
        }
        int fleetCap = (player.getFleetCC() + armadaValue+player.getMahactCC().size())*2;
        if(player.hasLeader("letnevhero")){
            Leader playerLeader = player.getLeader("letnevhero");
            if(playerLeader.isActive()){
                fleetCap = 1000;
            }
        }
        int capacity = 0;
        int numInfNFightersNMechs = 0;
        int numOfCapatitalShips = 0;
        int fightsIgnored = 0;
        int numFighter2s = 0;
        int numFighter2sFleet = 0;
        boolean capacityViolated = false;
        boolean fleetSupplyViolated = false;

        for(UnitHolder capChecker : tile.getUnitHolders().values()){
            java.util.HashMap<UnitModel, Integer> unitsByQuantity = CombatHelper.GetAllUnits(capChecker, player, event);
            for(UnitModel unit : unitsByQuantity.keySet()){
                if(capChecker.getName().equalsIgnoreCase("space")){
                    capacity = capacity + (unit.getCapacityValue()*unitsByQuantity.get(unit));
                }
                 if(unit.getBaseType().equalsIgnoreCase("spacedock") && !capChecker.getName().equalsIgnoreCase("space")){
                    if(unit.getId().equalsIgnoreCase("cabal_spacedock")){
                        fightsIgnored = fightsIgnored + 6;
                        
                    }else if(unit.getId().equalsIgnoreCase("cabal_spacedock2")){
                        fightsIgnored = fightsIgnored + 12;
                    }else{
                        fightsIgnored = fightsIgnored + 3;
                        
                    }
                 }
                
            }
        }
        UnitHolder combatOnHolder = tile.getUnitHolders().get("space");
        java.util.HashMap<UnitModel, Integer> unitsByQuantity = CombatHelper.GetAllUnits(combatOnHolder, player, event);
        for(UnitModel unit : unitsByQuantity.keySet()){
            if(unit.getBaseType().equalsIgnoreCase("fighter") || unit.getBaseType().equalsIgnoreCase("infantry") || unit.getBaseType().equalsIgnoreCase("mech")){
                if(unit.getBaseType().equalsIgnoreCase("fighter")&& player.hasFF2Tech()){
                    numFighter2s  = unitsByQuantity.get(unit) -fightsIgnored;
                    if(numFighter2s < 0){
                        numFighter2s = 0;
                    }
                }
                if(unit.getBaseType().equalsIgnoreCase("fighter")){
                    int numCountedFights = unit.getCapacityUsed()*unitsByQuantity.get(unit) - fightsIgnored;
                    if(numCountedFights < 0){
                        numCountedFights = 0;
                    }
                    numInfNFightersNMechs =numInfNFightersNMechs + numCountedFights;
                }else{
                    numInfNFightersNMechs =numInfNFightersNMechs + unit.getCapacityUsed()*unitsByQuantity.get(unit);
                }
                
            }else{
                if((unit.getIsShip() != null && unit.getIsShip())){
                    if(player.hasAbility("capital_fleet") && unit.getBaseType().contains("destroyer")){
                        numOfCapatitalShips = numOfCapatitalShips + unitsByQuantity.get(unit);
                    }else{
                        numOfCapatitalShips = numOfCapatitalShips + unitsByQuantity.get(unit)*2;
                    }
                }
            }
            
            
            
        }
        if(numOfCapatitalShips > fleetCap){
            fleetSupplyViolated = true;
        }
        if(numInfNFightersNMechs > capacity){
            if(numInfNFightersNMechs - numFighter2s > capacity)
            {
                capacityViolated = true;
            }else{
                numFighter2s = numInfNFightersNMechs - capacity;
                if(player.hasTech("hcf2")){
                        numFighter2sFleet = numFighter2s;
                }else{
                    numFighter2sFleet = numFighter2s * 2;
                }
                if(numFighter2sFleet+numOfCapatitalShips > fleetCap){
                    fleetSupplyViolated = true;
                }
            }
        }
        if(numOfCapatitalShips > 4 && !fleetSupplyViolated){
            if(player.getLeaderIDs().contains("letnevcommander") && !player.hasLeaderUnlocked("letnevcommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "letnev", event);
            }
        }
        String message = ButtonHelper.getTrueIdentity(player, activeMap);
        if(fleetSupplyViolated){
            message = message + " You are violating fleet supply in tile "+tile.getRepresentation() +". ";
        }
        if(capacityViolated){
            message = message + " You are violating carrying capacity in tile "+tile.getRepresentation() +". ";
        }
        System.out.println(fleetCap + " "+numOfCapatitalShips + " "+capacity + " "+numInfNFightersNMechs);
        if(capacityViolated || fleetSupplyViolated){
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap), message);
        }

     }
    
    public static List<String> getAllPlanetsAdjacentToTileNotOwnedByPlayer(Tile tile, Map activeMap, Player player){
        List<String> planets = new ArrayList<String>();
        for(String pos2 : FoWHelper.getAdjacentTiles(activeMap, tile.getPosition(), player, false)){
            Tile tile2 = activeMap.getTileByPosition(pos2);
             for(UnitHolder planetUnit2 : tile2.getUnitHolders().values()){
                if(planetUnit2.getName().equalsIgnoreCase("space")){
                    continue;
                }
                Planet planetReal2 =  (Planet) planetUnit2;
                String planet2 = planetReal2.getName(); 
                if (planetReal2 != null && !player.getPlanets(activeMap).contains(planet2)) { 
                    planets.add(planet2);
                }
            }
        }
        return planets;
    }
    public static List<Button> customRexLegendary(Player player, Map activeMap){
        List<Button> buttons = new ArrayList<Button>();
        Tile rex = Helper.getTileFromPlanet("mr", activeMap);
        List<String> planetsToCheck = getAllPlanetsAdjacentToTileNotOwnedByPlayer(rex, activeMap, player);
        for(Player p2 : activeMap.getRealPlayers()){
            if(p2 == player){
                continue;
            }
            for(String planet2 : p2.getPlanets(activeMap)){
                PlanetModel mod = Mapper.getPlanet(planet2);
                if(mod.getLegendaryAbilityName() != null && !mod.getLegendaryAbilityName().equals("") && !planetsToCheck.contains(planet2)){
                    planetsToCheck.add(planet2);
                }
            }
        }
        for(String planet : planetsToCheck){
            UnitHolder planetUnit2 = activeMap.getPlanetsInfo().get(planet);
            if (planetUnit2 != null) { 
                for(Player p2 : activeMap.getRealPlayers()){
                    if(p2 == player){
                        continue;
                    }
                    int numMechs = 0;
                    int numInf = 0;
                    String colorID = Mapper.getColorID(p2.getColor());
                    String mechKey = colorID + "_mf.png";
                    String infKey = colorID + "_gf.png";
                    if (planetUnit2.getUnits() != null) {
                        if (planetUnit2.getUnits().get(mechKey) != null) {
                            numMechs =  planetUnit2.getUnits().get(mechKey);
                        }
                        if ( planetUnit2.getUnits().get(infKey) != null) {
                            numInf = planetUnit2.getUnits().get(infKey);
                        }
                    }
                    String planetId2 = planetUnit2.getName();
                    String planetRepresentation2 = Helper.getPlanetRepresentation(planetId2, activeMap);
                    if(numInf > 0){
                        buttons.add(Button.success("specialRex_"+planet+"_"+p2.getFaction()+"_infantry", "Remove 1 infantry from "+planetRepresentation2));
                    }
                    if(numMechs > 0){
                        buttons.add(Button.primary("specialRex_"+planet+"_"+p2.getFaction()+"_mech", "Remove 1 mech from "+planetRepresentation2));
                    }
                }
             }
        }

        return buttons;
    }
    public static void resolveSpecialRex(Player player, Map activeMap, String buttonID, String ident, ButtonInteractionEvent event){
        String planet = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = Helper.getPlayerFromColorOrFaction(activeMap, faction);
        String mechOrInf = buttonID.split("_")[3];
        String msg = ident + " used the special Mecatol Rex power to remove 1 "+mechOrInf + " on "+Helper.getPlanetRepresentation(planet, activeMap);
        new RemoveUnits().unitParsing(event, p2.getColor(), Helper.getTileFromPlanet(planet, activeMap), "1 "+mechOrInf + " "+planet, activeMap);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap), msg);
        event.getMessage().delete().queue();
    }
    
    public static List<Button> getEndOfTurnAbilities(Player player, Map activeMap) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> endButtons = new ArrayList<>();
        String planet = "mallice";
        if(player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet))
        {
            endButtons.add(Button.success(finChecker+"planetAbilityExhaust_"+planet, "Use Mallice Ability"));
        }
        planet = "mirage";
        if(player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet))
        {
            endButtons.add(Button.success(finChecker+"planetAbilityExhaust_"+planet, "Use Mirage Ability"));
        }
        planet = "hopesend";
        if(player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet))
        {
            endButtons.add(Button.success(finChecker+"planetAbilityExhaust_"+planet, "Use Hope's End Ability"));
        }
        planet = "primor";
        if(player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet))
        {
            endButtons.add(Button.success(finChecker+"planetAbilityExhaust_"+planet, "Use Primor Ability"));
        }
        planet = "mr";
        if(player.getPlanets().contains(planet) &&  !player.getExhaustedPlanetsAbilities().contains(planet) && activeMap.getPlanetsInfo().get("mr").getTokenList().contains("attachment_legendary.png"))
        {
            endButtons.add(Button.success(finChecker+"planetAbilityExhaust_"+planet, "Use Mecatol Rex Ability"));
        }
        if(player.getTechs().contains("pi") && !player.getExhaustedTechs().contains("pi")){
            endButtons.add(Button.danger(finChecker+"exhaustTech_pi", "Exhaust Predictive Intelligence"));
        }
        if(player.getTechs().contains("bs") && !player.getExhaustedTechs().contains("bs")){
            endButtons.add(Button.success(finChecker+"exhaustTech_bs", "Exhaust Bio-Stims"));
        }
        if(player.hasLeader("naazagent", activeMap)&& !player.getLeader("naazagent").isExhausted()){
            endButtons.add(Button.success(finChecker+"exhaustAgent_naazagent", "Use NRA Agent").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("naaz"))));
        }

        endButtons.add(Button.danger("deleteButtons", "Delete these buttons"));
        return endButtons;
    }
    public static List<Button> getStartOfTurnButtons(Player player, Map activeMap, boolean doneActionThisTurn, GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        activeMap.setDominusOrb(false);
        List<Button> startButtons = new ArrayList<>();
        Button tacticalAction = Button.success(finChecker+"tacticalAction", "Tactical Action ("+player.getTacticalCC()+")");
        int numOfComponentActions = ButtonHelper.getAllPossibleCompButtons(activeMap,player, event).size()-2;
        Button componentAction = Button.success(finChecker+"componentAction", "Component Action ("+numOfComponentActions+")");
        
        startButtons.add(tacticalAction);
        startButtons.add(componentAction);
        boolean hadAnyUnplayedSCs = false;
        for (Integer SC : player.getSCs()) {
            if (!activeMap.getPlayedSCs().contains(SC)) {
                hadAnyUnplayedSCs = true;
                if (activeMap.isHomeBrewSCMode()) {
                    Button strategicAction = Button.success(finChecker + "strategicAction_" + SC, "Play SC #" + SC);
                    startButtons.add(strategicAction);
                } else {
                    Button strategicAction = Button.success(finChecker + "strategicAction_" + SC, "Play SC #" + SC).withEmoji(Emoji.fromFormatted(Helper.getSCEmojiFromInteger(SC)));
                    startButtons.add(strategicAction);
                }
            }
        }

        
        if(!hadAnyUnplayedSCs && !doneActionThisTurn)
        {
            Button pass = Button.danger(finChecker+"passForRound", "Pass");
            if(ButtonHelper.getEndOfTurnAbilities(player, activeMap).size()> 1){
                startButtons.add(Button.primary("endOfTurnAbilities", "Do End Of Turn Ability ("+(ButtonHelper.getEndOfTurnAbilities(player, activeMap).size()- 1)+")"));
            }
            
            startButtons.add(pass);
            
        }
        if(doneActionThisTurn)
        {
            if(ButtonHelper.getEndOfTurnAbilities(player, activeMap).size()> 1){
                startButtons.add(Button.primary("endOfTurnAbilities", "Do End Of Turn Ability ("+(ButtonHelper.getEndOfTurnAbilities(player, activeMap).size()- 1)+")"));
            }
            Button pass = Button.danger(finChecker+"turnEnd", "End Turn");
            startButtons.add(pass);
        }else  {
            if(player.getTechs().contains("cm")){
                Button chaos = Button.secondary("startChaosMapping", "Use Chaos Mapping").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("saar")));
                startButtons.add(chaos);
            }
            if(player.hasTech("td") && !player.getExhaustedTechs().contains("td"))
            {
                Button transit = Button.secondary(finChecker+"exhaustTech_td", "Exhaust Transit Diodes");
                transit = transit.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("Cybernetictech")));
                startButtons.add(transit);
            }
            if(player.getStasisInfantry() > 0){
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeMap), "Use buttons to revive infantry. You have "+player.getStasisInfantry() + " infantry left to revive.", ButtonHelper.getPlaceStatusInfButtons(activeMap, player));

            }
        }

        Button transaction = Button.primary("transaction", "Transaction");
        startButtons.add(transaction);
        Button modify = Button.secondary("getModifyTiles", "Modify Units");
        startButtons.add(modify);
        if(activeMap.getLatestTransactionMsg() != null && !activeMap.getLatestTransactionMsg().equalsIgnoreCase(""))
        {
            activeMap.getMainGameChannel().deleteMessageById(activeMap.getLatestTransactionMsg()).queue();
            activeMap.setLatestTransactionMsg("");
        }
        if(activeMap.getActionCards().size() > 130 && ButtonHelper.getButtonsToSwitchWithAllianceMembers(player, activeMap, false).size() > 0){
            startButtons.addAll(ButtonHelper.getButtonsToSwitchWithAllianceMembers(player, activeMap, false));
        }
        

        return startButtons;
    }
    public static List<Button> getPossibleRings(Player player, Map activeMap) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> ringButtons = new ArrayList<>();
        Tile centerTile = activeMap.getTileByPosition("000");
        Button rex = Button.success(finChecker+"ringTile_000", centerTile.getRepresentationForButtons(activeMap, player));
        ringButtons.add(rex);
        int rings = activeMap.getRingCount();
        for(int x = 1; x < rings+1; x++)
        {
            Button ringX = Button.success(finChecker+"ring_"+x, "Ring #"+x);
            ringButtons.add(ringX);
        }
        Button corners = Button.success(finChecker+"ring_corners", "Corners");
        ringButtons.add(corners);
        return ringButtons;
    }
    public static List<Button> getTileInARing(Player player, Map activeMap, String buttonID, GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> ringButtons = new ArrayList<>();
        String ringNum = buttonID.replace("ring_", "");
        
        if(ringNum.equalsIgnoreCase("corners")){
            Tile tr = activeMap.getTileByPosition("tl");
            if(tr != null && !AddCC.hasCC(event, player.getColor(), tr)){
                Button corners = Button.success(finChecker+"ringTile_tl", tr.getRepresentationForButtons(activeMap, player));
                ringButtons.add(corners);
            }
            tr = activeMap.getTileByPosition("tr");
            if(tr != null&& !AddCC.hasCC(event, player.getColor(), tr)){
                Button corners = Button.success(finChecker+"ringTile_tr", tr.getRepresentationForButtons(activeMap, player));
                ringButtons.add(corners);
            }
            tr = activeMap.getTileByPosition("bl");
            if(tr != null&& !AddCC.hasCC(event, player.getColor(), tr)){
                Button corners = Button.success(finChecker+"ringTile_bl", tr.getRepresentationForButtons(activeMap, player));
                ringButtons.add(corners);
            }
            tr = activeMap.getTileByPosition("br");
            if(tr != null&& !AddCC.hasCC(event, player.getColor(), tr)){
                Button corners = Button.success(finChecker+"ringTile_br", tr.getRepresentationForButtons(activeMap, player));
                ringButtons.add(corners);
            }
        }
        else{
            
            int ringN = Integer.parseInt(ringNum.charAt(0)+"");
             if(ringNum.contains("_")){
                ringN = Integer.parseInt(ringNum.substring(0, ringNum.indexOf("_")));
             }else{
                ringN = Integer.parseInt(ringNum);
             }
            int totalTiles = ringN*6;
            if(ringNum.contains("_")){
                String side = ringNum.substring(ringNum.lastIndexOf("_")+1, ringNum.length());
                if(side.equalsIgnoreCase("left")){
                    for(int x = totalTiles/2; x < totalTiles+1; x++)
                    {
                        String pos = ringN+""+x;
                        Tile tile = activeMap.getTileByPosition(pos);
                        if(tile != null && !tile.getRepresentationForButtons(activeMap, player).contains("Hyperlane") && !AddCC.hasCC(event, player.getColor(), tile)){
                            Button corners = Button.success(finChecker+"ringTile_"+pos, tile.getRepresentationForButtons(activeMap, player));
                            ringButtons.add(corners);
                        }
                    }
                    String pos = ringN+"01";
                    Tile tile = activeMap.getTileByPosition(pos);
                    if(tile != null&& !tile.getRepresentationForButtons(activeMap, player).contains("Hyperlane")&& !AddCC.hasCC(event, player.getColor(), tile)){
                        Button corners = Button.success(finChecker+"ringTile_"+pos, tile.getRepresentationForButtons(activeMap, player));
                        ringButtons.add(corners);
                    }
                }
                else{
                    for(int x = 1; x < (totalTiles/2)+1; x++)
                    {
                        String pos = ringN+""+x;
                        if(x<10){
                            pos = ringN+"0"+x;
                        }
                        Tile tile = activeMap.getTileByPosition(pos);
                        if(tile != null&& !tile.getRepresentationForButtons(activeMap, player).contains("Hyperlane")&& !AddCC.hasCC(event, player.getColor(), tile)){
                            Button corners = Button.success(finChecker+"ringTile_"+pos, tile.getRepresentationForButtons(activeMap, player));
                            ringButtons.add(corners);
                        }
                    }
                }
            }
            else{

                if(ringN <5){
                    for(int x = 1; x < totalTiles+1; x++)
                    {
                        String pos = ringN+""+x;
                        if(x<10){
                            pos = ringN+"0"+x;
                        }
                        Tile tile = activeMap.getTileByPosition(pos);
                        if(tile != null&& !tile.getRepresentationForButtons(activeMap, player).contains("Hyperlane")&& !AddCC.hasCC(event, player.getColor(), tile)){
                            Button corners = Button.success(finChecker+"ringTile_"+pos, tile.getRepresentationForButtons(activeMap, player));
                            ringButtons.add(corners);
                        }
                    }
                }
                else{
                    Button ringLeft = Button.success(finChecker+"ring_"+ringN+"_left", "Left Half");
                    ringButtons.add(ringLeft);
                    Button ringRight = Button.success(finChecker+"ring_"+ringN+"_right", "Right Half");
                    ringButtons.add(ringRight);
                }
            }
        }
        ringButtons.add(Button.danger("ChooseDifferentDestination", "Get a different ring"));

        return ringButtons;
    }
    public static String getTrueIdentity(Player player, Map activeMap){
        return Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true);
    }
    public static void exploreDET(Player player, Map activeMap, ButtonInteractionEvent event) {
        Tile tile =  activeMap.getTileByPosition(activeMap.getActiveSystem());
        if(!FoWHelper.playerHasShipsInSystem(player, tile))
        {
            return;
        }
        if(player.hasTech("det") && tile.getUnitHolders().get("space").getTokenList().contains(Mapper.getTokenID(Constants.FRONTIER)))
        {
            if(player.hasAbility("voidsailors")){
                        String cardID = activeMap.drawExplore(Constants.FRONTIER);
                        String cardID2 = activeMap.drawExplore(Constants.FRONTIER);
                        String card = Mapper.getExplore(cardID);
                        String[] cardInfo1 = card.split(";");
                        String name1 = cardInfo1[0];
                        String card2 = Mapper.getExplore(cardID2);
                        String[] cardInfo2 = card2.split(";");
                        String name2 = cardInfo2[0];

                        Button resolveExplore1  = Button.success("resFrontier_"+cardID+"_"+tile.getPosition() + "_"+cardID2, "Choose "+name1);
                        Button resolveExplore2 = Button.success("resFrontier_"+cardID2+"_"+tile.getPosition() + "_"+cardID, "Choose "+name2);
                        List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                        //code to draw 2 explores and get their names
                        //Send Buttons to decide which one to explore
                        String message = Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true)+" Please decide which card to resolve.";

                        if (activeMap != null && !activeMap.isFoWMode() &&(event.getChannel() !=  activeMap.getActionsChannel())) {
                            
                            String pF = Helper.getFactionIconFromDiscord(player.getFaction());
                            MessageHelper.sendMessageToChannel(activeMap.getActionsChannel(), "Using Voidsailors,  " + pF + " found a "+name1+" and a " +name2+ " in "+tile.getRepresentation());
                            
                        }
                        else
                        {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Found a "+name1+" and a " +name2+ " in "+tile.getRepresentation());
                        }
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            }else{
                 new ExpFrontier().expFront(event, tile, activeMap, player);
            }
           
        }
    }
    

    public static boolean doesPlanetHaveAttachmentTechSkip(Tile tile, String planet){
        UnitHolder unitHolder = tile.getUnitHolders().get(planet);
        if(unitHolder.getTokenList().contains(Mapper.getAttachmentID(Constants.WARFARE)) ||
            unitHolder.getTokenList().contains(Mapper.getAttachmentID(Constants.CYBERNETIC)) ||
            unitHolder.getTokenList().contains(Mapper.getAttachmentID(Constants.BIOTIC)) ||
            unitHolder.getTokenList().contains(Mapper.getAttachmentID(Constants.PROPULSION)))
        {
            return true;
        }else{
            return false;
        }
    }

    public static List<Button> scanlinkResolution(Player player, Map activeMap, ButtonInteractionEvent event) {
        Tile tile =  activeMap.getTileByPosition(activeMap.getActiveSystem());
        List<Button> buttons = new ArrayList<Button>();
        for(UnitHolder planetUnit : tile.getUnitHolders().values()){
            if(planetUnit.getName().equalsIgnoreCase("space")){
                continue;
            }
            Planet planetReal =  (Planet) planetUnit;
            String planet = planetReal.getName();    
            if (planetReal != null && planetReal.getOriginalPlanetType() != null && player.getPlanets(activeMap).contains(planet) && FoWHelper.playerHasUnitsOnPlanet(player, tile, planet)) {
                List<Button> planetButtons = getPlanetExplorationButtons(activeMap, planetReal);
                buttons.addAll(planetButtons);
            }
        }
        return buttons;
    }

    public static List<Button> getPlanetExplorationButtons(Map activeMap, Planet planet) {
        if (planet == null || activeMap == null) return null;

        String planetType = planet.getOriginalPlanetType();
        String planetId = planet.getName();
        String planetRepresentation = Helper.getPlanetRepresentation(planetId, activeMap);
        List<Button> buttons = new ArrayList<>();
        Set<String> explorationTraits = new HashSet<>();
        if (planetType != null && (planetType.equalsIgnoreCase("industrial") || planetType.equalsIgnoreCase("cultural") || planetType.equalsIgnoreCase("hazardous"))) {
            explorationTraits.add(planetType);
        }
        if (planet.getTokenList().contains("attachment_titanspn.png")) {
            explorationTraits.add("cultural");
            explorationTraits.add("industrial");
            explorationTraits.add("hazardous");
        }
        if (planet.getTokenList().contains("attachment_industrialboom.png")) {
            explorationTraits.add("industrial");
        }
        
        for (String trait : explorationTraits) {
            String buttonId = "movedNExplored_filler_" + planetId + "_" + trait;
            String buttonMessage = "Explore " + planetRepresentation + (explorationTraits.size() > 1 ? " as " + trait : "");
            Emoji emoji = Emoji.fromFormatted(Helper.getEmojiFromDiscord(trait));
            Button button = Button.secondary(buttonId, buttonMessage).withEmoji(emoji);
            buttons.add(button);
        }
        return buttons;
    }
    public static void resolveEmpyCommanderCheck(Player player, Map activeMap, Tile tile, GenericInteractionCreateEvent event) {

        for(Player p2 : activeMap.getRealPlayers()){
            if(p2 != player && AddCC.hasCC(event, p2.getColor(), tile) && activeMap.playerHasLeaderUnlockedOrAlliance(p2, "empyreancommander")){
                MessageChannel channel = activeMap.getMainGameChannel();
                if(activeMap.isFoWMode()){
                    channel = p2.getPrivateChannel();
                }
                RemoveCC.removeCC(event, p2.getColor(), tile, activeMap);
                String message = Helper.getPlayerRepresentation(p2, activeMap, activeMap.getGuild(), true)+ " due to having the empyrean commander, the cc you had in the active system has been removed. Reminder that this is optional but was done automatically";
                MessageHelper.sendMessageToChannel(channel, message);
            }
        }
    }

    public static List<Tile> getTilesWithShipsInTheSystem(Player player, Map activeMap) {
        List<Tile> buttons = new ArrayList<>();
        for (java.util.Map.Entry<String, Tile> tileEntry : new HashMap<>(activeMap.getTileMap()).entrySet()) {
			if (FoWHelper.playerHasShipsInSystem(player, tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                buttons.add(tile);
			}
		}
        return buttons;
    }
    public static List<Button> getTilesToModify(Player player, Map activeMap, GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (java.util.Map.Entry<String, Tile> tileEntry : new HashMap<>(activeMap.getTileMap()).entrySet()) {
			if ( FoWHelper.playerIsInSystem(activeMap, tileEntry.getValue(), player)) {
                Tile tile = tileEntry.getValue();
                Button validTile = Button.success(finChecker+"genericModify_"+tileEntry.getKey(), tile.getRepresentationForButtons(activeMap, player));
                buttons.add(validTile);
			}
		}
        Button validTile2 = Button.danger(finChecker+"deleteButtons", "Delete these buttons");
        buttons.add(validTile2);
        return buttons;
    }
    public static void offerBuildOrRemove(Player player, Map activeMap, GenericInteractionCreateEvent event, Tile tile) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        Button buildButton = Button.success(finChecker+"genericBuild_"+tile.getPosition(), "Build in "+tile.getRepresentationForButtons(activeMap, player));
        buttons.add(buildButton);
        Button remove = Button.danger(finChecker+"getDamageButtons_"+tile.getPosition(), "Remove or damage units in "+tile.getRepresentationForButtons(activeMap, player));
        buttons.add(remove);
        Button validTile2 = Button.secondary(finChecker+"deleteButtons", "Delete these buttons");
        buttons.add(validTile2);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Choose to either add units (build) or remove them", buttons);
    }
     public static void resolveCombatRoll(Player player, Map activeMap, GenericInteractionCreateEvent event, String buttonID) {
        String[] idInfo = buttonID.split("_");
        String pos = idInfo[1];
        String unitHolderName = idInfo[2];
        new CombatRoll().secondHalfOfCombatRoll(player, activeMap, event, activeMap.getTileByPosition(pos), unitHolderName, new HashMap<String, Integer>(), new ArrayList<>());
    }
    public static MessageChannel getCorrectChannel(Player player, Map activeMap){
        if(activeMap.isFoWMode()){
            return player.getPrivateChannel();
        }else{
            return activeMap.getMainGameChannel();
        }
    }


    public static List<Button> getTilesToMoveFrom(Player player, Map activeMap, GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (java.util.Map.Entry<String, Tile> tileEntry : new HashMap<>(activeMap.getTileMap()).entrySet()) {
			if (FoWHelper.playerHasUnitsInSystem(player, tileEntry.getValue()) && (!AddCC.hasCC(event, player.getColor(), tileEntry.getValue()) || ButtonHelper.NomadHeroAndDomOrbCheck(player, activeMap, tileEntry.getValue()))) {
                Tile tile = tileEntry.getValue();
                Button validTile = Button.success(finChecker+"tacticalMoveFrom_"+tileEntry.getKey(), tile.getRepresentationForButtons(activeMap, player));
                buttons.add(validTile);
			}
		}
        if (player.hasLeader("saaragent", activeMap)&&!player.getLeaderByID("saaragent").isExhausted()) {
            Button saarButton = Button.secondary("exhaustAgent_saaragent", "Use Saar Agent").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("saar")));
            buttons.add(saarButton);
        }
        if (player.hasRelic("dominusorb")) {
            Button domButton = Button.secondary("dominusOrb", "Purge Dominus Orb");
            buttons.add(domButton);
        }
        if (player.hasLeader("ghostagent", activeMap)&&!player.getLeaderByID("ghostagent").isExhausted() && FoWHelper.doesTileHaveWHs(activeMap, activeMap.getActiveSystem(), player)) {
            Button ghostButton = Button.secondary("exhaustAgent_ghostagent", "Use Ghost Agent").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("ghost")));
            buttons.add(ghostButton);
        }
        Button validTile = Button.danger(finChecker+"concludeMove", "Done moving");
        buttons.add(validTile);
        Button validTile2 = Button.primary(finChecker+"ChooseDifferentDestination", "Activate a different system");
        buttons.add(validTile2);
        return buttons;
    }
    public static List<Button> moveAndGetLandingTroopsButtons(Player player, Map activeMap, ButtonInteractionEvent event) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        
        List<Button> buttons = new ArrayList<>();
        java.util.Map<String, Integer> displacedUnits =  activeMap.getMovedUnitsFromCurrentActivation();
        java.util.Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        Tile tile = activeMap.getTileByPosition(activeMap.getActiveSystem());
        tile = MoveUnits.flipMallice(event, tile, activeMap);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not flip Mallice");
            return buttons;
        }
        int cc = player.getTacticalCC();
        
        if (!activeMap.getNaaluAgent()&&!AddCC.hasCC(event, player.getColor(), tile)) {
            cc -= 1;
            player.setTacticalCC(cc);
            AddCC.addCC(event, player.getColor(), tile, true);
        }
        String thingToAdd = "box";
        for(String unit :displacedUnits.keySet()){
            int amount = displacedUnits.get(unit);
            if(unit.contains("damaged")){
                unit = unit.replace("damaged", "");
            }
            if(thingToAdd.equalsIgnoreCase("box")){
                thingToAdd = amount +" " +unit;
            }else{
                thingToAdd = thingToAdd + ", "+amount +" " +unit;
            }
        }
        if(!thingToAdd.equalsIgnoreCase("box")){
            new AddUnits().unitParsing(event, player.getColor(),
                    tile, thingToAdd, activeMap);
        }
        for(String unit :displacedUnits.keySet()){
            int amount = displacedUnits.get(unit);
            if(unit.contains("damaged")){
                unit = unit.replace("damaged", "");
                 String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unit), player.getColor()); 
                tile.addUnitDamage("space", unitID, amount);
            }
        }
        
        activeMap.resetCurrentMovedUnitsFrom1TacticalAction();
        String colorID = Mapper.getColorID(player.getColor());
        String mechKey = colorID + "_mf.png";
        String infKey = colorID + "_gf.png";
        
        for (java.util.Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null){
                representation = name;
            }
            UnitHolder unitHolder = entry.getValue();
            if (unitHolder instanceof Planet planet) {
                int limit = 0;
                
                if(tile.getUnitHolders().get("space").getUnits() != null && tile.getUnitHolders().get("space").getUnits().get(infKey) != null)
                {
                    limit = tile.getUnitHolders().get("space").getUnits().get(infKey);
                    for(int x = 1; x < limit +1; x++){
                        if(x > 2){
                            break;
                        }
                        Button validTile2 = Button.danger(finChecker+"landUnits_"+tile.getPosition()+"_"+x+"infantry_"+representation, "Land "+x+" Infantry on "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("infantry")));
                        buttons.add(validTile2);
                    }
                }
                if(planet.getUnits().get(infKey) != null){
                    limit = planet.getUnits().get(infKey);
                    for(int x = 1; x < limit +1; x++){
                        if(x > 2){
                            break;
                        }
                        Button validTile2 = Button.success(finChecker+"spaceUnits_"+tile.getPosition()+"_"+x+"infantry_"+representation, "Undo Landing of "+x+" Infantry on "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("infantry")));
                        buttons.add(validTile2);
                    }
                }
                
                if(tile.getUnitHolders().get("space").getUnits() != null && tile.getUnitHolders().get("space").getUnits().get(mechKey) != null)
                {
                    limit = tile.getUnitHolders().get("space").getUnits().get(mechKey);
                    for(int x = 1; x < limit +1; x++){
                        if(x > 2){
                            break;
                        }
                        Button validTile2 = Button.primary(finChecker+"landUnits_"+tile.getPosition()+"_"+x+"mech_"+representation, "Land "+x+" Mech(s) on "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("mech")));
                        buttons.add(validTile2);
                    }
                }

                if(planet.getUnits().get(mechKey) != null){
                    for(int x = 1; x < planet.getUnits().get(mechKey) +1; x++){
                        if(x > 2){
                            break;
                        }
                        Button validTile2 = Button.primary(finChecker+"spaceUnits_"+tile.getPosition()+"_"+x+"mech_"+representation, "Undo Landing of "+x+" Mech(s) on "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("mech")));
                        buttons.add(validTile2);
                    }
                }
            }
        }
        if(activeMap.playerHasLeaderUnlockedOrAlliance(player, "sardakkcommander")){
            buttons.addAll(ButtonHelperFactionSpecific.getSardakkCommanderButtons(activeMap, player, event));
        }
        Button rift = Button.success(finChecker+"getRiftButtons_"+tile.getPosition(), "Rift some units").withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("grift")));
        buttons.add(rift);
        Button concludeMove = Button.secondary(finChecker+"doneLanding", "Done landing troops");
        buttons.add(concludeMove);
        if(player.getLeaderIDs().contains("naazcommander") && !player.hasLeaderUnlocked("naazcommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "naaz", event);
        }
        if(player.getLeaderIDs().contains("empyreancommander") && !player.hasLeaderUnlocked("empyreancommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "empyrean", event);
        }
        if(player.getLeaderIDs().contains("ghostcommander") && !player.hasLeaderUnlocked("ghostcommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "ghost", event);
        }
        return buttons;
    }
    
    public static String putInfWithMechsForStarforge(String pos, String successMessage, Map activeMap, Player player, ButtonInteractionEvent event) {
        
        Set<String> tiles = FoWHelper.getAdjacentTiles(activeMap,pos,player, true);
        if(!tiles.contains(pos))
        {
            tiles.add(pos);
        }
        for(String tilePos : tiles){
            Tile tile = activeMap.getTileByPosition(tilePos);
            for(UnitHolder unitHolder :tile.getUnitHolders().values())
            {

                String colorID = Mapper.getColorID(player.getColor());
                String mechKey = colorID + "_mf.png";
                int numMechs = 0;
                if (unitHolder.getUnits() != null) {

                    if (unitHolder.getUnits().get(mechKey) != null) {
                        numMechs = unitHolder.getUnits().get(mechKey);
                    }
                    if(numMechs > 0)
                    {
                        String planetName = "";
                        if(!unitHolder.getName().equalsIgnoreCase("space")) {
                            planetName = " "+unitHolder.getName();
                        }
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, numMechs + " infantry"+planetName, activeMap);
                        
                        successMessage = successMessage + "\n Put "+numMechs +" "+Helper.getEmojiFromDiscord("infantry")+" with the mechs in "+tile.getRepresentationForButtons(activeMap,player);
                    }
                }
            }
        }

        return successMessage;

    }
   
    public static List<Button> landAndGetBuildButtons(Player player, Map activeMap, ButtonInteractionEvent event) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        java.util.Map<String, Integer> displacedUnits =  activeMap.getCurrentMovedUnitsFrom1System();
        java.util.Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        Tile tile = activeMap.getTileByPosition(activeMap.getActiveSystem());
        
        // for(String unit :displacedUnits.keySet()){
        //     int amount = displacedUnits.get(unit);
        //     String[] combo = unit.split("_");
        //     if(combo.length < 2){
        //         continue;
        //     }
        //     combo[1] = combo[1].toLowerCase().replace(" ", "");
        //     combo[1] = combo[1].replace("'", "");
        //     if(combo[0].contains("damaged")){
        //         combo[0]=combo[0].replace("damaged","");
        //         new AddUnits().unitParsing(event, player.getColor(),
        //             tile, amount +" " +combo[0]+" "+combo[1], activeMap);
        //         tile.addUnitDamage(combo[1], combo[0],amount);
        //     }else{
        //          new AddUnits().unitParsing(event, player.getColor(),
        //         tile, amount +" " +combo[0]+" "+combo[1], activeMap);
        //     }
           
        //     String key = Mapper.getUnitID(AliasHandler.resolveUnit(combo[0]), player.getColor());
        //     tile.removeUnit("space",key, amount);
        // }
        activeMap.resetCurrentMovedUnitsFrom1System();
        Button buildButton = Button.success(finChecker+"tacticalActionBuild_"+activeMap.getActiveSystem(), "Build in this system");
        buttons.add(buildButton);
        Button rift = Button.success(finChecker+"getRiftButtons_"+tile.getPosition(), "Rift some units").withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("grift")));
        buttons.add(rift);
        if(player.hasLeader("sardakkagent", activeMap)&&!player.getLeaderByID("sardakkagent").isExhausted()){
            buttons.addAll(ButtonHelperFactionSpecific.getSardakkAgentButtons(activeMap, player));
        }
        Button concludeMove = Button.danger(finChecker+"doneWithTacticalAction", "Conclude tactical action (will DET if applicable)");
        buttons.add(concludeMove);
        return buttons;
    }
    public static String getIdent(Player player){
        return Helper.getFactionIconFromDiscord(player.getFaction());
    }
    public static String getIdentOrColor(Player player, Map activeMap){
        if(activeMap.isFoWMode()){
            return StringUtils.capitalize(player.getColor());
        }
        return Helper.getFactionIconFromDiscord(player.getFaction());
    }
     
    public static String buildMessageFromDisplacedUnits(Map activeMap, boolean landing, Player player, String moveOrRemove) {
        String message = "";
        HashMap<String, Integer> displacedUnits =  activeMap.getCurrentMovedUnitsFrom1System();
        String prefix = " > "+Helper.getFactionIconFromDiscord(player.getFaction());
        
        for(String unit :displacedUnits.keySet())
        {
            int amount = displacedUnits.get(unit);
            String damagedMsg = "";
            if(unit.contains("damaged")){
                unit = unit.replace("damaged", "");
                damagedMsg = " damaged ";
            }
            String planet  = null;
            if(unit.contains("_")){
                planet = unit.substring(unit.lastIndexOf("_")+1, unit.length());
                unit = unit.replace("_"+planet, "");
            }
            if(landing)
            {
                message = message + prefix+" Landed "+amount + " "+damagedMsg +Helper.getEmojiFromDiscord(unit.toLowerCase());
                if(planet == null){
                    message = message + "\n";
                }
                else{
                    message = message + " on the planet "+Helper.getPlanetRepresentation(planet.toLowerCase(), activeMap)+"\n";
                }
            }
            else {
                message = message + prefix+" "+moveOrRemove+"d "+amount + " "+damagedMsg +Helper.getEmojiFromDiscord(unit.toLowerCase());
                if(planet == null){
                    message = message + "\n";
                }
                else{
                    message = message + " from the planet "+Helper.getPlanetRepresentation(planet.toLowerCase(), activeMap)+"\n";
                }
            }
            
        }
        if(message.equalsIgnoreCase("")){
            message = "Nothing moved.";
        }
        return message;
    }
    public static List<LayoutComponent> turnButtonListIntoActionRowList(List<Button> buttons) {
        final List<LayoutComponent> list = new ArrayList<>();
        List<ItemComponent> buttonRow = new ArrayList<ItemComponent>();
        for(Button button : buttons)
        {
            if(buttonRow.size() == 5)
            {
                list.add(ActionRow.of(buttonRow));
                buttonRow =  new ArrayList<ItemComponent>();
            }
            buttonRow.add(button);
        }
        if(buttonRow.size() > 0)
        {
            list.add(ActionRow.of(buttonRow));
        }
        return list;
    }

    public static String getUnitName(String id) {
        String name = "";
        switch(id){
            case "fs"-> name = "Flagship";
            case "ws"-> name = "Warsun";
            case "gf"-> name = "Infantry";
            case "mf"-> name = "Mech";
            case "sd"-> name = "Spacedock";
            case "csd"-> name = "cabalspacedock";
            case "pd"-> name = "pds";
            case "ff"-> name = "fighter";
            case "ca"-> name = "cruiser";
            case "dd"-> name = "destroyer";
            case "cv"-> name = "carrier";
            case "dn"-> name = "dreadnought";
        }
        return name;
    }
    public static List<Button> getButtonsForRiftingUnitsInSystem(Player player, Map activeMap, Tile tile) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();

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
            HashMap<String, Integer> units = unitHolder.getUnits();
           
            if (unitHolder instanceof Planet planet) {
                continue;
            }
            else{
                for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {

                    String key = unitEntry.getKey();
                   
                    if (  (!activeMap.playerHasLeaderUnlockedOrAlliance(player, "sardakkcommander") && (key.endsWith("gf.png") || key.endsWith("mf.png"))) || (!player.hasFF2Tech() && key.endsWith("ff.png"))) {
                        continue;
                    }
                   
                    for (String unitRepresentationKey : unitRepresentation.keySet()) {
                        if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {
                            
                            String unitKey = key.replace(cID+"_", "");
                            
                            int totalUnits = unitEntry.getValue();
                            unitKey  = unitKey.replace(".png", "");
                            unitKey = ButtonHelper.getUnitName(unitKey);
                            int damagedUnits = 0;
                            if(unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null){
                                damagedUnits = unitHolder.getUnitDamage().get(key);
                            }
                            for(int x = 1; x < damagedUnits +1; x++){
                                if(x > 2){
                                    break;
                                }
                                Button validTile2 = Button.danger(finChecker+"riftUnit_"+tile.getPosition()+"_"+x+unitKey+"damaged", "Rift "+x+" damaged "+unitRepresentation.get(unitRepresentationKey)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }
                            totalUnits = totalUnits-damagedUnits;
                            for(int x = 1; x < totalUnits +1; x++){
                                if(x > 2){
                                    break;
                                }
                                Button validTile2 = Button.danger(finChecker+"riftUnit_"+tile.getPosition()+"_"+x+unitKey, "Rift "+x+" "+unitRepresentation.get(unitRepresentationKey)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }
                        }
                    }
                }
            }
        }
        Button concludeMove = null;
        Button doAll = null;
        Button concludeMove1 = null;
        
        doAll = Button.secondary(finChecker+"riftAllUnits_"+tile.getPosition(), "Rift all units");
        concludeMove1 = Button.primary("getDamageButtons_"+tile.getPosition(), "Remove excess inf/ff");
        concludeMove = Button.danger("deleteButtons", "Done rifting units and removing excess capacity");
        
        buttons.add(doAll);
        buttons.add(concludeMove1);
        buttons.add(concludeMove);
        
        
        return buttons;
    }
    public static List<Button> getButtonsForAllUnitsInSystem(Player player, Map activeMap, Tile tile, String moveOrRemove) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();

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
            HashMap<String, Integer> units = unitHolder.getUnits();
           
            if (unitHolder instanceof Planet planet) {
                for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                    String key = unitEntry.getKey();
                    representation = representation.replace(" ", "").toLowerCase().replace("'","").replace("-","");
                    if ((key.endsWith("gf.png") || key.endsWith("mf.png")) &&key.contains(cID)) {
                        String unitKey = key.replace(cID+"_", "");
                        unitKey = unitKey.replace(".png", "");
                        unitKey = ButtonHelper.getUnitName(unitKey);
                        for(int x = 1; x < unitEntry.getValue() +1; x++){
                            if(x > 2){
                                break;
                            }
                            Button validTile2 = null;
                            if(key.contains("gf")){
                                 validTile2 = Button.danger(finChecker+"unitTactical"+moveOrRemove+"_"+tile.getPosition()+"_"+x+unitKey+"_"+representation, moveOrRemove+" "+x+" Infantry from "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("infantry")));
                            }
                            else{
                                 validTile2 = Button.danger(finChecker+"unitTactical"+moveOrRemove+"_"+tile.getPosition()+"_"+x+unitKey+"_"+representation, moveOrRemove+" "+x+" Mech from "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("mech")));
                            }
                            buttons.add(validTile2);
                        }
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
                            unitKey  = unitKey.replace(".png", "");
                            unitKey = ButtonHelper.getUnitName(unitKey);
                            int damagedUnits = 0;
                            if(unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null){
                                damagedUnits = unitHolder.getUnitDamage().get(key);
                            }
                            for(int x = 1; x < damagedUnits +1; x++){
                                if(x > 2){
                                    break;
                                }
                                Button validTile2 = Button.danger(finChecker+"unitTactical"+moveOrRemove+"_"+tile.getPosition()+"_"+x+unitKey+"damaged", moveOrRemove+" "+x+" damaged "+unitRepresentation.get(unitRepresentationKey)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }
                            totalUnits = totalUnits-damagedUnits;
                            for(int x = 1; x < totalUnits +1; x++){
                                if(x > 2){
                                    break;
                                }
                                Button validTile2 = Button.danger(finChecker+"unitTactical"+moveOrRemove+"_"+tile.getPosition()+"_"+x+unitKey, moveOrRemove+" "+x+" "+unitRepresentation.get(unitRepresentationKey)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }
                        }
                    }
                }
            }
            
           
            
        }
        Button concludeMove = null;
        Button doAll = null;
        if(moveOrRemove.equalsIgnoreCase("Remove")){
            doAll = Button.secondary(finChecker+"unitTactical"+moveOrRemove+"_"+tile.getPosition()+"_removeAll", "Remove all units");
            concludeMove = Button.primary(finChecker+"doneRemoving", "Done removing units");
        }else{
            doAll = Button.secondary(finChecker+"unitTactical"+moveOrRemove+"_"+tile.getPosition()+"_moveAll", "Move all units");
            concludeMove = Button.primary(finChecker+"doneWithOneSystem_"+tile.getPosition(), "Done moving units from this system");
        }
        buttons.add(doAll);
        buttons.add(concludeMove);
        HashMap<String, Integer> displacedUnits = activeMap.getCurrentMovedUnitsFrom1System();
        for(String unit :displacedUnits.keySet())
        {
            String unitkey = "";
            String planet = "";
            String origUnit = unit;
            String damagedMsg = "";
            if(unit.contains("damaged")){
                unit = unit.replace("damaged", "");
                damagedMsg = " damaged ";
            }
            if(unit.contains("_"))
            {
                unitkey =unit.split("_")[0];
                planet =unit.split("_")[1];
            }
            else{
                unitkey = unit;
            }
            for(int x = 1; x < displacedUnits.get(origUnit)+1; x++)
            {
                if(x > 2){
                    break;
                }
                String blabel =  "Undo move of "+x+" "+damagedMsg+unitkey;
                if(!planet.equalsIgnoreCase(""))
                {
                    blabel = blabel + " from "+Helper.getPlanetRepresentation(planet.toLowerCase(), activeMap);
                }
                Button validTile2 = Button.success(finChecker+"unitTactical"+moveOrRemove+"_"+tile.getPosition()+"_"+x+unit.toLowerCase().replace(" ", "").replace("'","")+damagedMsg.replace(" ","")+"_reverse",blabel).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitkey.toLowerCase().replace(" ", ""))));
                buttons.add(validTile2);
            }
        }
        if(displacedUnits.keySet().size() > 0){
            Button validTile2 = Button.success(finChecker+"unitTactical"+moveOrRemove+"_"+tile.getPosition()+"_reverseAll","Undo all");
            buttons.add(validTile2);
        }
        return buttons;
    }


public static List<Button> getButtonsForRemovingAllUnitsInSystem(Player player, Map activeMap, Tile tile) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
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
            HashMap<String, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet planet) {
                for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                    String key = unitEntry.getKey();
                   for (String unitRepresentationKey : unitRepresentation.keySet()) {
                        if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {
                            String unitKey = key.replace(cID+"_", "");
                            unitKey = unitKey.replace(".png", "");
                            unitKey = ButtonHelper.getUnitName(unitKey);
                            int damagedUnits = 0;
                            if(unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null){
                                damagedUnits = unitHolder.getUnitDamage().get(key);
                            }
                            int totalUnits = unitEntry.getValue() - damagedUnits;
                            for(int x = 1; x < totalUnits +1; x++){
                                if(x > 2){
                                    break;
                                }
                                Button validTile2 = Button.danger(finChecker+"assignHits_"+tile.getPosition()+"_"+x+unitKey+"_"+representation, "Remove "+x+" "+unitRepresentation.get(unitRepresentationKey) +" from "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                                if(key.contains("mf") || (key.contains("pd") && (player.getUnitsOwned().contains("Hel-Titan") ||player.getTechs().contains("ht2")) )){
                                    Button validTile3 = Button.secondary(finChecker+"assignDamage_"+tile.getPosition()+"_"+x+unitKey+"_"+representation, "Sustain "+x+" "+unitRepresentation.get(unitRepresentationKey) +
                                    " from "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                    buttons.add(validTile3);
                                }
                            }
                            for(int x = 1; x < damagedUnits +1; x++){
                                if(x > 2){
                                    break;
                                }
                                Button validTile2 = Button.danger(finChecker+"assignHits_"+tile.getPosition()+"_"+x+unitKey+"_"+representation+"damaged", "Remove "+x+" damaged "+
                                    unitRepresentation.get(unitRepresentationKey) + " from "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }
                        }
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
                            unitKey  = unitKey.replace(".png", "");
                            unitKey = ButtonHelper.getUnitName(unitKey);
                            int damagedUnits = 0;
                            if(unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null){
                                damagedUnits = unitHolder.getUnitDamage().get(key);
                            }
                            totalUnits = totalUnits-damagedUnits;
                            for(int x = 1; x < damagedUnits +1; x++){
                                if(x > 2){
                                    break;
                                }
                                Button validTile2 = Button.danger(finChecker+"assignHits_"+tile.getPosition()+"_"+x+unitKey+"damaged", "Remove "+x+" damaged "+
                                    unitRepresentation.get(unitRepresentationKey)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }
                            for(int x = 1; x < totalUnits +1; x++){
                                if(x > 2){
                                    break;
                                }
                                Button validTile2 = Button.danger(finChecker+"assignHits_"+tile.getPosition()+"_"+x+unitKey, "Remove "+x+" "+unitRepresentation.get(unitRepresentationKey)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }
                            if( ((unitKey.equalsIgnoreCase("mech") && !activeMap.getLaws().keySet().contains("articles_war") &&player.getUnitsOwned().contains("nomad_mech")) ||unitKey.equalsIgnoreCase("dreadnought") || unitKey.equalsIgnoreCase("warsun") || unitKey.equalsIgnoreCase("flagship") || (unitKey.equalsIgnoreCase("cruiser") && player.hasTech("se2")) || (unitKey.equalsIgnoreCase("carrier") && player.hasTech("ac2"))) && totalUnits > 0){
                                Button validTile2 = Button.secondary(finChecker+"assignDamage_"+tile.getPosition()+"_"+1+unitKey, "Sustain "+1+" "+unitRepresentation.get(unitRepresentationKey)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }
                            
                        }
                    }
                }
            }
        }
        Button doAll = Button.secondary(finChecker+"assignHits_"+tile.getPosition()+"_All", "Remove all units");
        Button concludeMove = Button.primary("deleteButtons", "Done removing/sustaining units");
        buttons.add(doAll);
        buttons.add(concludeMove);
        return buttons;
    }



    public static List<Button> getButtonsForRepairingUnitsInASystem(Player player, Map activeMap, Tile tile) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
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
            HashMap<String, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet planet) {
                for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                    String key = unitEntry.getKey();
                   for (String unitRepresentationKey : unitRepresentation.keySet()) {
                        if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {
                            String unitKey = key.replace(cID+"_", "");
                            unitKey = unitKey.replace(".png", "");
                            unitKey = ButtonHelper.getUnitName(unitKey);
                            int damagedUnits = 0;
                            if(unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null){
                                damagedUnits = unitHolder.getUnitDamage().get(key);
                            }
                            for(int x = 1; x < damagedUnits+1; x++){
                                if(x > 2){
                                    break;
                                }
                                Button validTile3 = Button.success(finChecker+"repairDamage_"+tile.getPosition()+"_"+x+unitKey+"_"+representation, "Repair "+x+" "+unitRepresentation.get(unitRepresentationKey) +
                                " from "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile3);            
                            }
                        }
                    }
                }
            }
            else{
                for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                    String key = unitEntry.getKey();
                    for (String unitRepresentationKey : unitRepresentation.keySet()) {
                        if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {
                            String unitKey = key.replace(cID+"_", "");
                            unitKey  = unitKey.replace(".png", "");
                            unitKey = ButtonHelper.getUnitName(unitKey);
                            int damagedUnits = 0;
                            if(unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null){
                                damagedUnits = unitHolder.getUnitDamage().get(key);
                            }
                            for(int x = 1; x < damagedUnits +1; x++){
                                if(x > 2){
                                    break;
                                }
                                Button validTile2 = Button.danger(finChecker+"repairDamage_"+tile.getPosition()+"_"+x+unitKey, "Repair "+x+" damaged "+
                                    unitRepresentation.get(unitRepresentationKey)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }
                            
                        }
                    }
                }
            }
        }
        Button concludeMove = Button.primary("deleteButtons", "Done repairing units");
        buttons.add(concludeMove);
        return buttons;
    }

    public static List<Player> tileHasPDS2Cover(Player player, Map activeMap, String tilePos) {
        
        Set<String> adjTiles = FoWHelper.getAdjacentTiles(activeMap, tilePos, player, false);
        List<Player> playersWithPds2 = new ArrayList<Player>();
        for (String tilePo : adjTiles) {
			Tile tile = activeMap.getTileByPosition(tilePo);
            for(UnitHolder area : tile.getUnitHolders().values())
            {
                for(Player p :activeMap.getPlayers().values())
                {
                    if(p.isRealPlayer() && !p.getFaction().equalsIgnoreCase(player.getFaction()))
                    {
                        String unitKey1 = Mapper.getUnitID(AliasHandler.resolveUnit("pds"), p.getColor());
                        if(area.getUnits().containsKey(unitKey1) && p.hasPDS2Tech())
                        {
                            if(!playersWithPds2.contains(p)){
                                playersWithPds2.add(p);
                            }
                        }
                        if(p.getUnitsOwned().contains("xxcha_mech"))
                        {
                           
                            String unitKey3 = Mapper.getUnitID(AliasHandler.resolveUnit("mech"), p.getColor());
                            if(area.getUnits().containsKey(unitKey3))
                            {
                                if(!playersWithPds2.contains(p)){
                                    playersWithPds2.add(p);
                                }
                            }
                        }
                        if(p.getUnitsOwned().contains("xxcha_flagship"))
                        {
                            String unitKey2 = Mapper.getUnitID(AliasHandler.resolveUnit("flagship"), p.getColor());
                            if(area.getUnits().containsKey(unitKey2) )
                            {
                                if(!playersWithPds2.contains(p)){
                                    playersWithPds2.add(p);
                                }
                            }
                        }
                    }
                }
            }
		}
        return playersWithPds2;
    }
    
    public static void fixRelics(Map activeMap) {
        for(Player player : activeMap.getPlayers().values()){
            if(player != null && player.getRelics() != null){
                List<String> rels = new ArrayList<String>();
                rels.addAll(player.getRelics());
                for(String relic : rels){
                    if(relic.contains("extra")){
                        player.removeRelic(relic);
                        relic = relic.replace("extra1", "");
                        relic = relic.replace("extra2", "");
                        player.addRelic(relic);
                    }
                }
            }
        }
    }
    //playerHasUnitsInSystem(player, tile);
     public static void startActionPhase(GenericInteractionCreateEvent event, Map activeMap) {

        Boolean privateGame = FoWHelper.isPrivateGame(activeMap, event);
        boolean isFowPrivateGame = (privateGame != null && privateGame);
        String msg = "";
        String msgExtra = "";
        boolean allPicked = true;
        Player privatePlayer = null;
        Collection<Player> activePlayers = activeMap.getPlayers().values().stream()
                .filter(player_ -> player_.isRealPlayer())
                .collect(Collectors.toList());
        StringBuilder sb = new StringBuilder();
        Player nextPlayer = null;
        int lowestSC = 100;
        msgExtra += Helper.getGamePing(event, activeMap) + "\nAll players picked SC";
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
            if(activeMap.isFoWMode()){
                FoWHelper.pingAllPlayersWithFullStats(activeMap, event, nextPlayer, "started turn");
            }
            ButtonHelperFactionSpecific.resolveMilitarySupportCheck(nextPlayer, activeMap);
            
            activeMap.setCurrentPhase("action");
        }

        msg = sb.toString();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
         if (isFowPrivateGame ) {
            if (allPicked) {
                msgExtra = "# " + Helper.getPlayerRepresentation(privatePlayer, activeMap, event.getGuild(), true) + " UP NEXT";
            }
            String fail = "User for next faction not found. Report to ADMIN";
            String success = "The next player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(privatePlayer, activeMap, event, msgExtra, fail, success);
            activeMap.updateActivePlayer(privatePlayer);
            
            if(!allPicked)
            {
                activeMap.setCurrentPhase("strategy");
                MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getPrivateChannel(), "Use Buttons to Pick SC", Helper.getRemainingSCButtons(event, activeMap, privatePlayer));
            }
            else{
                   
                MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getPrivateChannel(), msgExtra + "\n Use Buttons to do turn.", ButtonHelper.getStartOfTurnButtons(privatePlayer, activeMap, false, event));     
                    
                }

        } else {
            if (allPicked) {
                ListTurnOrder.turnOrder(event, activeMap);
            }
            if (!msgExtra.isEmpty()) {
                if(!allPicked && !activeMap.isHomeBrewSCMode())
                {
                    activeMap.updateActivePlayer(privatePlayer);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msgExtra+"\nUse Buttons to Pick SC", Helper.getRemainingSCButtons(event, activeMap, privatePlayer));
                    activeMap.setCurrentPhase("strategy");
                }
                else{
                    if(allPicked)
                    {
                        MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), msgExtra);
                        MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(), "\n Use Buttons to do turn.", ButtonHelper.getStartOfTurnButtons(privatePlayer, activeMap, false, event));

                    }
                    else
                    {
                        MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), msgExtra);
                    }
                }


            }
        }
     }

    public static void startStatusHomework(GenericInteractionCreateEvent event, Map activeMap) {
        int playersWithSCs = 0;
        for (Player player2 : activeMap.getPlayers().values()) {
            if (playersWithSCs > 1) {
                new Cleanup().runStatusCleanup(activeMap);
                MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), Helper.getGamePing(activeMap.getGuild(), activeMap) + "Status Cleanup Run!");
                playersWithSCs = -30;
                if(!activeMap.isFoWMode()){
                    DisplayType displayType = DisplayType.map;
                    File stats_file = GenerateMap.getInstance().saveImage(activeMap, displayType, event);
                    MessageHelper.sendFileToChannel(activeMap.getActionsChannel(), stats_file);
                }
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
                                + " Reminder this is the window to do Naalu Hero");
            }
            if (player2.getRelics() != null && player2.hasRelic("mawofworlds") && activeMap.isCustodiansScored()) {
                MessageHelper.sendMessageToChannel((MessageChannel) player2.getCardsInfoThread(activeMap),
                        Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)
                                + " Reminder this is the window to do Maw of Worlds");
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) player2.getCardsInfoThread(activeMap),
                        Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)
                                + " You can use these buttons to resolve Maw Of Worlds", ButtonHelper.getMawButtons());
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
                                        + "Reminder this is the window to purge Crown of Emphidia if you want to.");
                        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) player2.getCardsInfoThread(activeMap),
                        Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)
                                + " You can use these buttons to resolve Crown of Emphidia", ButtonHelper.getCrownButtons());
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
        String message2 = "Resolve status homework using the buttons. Only the Ready for [X] button is essential to hit, all others are optional. ";
        activeMap.setCurrentAgendaInfo("");
        Button draw1AC = Button.success("drawStatusACs", "Draw Status Phase ACs").withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
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
        List<Button> buttons = new ArrayList<Button>();
        if (activeMap.isFoWMode()) {
            buttons.add(draw1AC);
            buttons.add(getCCs);
            message2 = "Resolve status homework using the buttons";
            for (Player p1 : activeMap.getPlayers().values()) {
                if (p1 == null || p1.isDummy() || p1.getFaction() == null || p1.getPrivateChannel() == null) {
                    continue;
                } else {
                    MessageHelper.sendMessageToChannelWithButtons(p1.getPrivateChannel(), message2, buttons);

                }
            }
            buttons = new ArrayList<Button>();
            buttons.add(passOnAbilities);
        } else {

            
            buttons.add(draw1AC);
            buttons.add(getCCs);
            buttons.add(passOnAbilities);
        }
        if(activeMap.getActionCards().size() > 130 && Helper.getPlayerFromColorOrFaction(activeMap,"hacan") != null && ButtonHelper.getButtonsToSwitchWithAllianceMembers(Helper.getPlayerFromColorOrFaction(activeMap,"hacan"), activeMap, false).size() > 0){
            buttons.add(Button.secondary("getSwapButtons_", "Swap"));
        }
        MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(), message2, buttons);
    }
    public static void startStrategyPhase(GenericInteractionCreateEvent event, Map activeMap) {
        if(activeMap.isFoWMode()){
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Pinged speaker to pick SC.");
        }
        Player speaker = null;
        if (activeMap.getPlayer(activeMap.getSpeaker()) != null) {
            speaker = activeMap.getPlayers().get(activeMap.getSpeaker());
        } else {
            speaker = null;
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Speaker not found. Can't proceed");
            return;
        }
        String message = Helper.getPlayerRepresentation(speaker, activeMap, event.getGuild(), true)
                + " UP TO PICK SC\n";
        activeMap.updateActivePlayer(speaker);
        activeMap.setCurrentPhase("strategy");
        ButtonHelperFactionSpecific.giveKeleresCommsNTg(activeMap, event);
        if (activeMap.isFoWMode()) {
            if (!activeMap.isHomeBrewSCMode()) {
                MessageHelper.sendMessageToChannelWithButtons(speaker.getPrivateChannel(),
                        message + "Use Buttons to Pick SC", Helper.getRemainingSCButtons(event, activeMap, speaker));
            } else {
                MessageHelper.sendPrivateMessageToPlayer(speaker, activeMap, message);
            }
        } else {
            MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(), message + "Use Buttons to Pick SC", Helper.getRemainingSCButtons(event, activeMap, speaker));
        }
        for(Player player2 : activeMap.getRealPlayers()){
            if (player2.getActionCards() != null && player2.getActionCards().keySet().contains("summit")
                    && activeMap.isCustodiansScored()) {
                MessageHelper.sendMessageToChannel((MessageChannel) player2.getCardsInfoThread(activeMap),
                        Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)
                                + "Reminder this is the window to do summit");
            }
        }
    }
    public static List<Button> getMawButtons() {
         List<Button> playerButtons = new ArrayList<>();
        playerButtons.add(Button.success("resolveMaw", "Purge Maw of Worlds"));
        playerButtons.add(Button.danger("deleteButtons", "Decline"));
         return playerButtons;
    }
    public static List<Button> getCrownButtons() {
         List<Button> playerButtons = new ArrayList<>();
        playerButtons.add(Button.success("resolveCrownOfE", "Purge Crown"));
        playerButtons.add(Button.danger("deleteButtons", "Decline"));
        return playerButtons;
    }

    public static void resolveMaw(Map activeMap, Player player, ButtonInteractionEvent event) {

        player.removeRelic("mawofworlds");
        player.removeExhaustedRelic("mawofworlds");
        for (String planet : player.getPlanets()) {
            player.exhaustPlanet(planet);
        }
        activeMap.setComponentAction(true);
        Button getTech = Button.success("acquireATech", "Get a tech");
        List<Button> buttons = new ArrayList<Button>();
        buttons.add(getTech);
        buttons.add(Button.danger("deleteButtons", "Delete These"));
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap), Helper.getPlayerRepresentation(player, activeMap)+" purged Maw Of Worlds.");
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), Helper.getPlayerRepresentation(player, activeMap)+" Use the button to get a tech", buttons);
        event.getMessage().delete().queue();
    }
    public static void resolveCrownOfE(Map activeMap, Player player, ButtonInteractionEvent event) {
        player.removeRelic("emphidia");
        player.removeExhaustedRelic("emphidia");
        Integer poIndex = activeMap.addCustomPO("Crown of Emphidia", 1);
        activeMap.scorePublicObjective(player.getUserID(), poIndex);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap), Helper.getPlayerRepresentation(player, activeMap)+" scored Crown of Emphidia");
        event.getMessage().delete().queue();
    }

    public static List<Button> getPlayersToTransact(Map activeMap, Player p) {
        List<Button> playerButtons = new ArrayList<>();
        String finChecker = "FFCC_"+p.getFaction() + "_";
        for (Player player : activeMap.getPlayers().values()) {
            if (player.isRealPlayer()) {
                if(player.getFaction().equalsIgnoreCase(p.getFaction()))
                {
                    continue;
                }
                String faction = player.getFaction();
                if (faction != null && Mapper.isFaction(faction)) {
                    Button button = null;
                    if (!activeMap.isFoWMode()) {
                        button = Button.secondary(finChecker+"transactWith_"+faction, " ");
                        
                        String factionEmojiString = Helper.getFactionIconFromDiscord(faction);
                        button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                    } else {
                            button = Button.secondary(finChecker+"transactWith_"+player.getColor(), player.getColor());
                    }
                    playerButtons.add(button);
                }
                
            }
        }
        return playerButtons;
    }

    public static List<Button> getStuffToTransButtons(Map activeMap, Player p1, Player p2) {
        String finChecker = "FFCC_"+p1.getFaction() + "_";
        List<Button> stuffToTransButtons = new ArrayList<>();
        if(p1.getTg() > 0)
        {
            Button transact= Button.success(finChecker+"transact_TGs_"+p2.getFaction(), "TGs");
            stuffToTransButtons.add(transact);
        }
        if(p1.getCommodities() > 0)
        {
            Button transact = Button.success(finChecker+"transact_Comms_"+p2.getFaction(), "Commodities");
            stuffToTransButtons.add(transact);
        }
        if((p1.hasAbility("arbiters") || p2.hasAbility("arbiters")) && p1.getAc() > 0)
        {
            Button transact = Button.success(finChecker+"transact_ACs_"+p2.getFaction(), "Action Cards");
            stuffToTransButtons.add(transact);
        }
        if(p1.getPnCount() > 0)
        {
            Button transact = Button.success(finChecker+"transact_PNs_"+p2.getFaction(), "Promissory Notes");
            stuffToTransButtons.add(transact);
        }
        if(p1.getFragments().size() > 0)
        {
            Button transact = Button.success(finChecker+"transact_Frags_"+p2.getFaction(), "Fragments");
            stuffToTransButtons.add(transact);
        }
        return stuffToTransButtons;
    }


    public static void resolveSpecificTransButtons(Map activeMap, Player p1, String buttonID, ButtonInteractionEvent event) {
        String finChecker = "FFCC_"+p1.getFaction() + "_";
   
        List<Button> stuffToTransButtons = new ArrayList<>();
        buttonID = buttonID.replace("transact_", "");
        String thingToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        String factionToTrans = buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());
        Player p2 = Helper.getPlayerFromColorOrFaction(activeMap, factionToTrans);

        switch(thingToTrans)
        {
            case "TGs" -> {

                String message = "Click the amount of tgs you would like to send";
                for(int x = 1; x < p1.getTg()+1; x++)
                {
                    Button transact = Button.success(finChecker+"send_TGs_"+p2.getFaction() + "_"+x, ""+x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),message, stuffToTransButtons);
            }
            case "Comms" -> {
                String message = "Click the amount of commodities you would like to send";
                for(int x = 1; x < p1.getCommodities()+1; x++)
                {
                    Button transact = Button.success(finChecker+"send_Comms_"+p2.getFaction() + "_"+x, ""+x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),message, stuffToTransButtons);
            }
            case "ACs" -> {
                String message = Helper.getPlayerRepresentation(p1, activeMap, activeMap.getGuild(), true)+" Click the GREEN button that indicates the AC you would like to send";
                for(String acShortHand : p1.getActionCards().keySet())
                {
                    Button transact = Button.success(finChecker+"send_ACs_"+p2.getFaction() + "_"+p1.getActionCards().get(acShortHand), Mapper.getActionCardName(acShortHand));
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(p1.getCardsInfoThread(activeMap),message, stuffToTransButtons);
            }
            case "PNs" -> {
                PNInfo.sendPromissoryNoteInfo(activeMap, p1, false);
                String message =  Helper.getPlayerRepresentation(p1, activeMap, activeMap.getGuild(), true)+" Click the PN you would like to send";
               
                for(String pnShortHand : p1.getPromissoryNotes().keySet())
                {
                    if(p1.getPromissoryNotesInPlayArea().contains(pnShortHand)){
                        continue;
                    }
                    PromissoryNoteModel promissoryNote = Mapper.getPromissoryNoteByID(pnShortHand);
                    Player owner = activeMap.getPNOwner(pnShortHand);
                    Button transact;
                    if(activeMap.isFoWMode()){
                        transact = Button.success(finChecker+"send_PNs_" + p2.getFaction() + "_" + p1.getPromissoryNotes().get(pnShortHand), owner.getColor() +" "+ promissoryNote.getName());
                    }else{
                        transact = Button.success(finChecker+"send_PNs_" + p2.getFaction() + "_" + p1.getPromissoryNotes().get(pnShortHand), promissoryNote.getName()).withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord(owner.getFaction())));
                    }
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(p1.getCardsInfoThread(activeMap),message, stuffToTransButtons);
            }
            case "Frags" -> {
                String message = "Click the amount of fragments you would like to send";
                
                if(p1.getCrf() > 0)
                {
                    for(int x = 1; x < p1.getCrf()+1; x++)
                    {
                        Button transact = Button.primary(finChecker+"send_Frags_"+p2.getFaction() + "_CRF"+x, "Cultural Fragments ("+x+")");
                        stuffToTransButtons.add(transact);
                    }
                }
                if(p1.getIrf() > 0)
                {
                    for(int x = 1; x < p1.getIrf()+1; x++)
                    {
                        Button transact = Button.success(finChecker+"send_Frags_"+p2.getFaction() + "_IRF"+x, "Industrial Fragments ("+x+")");
                        stuffToTransButtons.add(transact);
                    }
                }
                if(p1.getHrf() > 0)
                {
                    for(int x = 1; x < p1.getHrf()+1; x++)
                    {
                        Button transact = Button.danger(finChecker+"send_Frags_"+p2.getFaction() + "_HRF"+x, "Hazardous Fragments ("+x+")");
                        stuffToTransButtons.add(transact);
                    }
                }
                
                if(p1.getVrf() > 0)
                {
                    for(int x = 1; x < p1.getVrf()+1; x++)
                    {
                        Button transact = Button.secondary(finChecker+"send_Frags_"+p2.getFaction() + "_URF"+x, "Frontier Fragments ("+x+")");
                        stuffToTransButtons.add(transact);
                    }
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),message, stuffToTransButtons);

            }
        }
        
    }

    public static void resolveSpecificTransButtonPress(Map activeMap, Player p1, String buttonID, ButtonInteractionEvent event) {
        String finChecker = "FFCC_"+p1.getFaction() + "_";
        buttonID = buttonID.replace("send_", "");
        List<Button> goAgainButtons = new ArrayList<>();

        String thingToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        buttonID = buttonID.replace(thingToTrans+"_", "");
        String factionToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        String amountToTrans = buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());
        Player p2 = Helper.getPlayerFromColorOrFaction(activeMap, factionToTrans);
        String message2 = "";
        String ident = Helper.getPlayerRepresentation(p1, activeMap, activeMap.getGuild(), false);
        String ident2 = Helper.getPlayerRepresentation(p2, activeMap, activeMap.getGuild(), false);
        switch(thingToTrans)
        {
            case "TGs" -> {

                int tgAmount = Integer.parseInt(amountToTrans);
                p1.setTg(p1.getTg()-tgAmount);
                p2.setTg(p2.getTg()+tgAmount);
                if(p2.getLeaderIDs().contains("hacancommander") && !p2.hasLeaderUnlocked("hacancommander")){
					ButtonHelper.commanderUnlockCheck(p2, activeMap, "hacan", event);
				}
                message2 = ident + " sent " + tgAmount+ " TGs to "+ident2;
            }
            case "Comms" -> {
                int tgAmount = Integer.parseInt(amountToTrans);
                p1.setCommodities(p1.getCommodities()-tgAmount);
                if(!p1.isPlayerMemberOfAlliance(p2)){
                    int targetTG = p2.getTg();
                    targetTG += tgAmount;
                    p2.setTg(targetTG);
                }else{
                    int targetTG = p2.getCommodities();
                    targetTG += tgAmount;
                    if(targetTG > p2.getCommoditiesTotal()){
                        targetTG = p2.getCommoditiesTotal();
                    }
                    p2.setCommodities(targetTG);
                }
                
                if(p2.getLeaderIDs().contains("hacancommander") && !p2.hasLeaderUnlocked("hacancommander")){
					ButtonHelper.commanderUnlockCheck(p2, activeMap, "hacan", event);
				}
                ButtonHelperFactionSpecific.pillageCheck(p1, activeMap);
                ButtonHelperFactionSpecific.pillageCheck(p2, activeMap);
                ButtonHelperFactionSpecific.resolveDarkPactCheck(activeMap, p1, p2, tgAmount, event);
                message2 = ident + " sent " + tgAmount+ " Commodities to "+ident2;
            }
            case "ACs" -> {
                
                message2 =ident + " sent AC #" + amountToTrans+ " to "+ident2;
               int acNum = Integer.parseInt(amountToTrans);
               String acID = null;
               if(!p1.getActionCards().values().contains((Integer) acNum)){
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that AC, no AC sent");
                    return;
               }
               for (java.util.Map.Entry<String, Integer> so : p1.getActionCards().entrySet()) {
                   if (so.getValue().equals(acNum)) {
                       acID = so.getKey();
                   }
               }
                p1.removeActionCard(acNum);
                p2.setActionCard(acID);
                ACInfo.sendActionCardInfo(activeMap, p2);
                ACInfo.sendActionCardInfo(activeMap, p1);
            }
            case "PNs" -> {
                String id = null;
		        int pnIndex;
                pnIndex = Integer.parseInt(amountToTrans);
                for (java.util.Map.Entry<String, Integer> so : p1.getPromissoryNotes().entrySet()) {
                    if (so.getValue().equals(pnIndex)) {
                        id = so.getKey();
                    }
                }
                p1.removePromissoryNote(id);
                p2.setPromissoryNote(id);
                boolean sendSftT = false;
                boolean sendAlliance = false;
                String promissoryNoteOwner = Mapper.getPromissoryNoteOwner(id);
                if ((id.endsWith("_sftt") || id.endsWith("_an")) && !promissoryNoteOwner.equals(p2.getFaction())
                        && !promissoryNoteOwner.equals(p2.getColor()) && !p2.isPlayerMemberOfAlliance(Helper.getPlayerFromColorOrFaction(activeMap,promissoryNoteOwner))) {
                    p2.setPromissoryNotesInPlayArea(id);
                    if (id.endsWith("_sftt")) {
                        sendSftT = true;
                    } else {
                        sendAlliance = true;
                    }
                }
                PNInfo.sendPromissoryNoteInfo(activeMap, p1, false);
                PNInfo.sendPromissoryNoteInfo(activeMap, p2, false);
                String text = sendSftT ? "**Support for the Throne** " : (sendAlliance ? "**Alliance** " : "");
                message2 = Helper.getPlayerRepresentation(p1, activeMap) + " sent " + Emojis.PN + text + "PN to " + ident2;   
            }
            case "Frags" -> {
               
                String fragType = amountToTrans.substring(0, 3);
                int fragNum = Integer.parseInt(amountToTrans.charAt(3)+"");
                String trait = 	switch (fragType){
                    case "CRF"-> "cultural"; 
                    case  "HRF"-> "hazardous";
                    case "IRF"->  "industrial" ;
                    case  "URF"->  "frontier" ;
                    default -> "";
                };
                new SendFragments().sendFrags(event, p1, p2, trait, fragNum, activeMap);
                message2 = "";
            }
        }
        Button button = Button.secondary(finChecker+"transactWith_"+p2.getColor(), "Send something else to player?");
        Button done = Button.secondary("finishTransaction_"+p2.getColor(), "Done With This Transaction");
                    
        goAgainButtons.add(button);
        goAgainButtons.add(done);
        if(activeMap.isFoWMode())
        {
            MessageHelper.sendMessageToChannel(p1.getPrivateChannel(), message2);
            MessageHelper.sendMessageToChannelWithButtons(p1.getPrivateChannel(),ident+" Use Buttons To Complete Transaction", goAgainButtons);
            MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), message2);
        }
        else
        {
            MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), message2);
            MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(),ident+" Use Buttons To Complete Transaction", goAgainButtons);
        }
        MapSaveLoadManager.saveMap(activeMap, event);

    
    }

    

    public static List<Button> getAllPossibleCompButtons(Map activeMap, Player p1, GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_"+p1.getFaction() + "_";
        String prefix = "componentActionRes_";
        List<Button> compButtons = new ArrayList<>();
        //techs
        for(String tech : p1.getTechs())
        {
            if(!p1.getExhaustedTechs().isEmpty() && p1.getExhaustedTechs().contains(tech)){
                continue;
            }
            TechnologyModel techRep = Mapper.getTechs().get(tech);
            String techName = techRep.getName(); 
            String techType = techRep.getType();
            String techEmoji = Helper.getEmojiFromDiscord(techType + "tech");
            String techText = techRep.getText();

            if(techText.contains("ACTION")) {
                Button tButton = Button.danger(finChecker+prefix+"tech_"+tech, "Exhaust "+techName).withEmoji(Emoji.fromFormatted(techEmoji));
                compButtons.add(tButton);
            }
        }
        //leaders
        for(Leader leader:p1.getLeaders())
        {
            if(!leader.isExhausted() && !leader.isLocked())
            {
                String leaderID = leader.getId();
                
                String leaderRep =  Mapper.getLeaderRepresentations().get(leaderID.toString());
                if(leaderRep == null){
                    continue;
                }
                //leaderID = 0:LeaderName ; 1:LeaderTitle ; 2:BacksideTitle/HeroAbility ; 3:AbilityWindow ; 4:AbilityText
                String[] leaderRepSplit = leaderRep.split(";");
                String leaderName = leaderRepSplit[0];
                String leaderAbilityWindow = leaderRepSplit[3];
                
                
                String factionEmoji =Helper.getFactionLeaderEmoji(leader);
                if(leaderAbilityWindow.equalsIgnoreCase("ACTION:") || leaderName.contains("Ssruu"))
                {
                    if(leaderName.contains("Ssruu")){
                        String led = "muaatagent";
                        if(!p1.hasLeader(led) && p1.hasLeader(led, activeMap)){
                            Button lButton = Button.secondary(finChecker+prefix+"leader_"+led, "Use "+leaderName+ " as Muaat agent").withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(lButton);
                        }
                        led = "naaluagent";
                        if(!p1.hasLeader(led) && p1.hasLeader(led, activeMap)){
                            Button lButton = Button.secondary(finChecker+prefix+"leader_"+led, "Use "+leaderName+ " as Naalu agent").withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(lButton);
                        }
                        led = "arborecagent";
                        if(!p1.hasLeader(led) && p1.hasLeader(led, activeMap)){
                            Button lButton = Button.secondary(finChecker+prefix+"leader_"+led, "Use "+leaderName+ " as Arborec agent").withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(lButton);
                        }
                        led = "xxchaagent";
                        if(!p1.hasLeader(led) && p1.hasLeader(led, activeMap)){
                            Button lButton = Button.secondary(finChecker+prefix+"leader_"+led, "Use "+leaderName+ " as Xxcha agent").withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(lButton);
                        }
                        led = "yssarilagent";
                        Button lButton = Button.secondary(finChecker+prefix+"leader_"+led, "Use "+leaderName+ " as Unimplemented Component Agent").withEmoji(Emoji.fromFormatted(factionEmoji));
                        compButtons.add(lButton);
                        
                    }else{
                        Button lButton = Button.secondary(finChecker+prefix+"leader_"+leaderID, "Use "+leaderName).withEmoji(Emoji.fromFormatted(factionEmoji));
                        compButtons.add(lButton);
                    }
                    
                }else if(leaderID.equalsIgnoreCase("mahactcommander") && p1.getTacticalCC() > 0 && ButtonHelper.getTilesWithYourCC(p1,activeMap,event).size()>0){
                     Button lButton = Button.secondary(finChecker+"mahactCommander", "Use "+leaderName).withEmoji(Emoji.fromFormatted(factionEmoji));
                    compButtons.add(lButton);
                }
            }
        }
        //Relics
        boolean dontEnigTwice = true;
        for(String relic : p1.getRelics())
        {
            
            String relicText = Mapper.getRelic(relic);
            String[] relicData =  null;
            if(relicText != null)
            {
                relicData = relicText.split(";");
            }
            if(relicData != null && relicData[0].contains("<"))
            {
                relicData[0] =relicData[0].substring(0, relicData[0].indexOf("<"));
            }
            
            if(relic.equalsIgnoreCase(Constants.ENIGMATIC_DEVICE) || (relicData != null && relicData[1].contains("Action:")))
            {
                Button rButton = null;
                if(relic.equalsIgnoreCase(Constants.ENIGMATIC_DEVICE))
                {
                    if(!dontEnigTwice){
                       continue;
                    }
                    rButton = Button.danger(finChecker+prefix+"relic_"+relic, "Purge Enigmatic Device");
                    dontEnigTwice = false;
                } else {
                    if(relic.equalsIgnoreCase("titanprototype") ||relic.equalsIgnoreCase("absol_jr") )
                    {
                        if(!p1.getExhaustedRelics().contains(relic)){
                            rButton = Button.primary(finChecker+prefix+"relic_"+relic, "Exhaust " + relicData[0]);
                        }else{
                            continue;
                        }
                        
                    }else {
                        rButton = Button.danger(finChecker+prefix+"relic_"+relic, "Purge " + relicData[0]);
                    }
                        
                }
                compButtons.add(rButton);
            }
        }
        //PNs
        for(String pn : p1.getPromissoryNotes().keySet()){
            if(Mapper.getPromissoryNoteOwner(pn) != null && !Mapper.getPromissoryNoteOwner(pn).equalsIgnoreCase(p1.getFaction()) && !p1.getPromissoryNotesInPlayArea().contains(pn))
            {
                String pnText = Mapper.getPromissoryNote(pn, true);
                if(pnText.contains("Action:") && !pn.equalsIgnoreCase("bmf"))
                {
                    PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(pn);
                    String pnName = pnModel.getName();
                    Button pnButton = Button.danger(finChecker+prefix+"pn_"+pn, "Use "+pnName);
                    compButtons.add(pnButton);
                }
            }
        }
        //Abilities
        if(p1.hasAbility("star_forge") && p1.getStrategicCC() > 0 && ButtonHelper.getTilesOfPlayersSpecificUnit(activeMap, p1, "warsun").size() > 0)
        {
            Button abilityButton = Button.success(finChecker+prefix+"ability_starForge", "Starforge");
            compButtons.add(abilityButton);
        }
        if(p1.hasAbility("orbital_drop") && p1.getStrategicCC() > 0)
        {
            Button abilityButton = Button.success(finChecker+prefix+"ability_orbitalDrop", "Orbital Drop");
            compButtons.add(abilityButton);
        }
        if(p1.hasAbility("stall_tactics") && p1.getActionCards().size() > 0)
        {
            Button abilityButton = Button.success(finChecker+prefix+"ability_stallTactics", "Stall Tactics");
            compButtons.add(abilityButton);
        }
        if(p1.hasAbility("fabrication") && p1.getFragments().size() > 0)
        {
            Button abilityButton = Button.success(finChecker+prefix+"ability_fabrication", "Purge 1 Frag for a CC");
            compButtons.add(abilityButton);
        }
        if(p1.getUnitsOwned().contains("muaat_flagship") &&  p1.getStrategicCC() > 0 && ButtonHelper.getTilesOfPlayersSpecificUnit(activeMap, p1, "flagship").size() > 0)
        {
            Button abilityButton = Button.success(finChecker+prefix+"ability_muaatFS", "Spend a Strat CC for a Cruiser with your FS");
            compButtons.add(abilityButton);
        }
        //Get Relic
        if(p1.enoughFragsForRelic())
        {
            Button getRelicButton = Button.success(finChecker+prefix+"getRelic_", "Get Relic");
            compButtons.add(getRelicButton);
        }
       //ACs
        Button acButton = Button.secondary(finChecker+prefix+"actionCards_", "Play \"ACTION:\" AC");
        compButtons.add(acButton);
        //Generic
        Button genButton = Button.secondary(finChecker+prefix+"generic_", "Generic Component Action");
        compButtons.add(genButton);

        return compButtons;
    }

    public static String mechOrInfCheck(String planetName, Map activeMap, Player player) {
        String message = "";
        Tile tile = activeMap.getTile(AliasHandler.resolveTile(planetName));
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        int numMechs = 0;
        int numInf = 0;
        String colorID = Mapper.getColorID(player.getColor());
        String mechKey = colorID + "_mf.png";
        String infKey = colorID + "_gf.png";
        if (unitHolder.getUnits() != null) {

            if (unitHolder.getUnits().get(mechKey) != null) {
                numMechs = unitHolder.getUnits().get(mechKey);
            }
            if (unitHolder.getUnits().get(infKey) != null) {
                numInf = unitHolder.getUnits().get(infKey);
            }
        }
        if (numMechs > 0 || numInf > 0) {
            if (numMechs > 0) {
                message = "Planet had a mech. ";
            } else {
                message = "Planet did not have a mech. Removed 1 infantry (" + numInf + "->" + (numInf - 1) + "). ";
                tile.removeUnit(planetName, infKey, 1);
            }
        } else {
            message = "Planet did not have a mech or infantry. Please try again.";
        }
        return message;
    }

    public static void addReaction(@NotNull ButtonInteractionEvent event, boolean skipReaction, boolean sendPublic,
            String message, String additionalMessage) {
        String userID = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
        Player player = Helper.getGamePlayer(activeMap, null, event.getMember(), userID);
        if (player == null || !player.isRealPlayer()) {
            event.getChannel().sendMessage("You're not an active player of the game").queue();
            return;
        }
        String playerFaction = player.getFaction();
        Guild guild = event.getGuild();
        if (guild == null) {
            event.getChannel().sendMessage("Could not find server Emojis").queue();
            return;
        }
        HashMap<String, Emoji> emojiMap = new ButtonListener().emoteMap.get(guild);
        List<RichCustomEmoji> emojis = guild.getEmojis();
        if (emojiMap != null && emojiMap.size() != emojis.size()) {
            emojiMap.clear();
        }
        if (emojiMap == null || emojiMap.isEmpty()) {
            emojiMap = new HashMap<>();
            for (Emoji emoji : emojis) {
                emojiMap.put(emoji.getName().toLowerCase(), emoji);
            }
        }

        Message mainMessage = event.getInteraction().getMessage();
        Emoji emojiToUse = Helper.getPlayerEmoji(activeMap, player, mainMessage);
        String messageId = mainMessage.getId();

        if (!skipReaction) {
            if (event.getMessageChannel() instanceof ThreadChannel) {
                
                activeMap.getActionsChannel().addReactionById(event.getChannel().getId(), emojiToUse).queue();
            }

            event.getChannel().addReactionById(messageId, emojiToUse).queue();
            new ButtonListener().checkForAllReactions(event, activeMap);
            if (message == null || message.isEmpty())
                return;
        }

        String text = Helper.getPlayerRepresentation(player, activeMap) + " " + message;
        if (activeMap.isFoWMode() && sendPublic) {
            text = message;
        } else if (activeMap.isFoWMode() && !sendPublic) {
            text = "(You) " + emojiToUse.getFormatted() + " " + message;
        }

        if (!additionalMessage.isEmpty()) {
            text += Helper.getGamePing(event.getGuild(), activeMap) + " " + additionalMessage;
        }

        if (activeMap.isFoWMode() && !sendPublic) {
            MessageHelper.sendPrivateMessageToPlayer(player, activeMap, text);
            return;
        }

        MessageHelper.sendMessageToChannel(Helper.getThreadChannelIfExists(event), text);
    }

    public static Tile getTileOfPlanetWithNoTrait(Player player, Map activeMap){
        
        for(String planet : player.getPlanets()){
            UnitHolder unitHolder = activeMap.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = false;
            if (planetReal != null && planetReal.getOriginalPlanetType() != null && (planetReal.getOriginalPlanetType().equalsIgnoreCase("industrial") || planetReal.getOriginalPlanetType().equalsIgnoreCase("cultural") || planetReal.getOriginalPlanetType().equalsIgnoreCase("hazardous"))) {
                oneOfThree = true;
            }
            if(!planet.equalsIgnoreCase("mr") && !planet.equalsIgnoreCase("custodiavigilia") && !oneOfThree){
                return Helper.getTileFromPlanet(planet, activeMap);
            }
        }

        return null;

    }
    public static String getListOfStuffAvailableToSpend(Player player, Map activeMap){
        String youCanSpend = "You have available to you to spend: ";
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        for (String planet : planets) {
            youCanSpend = youCanSpend + Helper.getPlanetRepresentation(planet, activeMap) +", ";
        }
        if(planets.isEmpty()){
            youCanSpend = "You have available to you 0 unexhausted planets ";
        }
        youCanSpend = youCanSpend +"and "+ player.getTg() + " tgs";

        return youCanSpend;
    }
    public static List<Tile> getTilesOfPlayersSpecificUnit(Map activeMap, Player p1, String unit)
    {
        List<Tile> tiles = new ArrayList<Tile>();
        for(Tile tile : activeMap.getTileMap().values())
        {
            boolean tileHasIt = false;
            String unitKey = Mapper.getUnitID(AliasHandler.resolveUnit(unit), p1.getColor());
            for(UnitHolder unitH : tile.getUnitHolders().values())
            {
                if(unitH.getUnits().containsKey(unitKey))
                {
                    tileHasIt = true;
                }
            }
            if(tileHasIt && !tiles.contains(tile))
            {
                tiles.add(tile);
            }
        }
        return tiles;  
    }
    

    public static int getNumberOfUnitsOnTheBoard(Map activeMap, Player p1, String unit)
    {
        int count = 0;
        for(Tile tile : activeMap.getTileMap().values())
        {
            String unitKey = Mapper.getUnitID(AliasHandler.resolveUnit(unit), p1.getColor());
            for(UnitHolder unitH : tile.getUnitHolders().values())
            {
                if(unitH.getUnits().containsKey(unitKey))
                {
                    count = count + unitH.getUnits().get(unitKey);
                }
            }
            for (Player player_ : activeMap.getPlayers().values()) {
                UnitHolder unitH = player_.getNomboxTile().getUnitHolders().get(Constants.SPACE);
                if (unitH == null){
                    continue;
                }else{
                    if(unitH.getUnits().containsKey(unitKey))
                    {
                        count = count + unitH.getUnits().get(unitKey);
                    }
                }
            }
        }
        return count;  
    }
    

    public static void resolvePressedCompButton(Map activeMap, Player p1, ButtonInteractionEvent event, String buttonID) {
        String prefix = "componentActionRes_";
        String finChecker = "FFCC_"+p1.getFaction() + "_";
        buttonID = buttonID.replace(prefix, "");
        
        String firstPart = buttonID.substring(0, buttonID.indexOf("_"));
        buttonID = buttonID.replace(firstPart+"_", "");
        
        switch(firstPart) {
            case "tech" -> {
                p1.exhaustTech(buttonID);
                
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), (Helper.getPlayerRepresentation(p1, activeMap) + " exhausted tech: " + Helper.getTechRepresentation(buttonID)));
                if(buttonID.equalsIgnoreCase("mi")){
                    List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(activeMap, null, "getACFrom",null);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select who you would like to mageon.", buttons);
                }
                if(buttonID.equalsIgnoreCase("sr")){
                    List<Button> buttons = new ArrayList<Button>();
                    List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeMap, p1, "spacedock");
                    if(tiles.isEmpty()){
                        tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeMap, p1, "cabalspacedock");
                    }
                    for(Tile tile : tiles){
                        Button tileButton = Button.success("produceOneUnitInTile_"+tile.getPosition()+"_sling", tile.getRepresentationForButtons(activeMap,p1));
                        buttons.add(tileButton);
                    }
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select which tile you would like to sling in.", buttons);
                }
            }
            case "leader" -> {
                Leader playerLeader = p1.getLeader(buttonID);
		
                if(buttonID.contains("agent")){
                    if(!buttonID.equalsIgnoreCase("naaluagent") && !buttonID.equalsIgnoreCase("muaatagent") && !buttonID.equalsIgnoreCase("arborecagent") && !buttonID.equalsIgnoreCase("xxchaagent")){
                        playerLeader.setExhausted(true);
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),Helper.getFactionLeaderEmoji(playerLeader));
                        StringBuilder messageText = new StringBuilder(Helper.getPlayerRepresentation(p1, activeMap))
                                .append(" exhausted ").append(Helper.getLeaderFullRepresentation(playerLeader));
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),messageText.toString());
                    }else{
                        List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(activeMap, buttonID);
                        String message = Helper.getPlayerRepresentation(p1, activeMap, activeMap.getGuild(), true)+" Use buttons to select the user of the agent";
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    }
                    
                }
                else {
                    StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(p1, activeMap)).append(" played ").append(Helper.getLeaderFullRepresentation(playerLeader));
                    if ("letnevhero".equals(playerLeader.getId()) || "nomadhero".equals(playerLeader.getId())) {
                        playerLeader.setLocked(false);
                        playerLeader.setActive(true);
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),message.toString() + " - Leader will be PURGED after status cleanup");
                    } else {
                        boolean purged = p1.removeLeader(playerLeader);
                        if (purged) {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(),message.toString() + " - Leader " + buttonID + " has been purged");
                        } else {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(),"Leader was not purged - something went wrong");
                        }
                        if ("titanshero".equals(playerLeader.getId())) {
                            String titanshero = Mapper.getTokenID("titanshero");
                            System.out.println(titanshero);
                            Tile t= activeMap.getTile(AliasHandler.resolveTile(p1.getFaction()));
                            if(Helper.getTileFromPlanet("elysium", activeMap) != null && Helper.getTileFromPlanet("elysium", activeMap).getPosition().equalsIgnoreCase(t.getPosition())){
                                t.addToken("attachment_titanshero.png", "elysium");
                                MessageHelper.sendMessageToChannel(event.getMessageChannel(),"Attachment added to Elysium and it has been readied");
                                new PlanetRefresh().doAction(p1, "elysium", activeMap);
                            }
                            else{
                                MessageHelper.sendMessageToChannel(event.getMessageChannel(),"`Use the following command to add the attachment: /add_token token:titanshero`");
                            }
                        }
                        if ("solhero".equals(playerLeader.getId())) {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), Helper.getPlayerRepresentation(p1, activeMap, activeMap.getGuild(), true)+" removed all of your ccs from the board");
                            for(Tile t : activeMap.getTileMap().values()){
                                if (AddCC.hasCC(event, p1.getColor(), t)) {
                                    RemoveCC.removeCC(event, p1.getColor(), t, activeMap);
                                }
                            }   
                        }
                        if ("yinhero".equals(playerLeader.getId())) {
                            List<Button> buttons = new ArrayList<Button>();
                            buttons.add(Button.primary(finChecker+"yinHeroStart", "Invade a planet with Yin Hero"));
                            buttons.add(Button.danger("deleteButtons", "Delete Buttons"));
                            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), Helper.getPlayerRepresentation(p1, activeMap, activeMap.getGuild(), true)+" use the button to do individual invasions, then delete the buttons when you have placed 3 total infantry.", buttons);  
                        }
                        if ("empyreanhero".equals(playerLeader.getId())) {
                             new AddFrontierTokens().parsingForTile(event,activeMap);
                             MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Added frontier tokens");  
                            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use Buttons to explore empties", ButtonHelperFactionSpecific.getEmpyHeroButtons(p1, activeMap));  
                        }
                        if ("cabalhero".equals(playerLeader.getId())) {
                            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use Buttons to capture people", ButtonHelperFactionSpecific.getCabalHeroButtons(p1, activeMap));  
                        }
                        if ("yssarilhero".equals(playerLeader.getId())) {
                            for(Player p2 : activeMap.getRealPlayers()){
                                if(p2 == p1 || p2.getAc() == 0){
                                    continue;
                                }
                                List<Button> buttons = new ArrayList<Button>();
                                buttons.addAll(ACInfo.getYssarilHeroActionCardButtons(activeMap, p1, p2));
                                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(activeMap), Helper.getPlayerRepresentation(p2, activeMap, activeMap.getGuild(), true)+ " Yssaril hero played.  Use buttons to select which AC you will offer to them.", buttons);
                            }
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), Helper.getPlayerRepresentation(p1, activeMap, activeMap.getGuild(), true)+" sent everyone a ping in their private threads with buttons to send you an AC");
                        }
                        if ("keleresheroharka".equals(playerLeader.getId())) {
                            new KeleresHeroMentak().secondHalf(activeMap, p1, event);  
                        }
                    }

                }
            }
            case "relic" -> {
                String relicId = buttonID;
                Player player = p1;
                String purgeOrExhaust = "Purged ";
                
                if (player.hasRelic(relicId)) {
                    if(relicId.equalsIgnoreCase("titanprototype") ||relicId.equalsIgnoreCase("absol_jr") )
                    {
                        List<Button> buttons2 = AgendaHelper.getPlayerOutcomeButtons(activeMap, null, "jrResolution", null);
                        player.addExhaustedRelic(relicId);
                        purgeOrExhaust = "Exhausted ";
                        Button sdButton = Button.success("jrStructure_sd", "Place A SD");
                        sdButton = sdButton.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("spacedock")));
                        Button pdsButton = Button.success("jrStructure_pds", "Place a PDS");
                        pdsButton = pdsButton.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("pds")));
                        List<Button> buttons = new ArrayList();
                        buttons.add(sdButton);
                        buttons.add(pdsButton);
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to decide who to use JR on", buttons2);
                    }
                    else{
                        player.removeRelic(relicId);
                        player.removeExhaustedRelic(relicId);
                    }
                   
                    String relicName = Mapper.getRelic(relicId).split(";")[0];
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),purgeOrExhaust + Emojis.Relic + " relic: " + relicName);
                    if(relicName.contains("Enigmatic")){
                        activeMap.setComponentAction(true);
                        Button getTech = Button.success("acquireATech", "Get a tech");
                        List<Button> buttons = new ArrayList();
                        buttons.add(getTech);
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use Button to get a tech", buttons);
                    }
                    if(relicName.contains("Nanoforge")){      
                        ButtonHelper.offerNanoforgeButtons(player, activeMap, event);
                    }
                    if (relicId.equals("dynamiscore") || relicId.equals("absol_dynamiscore")){
                        int oldTg = player.getTg();
                        player.setTg(oldTg+player.getCommoditiesTotal()+2);
                        if(relicId.equals("absol_dynamiscore")){
                             player.setTg(player.getTg()+2);
                        }
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Your tgs increased from "+oldTg+" -> "+player.getTg());
                        ButtonHelperFactionSpecific.pillageCheck(player, activeMap);
                    }
                    
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),"Invalid relic or player does not have specified relic");
                }
                
            }
            case "pn" -> {
                ButtonHelper.resolvePNPlay(buttonID, p1, activeMap, event);
            }
            case "ability" -> {
                if(buttonID.equalsIgnoreCase("starForge")){

                    List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeMap, p1, "warsun");
                    List<Button> buttons = new ArrayList<Button>();
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Chose to use the starforge ability");
                    String message = "Select the tile you would like to starforge in";
                    for(Tile tile : tiles)
                    {
                        Button starTile = Button.success("starforgeTile_"+tile.getPosition(), tile.getRepresentationForButtons(activeMap, p1));
                        buttons.add(starTile);
                    }
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                } else if(buttonID.equalsIgnoreCase("orbitalDrop")){
                    String successMessage = "Reduced strategy pool CCs by 1 ("+(p1.getStrategicCC())+"->"+(p1.getStrategicCC()-1)  +")";
                    p1.setStrategicCC(p1.getStrategicCC()-1);
                    ButtonHelperFactionSpecific.resolveMuaatCommanderCheck(p1, activeMap, event);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                    String message = "Select the planet you would like to place 2 infantry on.";
                    List<Button> buttons = Helper.getPlanetPlaceUnitButtons(p1, activeMap, "2gf", "place");
                    buttons.add(Button.danger("orbitolDropFollowUp", "Done Dropping Infantry"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                 } else if(buttonID.equalsIgnoreCase("muaatFS")){
                    String successMessage = "Used Muaat FS ability. Reduced strategy pool CCs by 1 ("+(p1.getStrategicCC())+"->"+(p1.getStrategicCC()-1)  +") \n";
                    p1.setStrategicCC(p1.getStrategicCC()-1);
                    ButtonHelperFactionSpecific.resolveMuaatCommanderCheck(p1, activeMap, event);
                    List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeMap, p1, "flagship");
                    Tile tile = tiles.get(0);
                    List<Button> buttons = ButtonHelper.getStartOfTurnButtons(p1, activeMap, true, event);
                    new AddUnits().unitParsing(event, p1.getColor(), tile, "1 cruiser", activeMap);
                    successMessage = successMessage+"Produced 1 " + Helper.getEmojiFromDiscord("cruiser") + " in tile "
                            + tile.getRepresentationForButtons(activeMap, p1) + ".";
                    MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
                    String message = "Use buttons to end turn or do another action";
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
                    event.getMessage().delete().queue();
                    
                }else if(buttonID.equalsIgnoreCase("fabrication")){
                    String message = "Click the fragment you'd like to purge. ";
                    List<Button> purgeFragButtons = new ArrayList<>();
                    if(p1.getCrf() > 0){
                            Button transact = Button.primary(finChecker+"purge_Frags_CRF_1", "Purge 1 Cultural Fragment");
                            purgeFragButtons.add(transact);
                    }
                    if(p1.getIrf()> 0){  
                        Button transact = Button.success(finChecker+"purge_Frags_IRF_1", "Purge 1 Industrial Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if(p1.getHrf() > 0){
                            Button transact = Button.danger(finChecker+"purge_Frags_HRF_1", "Purge 1 Hazardous Fragment");
                            purgeFragButtons.add(transact);
                    }       
                    if(p1.getVrf() > 0){
                            Button transact = Button.secondary(finChecker+"purge_Frags_URF_1", "Purge 1 Frontier Fragment");
                            purgeFragButtons.add(transact);
                    }
                    Button transact2 = Button.success(finChecker+"gain_CC", "Gain CC");
                    purgeFragButtons.add(transact2);
                    Button transact3 = Button.danger(finChecker+"finishComponentAction", "Done Resolving Fabrication");
                    purgeFragButtons.add(transact3);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),message, purgeFragButtons);

                }else if(buttonID.equalsIgnoreCase("stallTactics")){
                    String secretScoreMsg = "_ _\n"+ Helper.getPlayerRepresentation(p1, activeMap, activeMap.getGuild(), true)+" Click a button below to discard an Action Card";
                    List<Button> acButtons = ACInfo.getDiscardActionCardButtons(activeMap, p1, true);
                    if (acButtons != null && !acButtons.isEmpty()) {
                        List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(secretScoreMsg, acButtons);
                        ThreadChannel cardsInfoThreadChannel = p1.getCardsInfoThread(activeMap);
                        for (MessageCreateData message : messageList) {
                            cardsInfoThreadChannel.sendMessage(message).queue();
                        }
                    }
                }
            }
            case "getRelic" -> {
                String message = "Click the fragments you'd like to purge. ";
                List<Button> purgeFragButtons = new ArrayList<>();
                int numToBeat = 2 - p1.getVrf();
                if((p1.hasAbility("fabrication") || p1.getPromissoryNotes().containsKey("bmf")))
                {
                    numToBeat = numToBeat -1;
                    if(p1.getPromissoryNotes().containsKey("bmf") && !p1.hasAbility("fabrication"))
                    {
                        Button transact = Button.primary(finChecker+"resolvePNPlay_bmf", "Play BMF");
                        purgeFragButtons.add(transact);
                    }
                    
                }
                if(p1.getCrf() > numToBeat)
                {
                    for(int x = numToBeat+1; (x < p1.getCrf()+1 && x < 4); x++)
                    {
                        Button transact = Button.primary(finChecker+"purge_Frags_CRF_"+x, "Cultural Fragments ("+x+")");
                        purgeFragButtons.add(transact);
                    }
                }
                if(p1.getIrf()> numToBeat)
                {
                    for(int x = numToBeat+1; (x < p1.getIrf()+1&& x < 4); x++)
                    {
                        Button transact = Button.success(finChecker+"purge_Frags_IRF_"+x, "Industrial Fragments ("+x+")");
                        purgeFragButtons.add(transact);
                    }
                }
                if(p1.getHrf() > numToBeat)
                {
                    for(int x = numToBeat+1; (x < p1.getHrf()+1&& x < 4); x++)
                    {
                        Button transact = Button.danger(finChecker+"purge_Frags_HRF_"+x, "Hazardous Fragments ("+x+")");
                        purgeFragButtons.add(transact);
                    }
                }
                
                if(p1.getVrf() > 0)
                {
                    for(int x = 1; x < p1.getVrf()+1; x++)
                    {
                        Button transact = Button.secondary(finChecker+"purge_Frags_URF_"+x, "Frontier Fragments ("+x+")");
                        purgeFragButtons.add(transact);
                    }
                }
                Button transact2 = Button.danger(finChecker+"drawRelicFromFrag", "Finish Purging and Draw Relic");
                purgeFragButtons.add(transact2);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),message, purgeFragButtons);
            }
            case "generic" -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),"Doing unspecified component action. You could ping Fin to add this. ");
            }
            case "actionCards" -> {
                String secretScoreMsg = "_ _\nClick a button below to play an Action Card";
                List<Button> acButtons = ACInfo.getActionPlayActionCardButtons(activeMap, p1);
                if (acButtons != null && !acButtons.isEmpty()) {
                    List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(secretScoreMsg, acButtons);
                    ThreadChannel cardsInfoThreadChannel = p1.getCardsInfoThread(activeMap);
                    for (MessageCreateData message : messageList) {
                        cardsInfoThreadChannel.sendMessage(message).queue();
                    }
                }

            }
        }

        if(!firstPart.contains("ability") && !firstPart.contains("getRelic"))
        {
            String message = "Use buttons to end turn or do another action.";
            List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(p1, activeMap, true, event);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        }
        File file = GenerateMap.getInstance().saveImage(activeMap, DisplayType.all, event);
    }

     public static void offerNanoforgeButtons(Player player, Map activeMap, GenericInteractionCreateEvent event) {
        List<Button> buttons = new ArrayList<Button>();
        for(String planet : player.getPlanets()){
            UnitHolder unitHolder = activeMap.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = false;
            if (planetReal != null && planetReal.getOriginalPlanetType() != null && (planetReal.getOriginalPlanetType().equalsIgnoreCase("industrial") || planetReal.getOriginalPlanetType().equalsIgnoreCase("cultural") || planetReal.getOriginalPlanetType().equalsIgnoreCase("hazardous"))) {
                oneOfThree = true;
            }
            if (oneOfThree && !planetReal.isHasAbility()) {
                buttons.add(Button.success("nanoforgePlanet_"+planet, Helper.getPlanetRepresentation(planet, activeMap)));
            }
        }
        String message = "Use buttons to select which planet to nanoforge";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }

    public static void resolvePNPlay(String id, Player player, Map activeMap, GenericInteractionCreateEvent event) {
        boolean longPNDisplay = false;
        PromissoryNoteModel pn = Mapper.getPromissoryNoteByID(id);
        String pnName = pn.getName();
        String pnOwner = Mapper.getPromissoryNoteOwner(id);
        Player owner = activeMap.getPNOwner(id);
        if (pn.getPlayArea() && !player.isPlayerMemberOfAlliance(owner)) {
            player.setPromissoryNotesInPlayArea(id);
        } else {
            player.removePromissoryNote(id);
            owner.setPromissoryNote(id);
            PNInfo.sendPromissoryNoteInfo(activeMap, owner, false);
            PNInfo.sendPromissoryNoteInfo(activeMap, player, false);
        }
        String emojiToUse = activeMap.isFoWMode() ? "" : Helper.getFactionIconFromDiscord(pnOwner);
        StringBuilder sb = new StringBuilder(Helper.getPlayerRepresentation(player, activeMap) + " played promissory note: "+pnName+"\n");
        sb.append(emojiToUse + Emojis.PN);
        String pnText = "";

        //Handle AbsolMode Political Secret
        if (activeMap.isAbsolMode() && id.endsWith("_ps")) {
            pnText = "Political Secret" + Emojis.Absol + ":  *When you cast votes:* You may exhaust up to 3 of the {colour} player's planets and cast additional votes equal to the combined influence value of the exhausted planets. Then return this card to the {colour} player.";
        } else {
            pnText = Mapper.getPromissoryNote(id, longPNDisplay);
        }
        sb.append(pnText).append("\n");
        
        
        //TERRAFORM TIP
        if (id.equalsIgnoreCase("terraform")) {
            ButtonHelperFactionSpecific.offerTerraformButtons(player, activeMap, event);
        }
        if (id.equalsIgnoreCase("iff")) {
            List<Button> buttons = new ArrayList<Button>();
            buttons.addAll(ButtonHelperFactionSpecific.getCreusIFFTypeOptions(activeMap, player));
            String message = ButtonHelper.getTrueIdentity(player, activeMap)+" select type of wormhole you wish to drop";
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeMap), message, buttons);
        }
        if (id.equalsIgnoreCase("ms")) {
            List<Button> buttons = new ArrayList<Button>();
            buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, activeMap, "2gf", "placeOneNDone_skipbuild"));
            if(owner.getStrategicCC() > 0){
                owner.setStrategicCC(owner.getStrategicCC()-1);
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(owner, activeMap), ButtonHelper.getTrueIdentity(owner, activeMap) + " lost a command counter from strategy pool due to a Military Support play");
            }
            String message = ButtonHelper.getTrueIdentity(player, activeMap)+" Use buttons to drop 2 infantry on a planet";
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeMap), message, buttons);
        }
       
       

        //Fog of war ping
        if (activeMap.isFoWMode()) {
            // Add extra message for visibility
            FoWHelper.pingAllPlayersWithFullStats(activeMap, event, player, sb.toString());
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel( player, activeMap),sb.toString());
         if (id.equalsIgnoreCase("fires")) {
            player.addTech("ws");
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true ) + " acquired Warsun tech");
            owner.setFleetCC(owner.getFleetCC()-1);
            String reducedMsg = Helper.getPlayerRepresentation(owner, activeMap, activeMap.getGuild(), true ) + " reduced your fleet cc by 1";
            if(activeMap.isFoWMode()){
                MessageHelper.sendMessageToChannel(owner.getPrivateChannel(), reducedMsg);
            }else{
                MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), reducedMsg);
            }
        }
        if (id.endsWith("_ta")) {
            int comms = owner.getCommodities();
            owner.setCommodities(0);
            String reducedMsg = Helper.getPlayerRepresentation(owner, activeMap, activeMap.getGuild(), true ) + " your TA was played.";
            String reducedMsg2 = Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true ) + " you gained tgs equal to the number of comms the player had (your tgs went from "+player.getTg()+"tgs to -> "+(player.getTg()+comms)+"tgs). Please follow up with the player if this number seems off";
            player.setTg(player.getTg()+comms);
            ButtonHelperFactionSpecific.resolveDarkPactCheck(activeMap, owner, player, owner.getCommoditiesTotal(), event);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(owner, activeMap), reducedMsg);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap), reducedMsg2);
        }
        PNInfo.sendPromissoryNoteInfo(activeMap, player, false);
        PNInfo.sendPromissoryNoteInfo(activeMap, owner, false);
    }

}