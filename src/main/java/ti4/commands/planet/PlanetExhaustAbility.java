package ti4.commands.planet;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PlanetExhaustAbility extends PlanetAddRemove {
    public PlanetExhaustAbility() {
        super(Constants.PLANET_EXHAUST_ABILITY, "Exhaust Planet Ability");
    }

    @Override
    public void doAction(Player player, String planet, Game activeGame) {
        player.exhaustPlanetAbility(planet);
        MessageChannel channel = activeGame.getMainGameChannel();
        if(activeGame.isFoWMode()){
            channel = player.getPrivateChannel();
        }
        List<Button> buttons = new ArrayList<>();
        String message = "blank";
        if("mallice".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))){
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player)+" Chose to Exhaust Mallice Ability");
            message = "Use buttons to gain 2 tg or wash your commodities";
            buttons.add(Button.success("mallice_2_tg","Gain 2tg"));
            buttons.add(Button.success("mallice_convert_comm","Convert Commodities"));
            
        }
        if("hopesend".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))){
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player)+" Chose to Exhaust Hope's End Ability");
            message = "Use buttons to drop a mech on a planet or draw an AC";
            buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, activeGame, "mech", "placeOneNDone_skipbuild"));
            if(player.hasAbility("scheming")){
                buttons.add(Button.success("draw_2_ACDelete", "Draw 2 AC (With Scheming)"));
            }else{
                buttons.add(Button.success("draw_1_ACDelete", "Draw 1 AC"));
            }
        }
        if("primor".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))){
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player)+" Chose to Exhaust Primor's Ability");
            buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, activeGame, "2gf", "placeOneNDone_skipbuild"));
            message = "Use buttons to drop 2 infantry on a planet";
        }
        if("mr".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))){
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player)+" Chose to Exhaust Mecatol Rex's Ability");
            buttons.addAll(ButtonHelper.customRexLegendary(player, activeGame));
            message = "Use buttons to destroy a ground force on a legendary or planet adjacent to rex";
        }
        if("mirage".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))){
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player)+" Chose to Exhaust Mirage's Ability");
             buttons.addAll(Helper.getTileWithShipsPlaceUnitButtons(player, activeGame, "2ff", "placeOneNDone_skipbuild"));
            message = "Use buttons to put 2 fighters with your ships";
        }
        buttons.add(Button.danger("deleteButtons","Delete these buttons"));

        if(!"blank".equalsIgnoreCase(message))
        {
            
            MessageHelper.sendMessageToChannelWithButtons(channel,message, buttons);
           
        }

    }
}
