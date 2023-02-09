package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.status.ScorePublic;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
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

    public void doAction(Player player, String planet, Map map, SlashCommandInteractionEvent event) {
        player.addPlanet(planet);
        player.exhaustPlanet(planet);
        if (planet.equals("mirage")){
            map.clearPlanetsCache();
        }
        UnitHolder unitHolder = map.getPlanetsInfo().get(planet);
        String color = player.getColor();
        boolean moveTitanPN = false;
        if (unitHolder != null && player.isActivePlayer()) {
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
                if (event != null){
                    ScorePublic.informAboutScoring(event, event.getChannel(), map, player, 0);
                }
            }
        }
        for (Player player_ : map.getPlayers().values()) {
            if (player_ != player) {
                List<String> planets = player_.getPlanets();
                if (planets.contains(planet)) {
                    if (player_.getExhaustedPlanetsAbilities().contains(planet)) {
                        player.exhaustPlanetAbility(planet);
                    }
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

    }
}
