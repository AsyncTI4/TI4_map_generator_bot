package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.status.ScorePublic;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

import java.util.List;

public class PlanetAdd extends PlanetAddRemove {
    public PlanetAdd() {
        super(Constants.PLANET_ADD, "Add Planet");
    }

    @Override
    public void doAction(Player player, String planet, Map map) {
      doAction(player, planet, map, null);
    }

    public void doAction(Player player, String planet, Map map, GenericInteractionCreateEvent event) {
        player.addPlanet(planet);
        player.exhaustPlanet(planet);
        if (planet.equals("mirage")){
            map.clearPlanetsCache();
        }
        UnitHolder unitHolder = map.getPlanetsInfo().get(planet);
        String color = player.getColor();
        boolean moveTitanPN = false;
        if (unitHolder != null && color != null && !"null".equals(color)) {
            String ccID = Mapper.getControlID(color);
            String ccPath = Mapper.getCCPath(ccID);
            if (ccPath != null) {
                unitHolder.addControl(ccID);
            }
            if (unitHolder.getTokenList().contains(Constants.ATTACHMENT_TITANSPN_PNG)) {
                moveTitanPN = true;
            } else if (unitHolder.getTokenList().contains(Constants.CUSTODIAN_TOKEN_PNG)) {
                unitHolder.removeToken(Constants.CUSTODIAN_TOKEN_PNG);
                map.scorePublicObjective(player.getUserID(), 0);
                if (event != null && event instanceof SlashCommandInteractionEvent) {
                    ScorePublic.informAboutScoring(event, ((SlashCommandInteractionEvent) event).getChannel(), map, player, 0);
                }
            }
        }
        boolean alreadyOwned = false;
        for (Player player_ : map.getPlayers().values()) {
            if (player_ != player) {
                List<String> planets = player_.getPlanets();
                if (planets.contains(planet)) {
                    if (player_.getExhaustedPlanetsAbilities().contains(planet)) {
                        player.exhaustPlanetAbility(planet);
                    }
                    alreadyOwned = true;
                    player_.removePlanet(planet);
                    if (moveTitanPN){
                       if (player_.getPromissoryNotesInPlayArea().contains(Constants.TERRAFORM)){
                           player_.removePromissoryNote(Constants.TERRAFORM);
                           player.setPromissoryNote(Constants.TERRAFORM);
                           player.setPromissoryNotesInPlayArea(Constants.TERRAFORM);
                       }
                    }
                }
            }
        }
        
        if (!alreadyOwned && !map.isAllianceMode())
        {
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = false;
            if (planetReal != null && planetReal.getOriginalPlanetType() != null && (planetReal.getOriginalPlanetType().equalsIgnoreCase("industrial") || planetReal.getOriginalPlanetType().equalsIgnoreCase("cultural") || planetReal.getOriginalPlanetType().equalsIgnoreCase("hazardous")))
            {
                oneOfThree = true;
            }
            if ( oneOfThree)
            {
                String message = "Click Button To Explore";
                String drawColor = planetReal.getOriginalPlanetType();
                Button resolveExplore2 = Button.success("movedNExplored_filler_"+planet+"_"+drawColor, "Explore "+Helper.getPlanetRepresentation(planet, map));
                List<Button> buttons = List.of(resolveExplore2);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);

            }
           
        }
    }
}
