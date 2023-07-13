package ti4.commands.planet;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class PlanetExhaustAbility extends PlanetAddRemove {
    public PlanetExhaustAbility() {
        super(Constants.PLANET_EXHAUST_ABILITY, "Exhaust Planet Ability");
    }

    @Override
    public void doAction(Player player, String planet, Map activeMap) {
        player.exhaustPlanetAbility(planet);
        MessageChannel channel = activeMap.getMainGameChannel();
        if(activeMap.isFoWMode()){
            channel = player.getPrivateChannel();
        }
        List<Button> buttons = new ArrayList<Button>();
        String message = "blank";
        if(AliasHandler.resolvePlanet(planet).equalsIgnoreCase("mallice")){
            MessageHelper.sendMessageToChannel(channel, "Chose to Exhaust Mallice Ability");
            message = "Use buttons to gain 2 tg or wash your commodities";
            buttons.add(Button.success("mallice_2_tg","Gain 2tg"));
            buttons.add(Button.success("mallice_convert_comm","Convert Commodities"));
            
        }
        if(AliasHandler.resolvePlanet(planet).equalsIgnoreCase("hopesend")){
            MessageHelper.sendMessageToChannel(channel, "Chose to Exhaust Hope's End Ability");
            message = "Use buttons to drop a mech on a planet or draw an AC";
            buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, activeMap, "mech", "placeOneNDone_skipbuild"));
            buttons.add(Button.success("draw_1_ACDelete","Draw 1 AC"));
        }
        if(AliasHandler.resolvePlanet(planet).equalsIgnoreCase("primor")){
            MessageHelper.sendMessageToChannel(channel, "Chose to Exhaust Primor's Ability");
            buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, activeMap, "2gf", "placeOneNDone_skipbuild"));
            message = "Use buttons to drop 2 infantry on a planet";
        }
        if(AliasHandler.resolvePlanet(planet).equalsIgnoreCase("mirage")){
            MessageHelper.sendMessageToChannel(channel, "Chose to Exhaust Mirage's Ability");
             buttons.addAll(Helper.getTileWithShipsPlaceUnitButtons(player, activeMap, "2ff", "placeOneNDone_skipbuild"));
            message = "Use buttons to put 2 fighters with your ships";
        }
        buttons.add(Button.danger("deleteButtons","Delete these buttons"));

        if(!message.equalsIgnoreCase("blank"))
        {
            
            MessageHelper.sendMessageToChannelWithButtons(channel,message, buttons);
           
        }

    }
}
