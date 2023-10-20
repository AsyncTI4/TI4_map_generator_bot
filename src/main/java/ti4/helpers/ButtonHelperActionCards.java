package ti4.helpers;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.explore.ExploreAndDiscard;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;

public class ButtonHelperActionCards {

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

    public static List<Button> getPlagiarizeButtons(Game activeGame, Player player) {
        List<String> techToGain = new ArrayList<>();
        for (Player p2 : player.getNeighbouringPlayers()) {
            techToGain = ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(player, p2, techToGain, activeGame);
        }
        List<Button> techs = new ArrayList<>();
        for (String tech : techToGain) {
            if ("".equals(Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction())) {
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
            if(planet.toLowerCase().contains("custodia")){
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
            if(planet.toLowerCase().contains("custodia")){
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