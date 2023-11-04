package ti4.helpers;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.explore.ExploreAndDiscard;
import ti4.commands.tokens.RemoveCC;
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.TechnologyModel;

public class ButtonHelperActionCards {


    public static void resolveCounterStroke(Game activeGame, Player player, ButtonInteractionEvent event){
        RemoveCC.removeCC(event, player.getColor(), activeGame.getTileByPosition(activeGame.getActiveSystem()), activeGame);
        String message = ButtonHelper.getIdent(player) + " removed their CC from tile "+activeGame.getActiveSystem()+ " using counterstroke";
        Button getTactic = Button.success("increase_tactic_cc", "Gain 1 Tactic CC");
        Button getFleet = Button.success("increase_fleet_cc", "Gain 1 Fleet CC");
        Button getStrat = Button.success("increase_strategy_cc", "Gain 1 Strategy CC");
        Button DoneGainingCC = Button.danger("deleteButtons", "Done Gaining CCs");
        List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
        String message2 = player.getRepresentation() + "! Your current CCs are " + player.getCCRepresentation() + ". Use buttons to gain a CC";
        MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message2, buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveSummit(Game activeGame, Player player, ButtonInteractionEvent event){
        Button getTactic = Button.success("increase_tactic_cc", "Gain 1 Tactic CC");
        Button getFleet = Button.success("increase_fleet_cc", "Gain 1 Fleet CC");
        Button getStrat = Button.success("increase_strategy_cc", "Gain 1 Strategy CC");
        Button DoneGainingCC = Button.danger("deleteButtons", "Done Gaining CCs");
        List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
        String message2 = player.getRepresentation() + "! Your current CCs are " + player.getCCRepresentation() + ". Use buttons to gain a CC";
        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message2, buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveWarEffort(Game activeGame, Player player, ButtonInteractionEvent event){
        List<Button> buttons = new ArrayList<>();
        buttons.addAll(Helper.getTileWithShipsPlaceUnitButtons(player, activeGame, "cruiser", "placeOneNDone_skipbuild"));
        String message = "Use buttons to put 1 cruiser with your ships";
        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveHarnessEnergy(Game activeGame, Player player, ButtonInteractionEvent event){
        String message = ButtonHelper.getIdent(player)+ " Replenished Commodities (" + player.getCommodities() + "->" + player.getCommoditiesTotal()
        + ").";
        player.setCommodities(player.getCommoditiesTotal());
        MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
        ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
        ButtonHelperAgents.cabalAgentInitiation(activeGame, player);
        if (player.hasAbility("military_industrial_complex") && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                ButtonHelper.getTrueIdentity(player, activeGame) + " you have the opportunity to buy axis orders", ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
        }
        event.getMessage().delete().queue();
    }

     public static void resolveRally(Game activeGame, Player player, ButtonInteractionEvent event){
        RemoveCC.removeCC(event, player.getColor(), activeGame.getTileByPosition(activeGame.getActiveSystem()), activeGame);
        String message = ButtonHelper.getIdent(player) + " gained 2 fleet CC ("+player.getFleetCC()+ "->"+(player.getFleetCC()+2)+") using rally";
        player.setFleetCC(player.getFleetCC()+2);
        MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
        event.getMessage().delete().queue();
    }

    public static List<Button> getArcExpButtons(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        List<String> types = ButtonHelper.getTypesOfPlanetPlayerHas(activeGame, player);
        for (String type : types) {
            if ("industrial".equals(type)) {
                buttons.add(Button.success("arcExp_industrial", "Explore Industrials X 3"));
            }
            if ("cultural".equals(type)) {
                buttons.add(Button.primary("arcExp_cultural", "Explore Culturals X 3"));
            }
            if ("hazardous".equals(type)) {
                buttons.add(Button.danger("arcExp_hazardous", "Explore Hazardous X 3"));
            }
        }
        return buttons;
    }

    public static void resolveArcExpButtons(Game activeGame, Player player, String buttonID, ButtonInteractionEvent event, String trueIdentity) {
        String type = buttonID.replace("arcExp_", "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            String cardID = activeGame.drawExplore(type);
            sb.append(new ExploreAndDiscard().displayExplore(cardID)).append(System.lineSeparator());
            String card = Mapper.getExploreRepresentation(cardID);
            String[] cardInfo = card.split(";");
            String cardType = cardInfo[3];
            if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
                sb.append(trueIdentity).append(" Gained relic fragment\n");
                player.addFragment(cardID);
                activeGame.purgeExplore(cardID);
            }
        }
        MessageChannel channel = ButtonHelper.getCorrectChannel(player, activeGame);
        MessageHelper.sendMessageToChannel(channel, sb.toString());
        event.getMessage().delete().queue();
    }

    public static List<Button> getRepealLawButtons(Game activeGame, Player player) {
        List<Button> lawButtons = new ArrayList<>();
        for(String law : activeGame.getLaws().keySet()){
            lawButtons.add(Button.success("repealLaw_"+activeGame.getLaws().get(law),Mapper.getAgendaTitle(law)));
        }
        return lawButtons;
    }

   public static List<Button> getDivertFundingLoseTechOptions(Player player, Game activeGame) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (String tech : player.getTechs()) {
            TechnologyModel techM = Mapper.getTech(tech);
            if (!techM.getType().toString().equalsIgnoreCase("unitupgrade") && (techM.getFaction().isEmpty() || techM.getFaction().orElse("").length() < 1)) {
                buttons.add(Button.secondary(finChecker + "divertFunding@" + tech, techM.getName()));
            }
        }
        return buttons;
    }

    public static void divertFunding(Game activeGame, Player player, String buttonID, ButtonInteractionEvent event){
        String techOut = buttonID.split("@")[1];
        player.removeTech(techOut);
        TechnologyModel techM1 = Mapper.getTech(techOut);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdent(player) + " removed the tech "+techM1.getName());
        resolveFocusedResearch(activeGame, player, buttonID, event);
        event.getMessage().delete().queue();
    }


