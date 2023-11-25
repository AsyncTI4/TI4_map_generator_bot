package ti4.commands.ds;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

public class TrapReveal extends DiscordantStarsSubcommandData {

    public TrapReveal() {
        super(Constants.LIZHO_REVEAL_TRAP, "Select planets were to reveal trap tokens");
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.LIZHO_TRAP_ID, "Trap ID").setRequired(true));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        OptionMapping planetOption = event.getOption(Constants.PLANET);
        if (planetOption == null){
            return;
        }
        String planetName = planetOption.getAsString();
        if (!activeGame.getPlanets().contains(planetName)) {
            MessageHelper.replyToMessage(event, "Planet not found in map");
            return;
        }

        OptionMapping trapIDOption = event.getOption(Constants.LIZHO_TRAP_ID);
        if (trapIDOption == null){
            return;
        }

        Collection<Integer> values = player.getTrapCards().values();
        int trapID = trapIDOption.getAsInt();
        if (!values.contains(trapID)){
            MessageHelper.replyToMessage(event, "Trap ID not found");
            return;
        }
        String stringTrapID = "";
        for(String trapIDS : player.getTrapCards().keySet()){
            if(player.getTrapCards().get(trapIDS) == trapID){
                stringTrapID = trapIDS;
            }
        }
        revealTrapForPlanet(event, activeGame, planetName, stringTrapID, player, true);
    }

    public void revealTrapForPlanet(GenericInteractionCreateEvent event, Game activeGame, String planetName, String trap, Player player, boolean reveal) {
        if (player.getTrapCardsPlanets().containsValue(planetName) || planetName == null) {
            LinkedHashMap<String, String> trapCardsPlanets = player.getTrapCardsPlanets();
            for (Map.Entry<String, String> entry : trapCardsPlanets.entrySet()) {
                String planet = entry.getValue();
                if (planetName.equals(planet) || planet == null) {
                    ButtonHelperAbilities.removeATrapToken(activeGame, planetName);
                    player.removeTrapCardPlanet(trap);
                    player.setTrapCard(trap);
                    Map<String, String> dsHandcards = Mapper.getDSHandcards();
                    String info = dsHandcards.get(trap);
                    String[] split = info.split(";");
                    String trapType = split[0];
                    String trapName = split[1];
                    String trapText = split[2];
                    Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
                    String representation = planetRepresentations.get(planet);
                    if (representation == null) {
                        representation = planet;
                    }
                    if(reveal && planet != null){

                        
                        String sb = "__**" + "Trap: " + trapName + "**__" + " - " + trapText + "\n" +
                                "__**" + "Has been revealed on planet: " + representation + "**__";
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), sb);
                        if(trapName.equalsIgnoreCase("Minefields")){
                            for(Player p2: activeGame.getRealPlayers()){
                                if(p2 == player){
                                    continue;
                                }
                                new RemoveUnits().unitParsing(event, p2.getColor(), activeGame.getTileFromPlanet(planet), "2 inf "+planet, activeGame);
                            }
                            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Destroyed up to 2 enemy infantry from "+representation);
                        }
                        if(trapName.equalsIgnoreCase("Account Siphon")){
                            for(Player p2: activeGame.getRealPlayers()){
                                if(p2 == player){
                                    continue;
                                }
                                if(p2.getPlanets().contains(planet)){
                                    List<Button> buttons = new ArrayList<>();
                                    buttons.add(Button.success("steal2tg_"+p2.getFaction(), "Steal 2tg from "+ButtonHelper.getIdentOrColor(p2, activeGame)));
                                    buttons.add(Button.primary("steal3comm_"+p2.getFaction(), "Steal 3 comms from "+ButtonHelper.getIdentOrColor(p2, activeGame)));
                                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " use buttons to resolve", buttons);
                                }
                            }
                        }
                    }else{
                        String sb = "A trap has been removed from planet: " + representation;
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), sb);
                    }

                    
                    return;
                }
            }
        }
        else{
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), ButtonHelper.getTrueIdentity(player, activeGame) + " could not find a trap for the planet "+Helper.getPlanetRepresentation(planetName, activeGame));

        }
    }

    public void steal2Tg(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID){
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        int count = 2; 
        if(p2.getTg() < 2){
            count = p2.getTg();
        }
        p2.setTg(p2.getTg()-count);
        player.setTg(player.getTg()+count);
        String msg1 = ButtonHelper.getTrueIdentity(p2, activeGame) + " you had "+count+" tgs stolen by a trap";
        String msg2 = ButtonHelper.getTrueIdentity(player, activeGame) + " you stole "+count+" tgs via trap";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), msg1);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg2);
        event.getMessage().delete().queue();
    }
    public void steal3Comm(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID){
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        int count = 3; 
        if(p2.getCommodities() < 3){
            count = p2.getCommodities();
        }
        p2.setCommodities(p2.getCommodities()-count);
        player.setTg(player.getTg()+count);
        String msg1 = ButtonHelper.getTrueIdentity(p2, activeGame) + " you had "+count+" comms stolen by a trap";
        String msg2 = ButtonHelper.getTrueIdentity(player, activeGame) + " you stole "+count+" comms via trap";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), msg1);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg2);
        event.getMessage().delete().queue();
    }
}