    public static void resolveForwardSupplyBaseStep2(Player hacan, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player player = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Could not resolve target player, please resolve manually.");
            return;
        }
        int oldTg = player.getTg();
        player.setTg(oldTg + 1);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
            ButtonHelper.getIdentOrColor(player, activeGame) + " gained 1tg due to forward supply base (" + oldTg + "->" + player.getTg() + ")");
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(hacan, activeGame), ButtonHelper.getIdentOrColor(player, activeGame) + " gained 1tg due to forward supply base");
        }
        ButtonHelperAbilities.pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
        event.getMessage().delete().queue();
    }
    public static void resolveForwardSupplyBaseStep1(Player hacan, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player player = hacan;
        int oldTg = player.getTg();
        player.setTg(oldTg + 3);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
            ButtonHelper.getIdent(player) + " gained 3tg (" + oldTg + "->" + player.getTg() + ")");
        ButtonHelperAbilities.pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 3);
        List<Button> buttons = new ArrayList<Button>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == hacan) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("forwardSupplyBaseStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("forwardSupplyBaseStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " choose who should get 1tg", buttons);
    }

    public static void resolveReparationsStep1(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<Button>();
        String message = ButtonHelper.getTrueIdentity(player, activeGame) + " Click the names of the planet you wish to ready";
        buttons = Helper.getPlanetRefreshButtons(event, player, activeGame);
        Button DoneRefreshing = Button.danger("deleteButtons", "Done Readying Planets");
        buttons.add(DoneRefreshing);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
        buttons = new ArrayList<Button>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("reparationsStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("reparationsStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " tell the bot who took the planet from you", buttons);
    }

    public static void resolveFrontlineDeployment(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<Button>();
        String message = ButtonHelper.getTrueIdentity(player, activeGame) + " Click the names of the planet you wish to drop 3 infantry on";
        buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, activeGame, "3gf", "placeOneNDone_skipbuild"));
        Button DoneRefreshing = Button.danger("deleteButtons", "Done Readying Planets");
        buttons.add(DoneRefreshing);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
       
        event.getMessage().delete().queue();
    }

    public static void resolveUnexpectedAction(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        
        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, activeGame, event, "unexpected");
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to remove token.", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveUprisingStep1(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<Button>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("uprisingStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("uprisingStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " tell the bot who's planet you want to uprise", buttons);
    }

    public static void resolveInsubStep1(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<Button>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player || p2.getTacticalCC() < 1) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("insubStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("insubStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " tell the bot which player you want to subtract a tactical cc from", buttons);
    }
    public static void resolveUnstableStep1(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<Button>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("unstableStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("unstableStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " tell the bot who's planet you want to unstable planet", buttons);
    }
    public static void resolveInsubStep2(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        p2.setTacticalCC(p2.getTacticalCC()-1);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " you subtracted 1 tactical cc from " + ButtonHelper.getIdentOrColor(player, activeGame));
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), ButtonHelper.getTrueIdentity(p2, activeGame) + " you lost a tactic cc due to insubordination ("+(player.getTacticalCC()+1)+"->"+player.getTacticalCC()+").");
        event.getMessage().delete().queue();
    }

    public static void resolveReparationsStep2(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if(p2.getReadiedPlanets().size() == 0){
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Chosen player had no readied planets. This is fine and nothing more needs to be done.");
            event.getMessage().delete().queue();
            return;
        }
        List<Button> buttons = new ArrayList<Button>();
        for(String planet : p2.getReadiedPlanets()){
            buttons.add(Button.secondary("reparationsStep3_" + p2.getFaction()+"_"+planet, Helper.getPlanetRepresentation(planet, activeGame)));
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " select the planet you want to exhaust", buttons);
    }

    public static void resolveUprisingStep2(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if(p2.getReadiedPlanets().size() == 0){
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Chosen player had no readied planets. Nothing has been done.");
            event.getMessage().delete().queue();
            return;
        }
        List<Button> buttons = new ArrayList<Button>();
        for(String planet : p2.getReadiedPlanets()){
            buttons.add(Button.secondary("uprisingStep3_" + p2.getFaction()+"_"+planet, Helper.getPlanetRepresentation(planet, activeGame)));
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " select the planet you want to exhaust", buttons);
    }

    public static void resolveUnstableStep2(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<Button>();
        for(String planet : p2.getPlanets()){
            if(planet.toLowerCase().contains("custodia")){
                continue;
            }
            Planet p = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            if(p != null && (p.getOriginalPlanetType().equalsIgnoreCase("hazardous") || p.getTokenList().contains("attachment_titanspn.png"))){
                buttons.add(Button.secondary("unstableStep3_" + p2.getFaction()+"_"+planet, Helper.getPlanetRepresentation(planet, activeGame)));
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " select the planet you want to exhaust", buttons);
    }

    public static void resolveUnstableStep3(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, activeGame);
        event.getMessage().delete().queue();
        if(p2.getReadiedPlanets().contains(planet)){
            p2.exhaustPlanet(planet);
        }
        if (p2.hasInf2Tech()) {
            UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            int amount = uH.getUnitCount(UnitType.Infantry, p2.getColor());
            if(amount > 3){
                amount = 3;
            }
            ButtonHelper.resolveInfantryDeath(activeGame, p2, amount);
        }
        new RemoveUnits().unitParsing(event, p2.getColor(), activeGame.getTileFromPlanet(planet), "3 inf "+planet, activeGame);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " you exhausted " + planetRep + " and killed up to 3 infantry there");
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), ButtonHelper.getTrueIdentity(p2, activeGame) + " your planet "+planetRep+" was exhausted and up to 3 infantry were destroyed.");
     }

     public static void resolveUprisingStep3(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, activeGame);
        event.getMessage().delete().queue();
        p2.exhaustPlanet(planet);
        int resValue = Helper.getPlanetResources(planet, activeGame);
        int oldTg = player.getTg();
        int count = resValue;
        player.setTg(oldTg+count);
        MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdent(player)+ " gained "+count+ " tgs ("+oldTg +"->"+player.getTg()+")");
        ButtonHelperAbilities.pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, count);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " you exhausted " + planetRep);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), ButtonHelper.getTrueIdentity(p2, activeGame) + " your planet "+planetRep+" was exhausted.");
     }

     public static void resolveReparationsStep3(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, activeGame);
        event.getMessage().delete().queue();
        p2.exhaustPlanet(planet);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " you exhausted " + planetRep);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), ButtonHelper.getTrueIdentity(p2, activeGame) + " your planet "+planetRep+" was exhausted.");
     }

     





    public static void resolveFocusedResearch(Game activeGame, Player player, String buttonID, ButtonInteractionEvent event){
        if(!player.hasAbility("propagation")){
            activeGame.setComponentAction(true);
            Button getTech = Button.success("acquireATech", "Get a tech");
            List<Button> buttons = new ArrayList<>();
            buttons.add(getTech);
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                ButtonHelper.getTrueIdentity(player, activeGame) + " you can use the button to get your tech", buttons);
        }else{
            Button getTactic = Button.success("increase_tactic_cc", "Gain 1 Tactic CC");
            Button getFleet = Button.success("increase_fleet_cc", "Gain 1 Fleet CC");
            Button getStrat = Button.success("increase_strategy_cc", "Gain 1 Strategy CC");
            Button DoneGainingCC = Button.danger("deleteButtons", "Done Gaining CCs");
            List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
            String message2 = player.getRepresentation() + "! Your current CCs are " + player.getCCRepresentation() + ". Use buttons to gain a CC";
            MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message2, buttons);
        }
    }

    public static void repealLaw(Game activeGame, Player player, String buttonID, ButtonInteractionEvent event){
        String numID = buttonID.split("_")[1];
        String name = "";
        for(String law : activeGame.getLaws().keySet()){
            if(numID.equalsIgnoreCase(""+activeGame.getLaws().get(law))){
                name = law;
            }
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdent(player) + " repealed "+Mapper.getAgendaTitle(name));
        if(activeGame.isFoWMode()){
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), Mapper.getAgendaTitle(name)+ " was repealed");
        }
        activeGame.removeLaw(name);
        event.getMessage().delete().queue();
    }
    public static List<Button> getPlagiarizeButtons(Game activeGame, Player player) {
        List<String> techToGain = new ArrayList<>();
        for (Player p2 : player.getNeighbouringPlayers()) {
            techToGain = ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(player, p2, techToGain, activeGame);
        }
        List<Button> techs = new ArrayList<>();
        for (String tech : techToGain) {
            if ("".equals(Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction().orElse(""))) {
                techs.add(Button.success("getTech_" + Mapper.getTech(tech).getName() + "_noPay", Mapper.getTech(tech).getName()));
            }
        }
        return techs;
    }

    public static void resolveReverse(Game activeGame, Player player, String buttonID, ButtonInteractionEvent event) {
        String acName = buttonID.split("_")[1];
        List<String> acStrings = new ArrayList<String>();
        acStrings.addAll(activeGame.getDiscardActionCards().keySet());
        for (String acStringID : acStrings) {
            ActionCardModel actionCard = Mapper.getActionCard(acStringID);
            String actionCardTitle = actionCard.getName();
            if (acName.equalsIgnoreCase(actionCardTitle)) {
                boolean picked = activeGame.pickActionCard(player.getUserID(), activeGame.getDiscardActionCards().get(acStringID));
                if (!picked) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action Card ID found, please retry");
                    return;
                }
                String sb = "Game: " + activeGame.getName() + " " +
                    "Player: " + player.getUserName() + "\n" +
                    "Picked card from Discards: " +
                    Mapper.getActionCard(acStringID).getRepresentation() + "\n";
                MessageHelper.sendMessageToChannel(event.getChannel(), sb);

                ACInfo.sendActionCardInfo(activeGame, player);
            }
        }
        event.getMessage().delete().queue();
    }

    public static void economicInitiative(Player player, Game activeGame, ButtonInteractionEvent event){
        for(String planet: player.getPlanetsAllianceMode()){
            if(planet.toLowerCase().contains("custodia")){
                continue;
            }
            Planet p = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            if(p != null && (p.getOriginalPlanetType().equalsIgnoreCase("cultural") || p.getTokenList().contains("attachment_titanspn.png"))){
                player.refreshPlanet(planet);
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdent(player)+ " readied each cultural planet");
        event.getMessage().delete().queue();
    }

    public static void industrialInitiative(Player player, Game activeGame, ButtonInteractionEvent event){
        int oldTg = player.getTg();
        int count = 0;
        for(String planet: player.getPlanetsAllianceMode()){
            if(planet.toLowerCase().contains("custodia") || planet.contains("ghoti")){
                continue;
            }
            Planet p = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            if(p != null && (p.getOriginalPlanetType().equalsIgnoreCase("industrial") || p.getTokenList().contains("attachment_titanspn.png"))){
                count = count +1;
            }
        }
        player.setTg(oldTg+count);
        MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdent(player)+ " gained "+count+ " tgs("+oldTg +"->"+player.getTg()+")");
        ButtonHelperAbilities.pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, count);
        event.getMessage().delete().queue();
    }

    public static void miningInitiative(Player player, Game activeGame, ButtonInteractionEvent event){
        int oldTg = player.getTg();
        int count = 0;
        for(String planet: player.getPlanetsAllianceMode()){
            if(planet.toLowerCase().contains("custodia") || planet.contains("ghoti")){
                continue;
            }
            Planet p = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            if(p != null && p.getResources() > count){
                count = p.getResources();
            }
        }
        player.setTg(oldTg+count);
        MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdent(player)+ " gained "+count+ " tgs ("+oldTg +"->"+player.getTg()+") from their highest resource planet");
        ButtonHelperAbilities.pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, count);
        event.getMessage().delete().queue();
    }
}