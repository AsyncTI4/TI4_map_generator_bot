package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import software.amazon.awssdk.utils.StringUtils;
import ti4.commands.status.ScorePublic;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
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
    public void doAction(Player player, String planet, Map activeMap) {
      doAction(player, planet, activeMap, null);
    }

    public void doAction(Player player, String planet, Map activeMap, GenericInteractionCreateEvent event) {
        player.addPlanet(planet);
        player.exhaustPlanet(planet);
        if (planet.equals("mirage")){
            activeMap.clearPlanetsCache();
        }
        UnitHolder unitHolder = activeMap.getPlanetsInfo().get(planet);
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
                activeMap.scorePublicObjective(player.getUserID(), 0);
                if (event != null && event instanceof SlashCommandInteractionEvent) {
                    ScorePublic.informAboutScoring(event, ((SlashCommandInteractionEvent) event).getChannel(), activeMap, player, 0);
                }
            }
        }
        boolean alreadyOwned = false;
        for (Player player_ : activeMap.getPlayers().values()) {
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

        if(activeMap.getActivePlayer() != null && !(activeMap.getActivePlayer().equalsIgnoreCase("")) && player.hasAbility("scavenge") && event != null)
        {
            String fac = Helper.getFactionIconFromDiscord(player.getFaction());
            
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), fac+" gained 1tg from Scavenge ("+player.getTg()+"->"+(player.getTg()+1)+"). Reminder that this is optional, but was done automatically for convenience. You do not legally have this tg prior to exploring." );
            player.setTg(player.getTg()+1);
        }
        if (!alreadyOwned && !activeMap.isAllianceMode() && (!planet.equals("mirage"))&& !activeMap.isBaseGameMode()) {
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = false;
            if (planetReal != null && planetReal.getOriginalPlanetType() != null && (planetReal.getOriginalPlanetType().equalsIgnoreCase("industrial") || planetReal.getOriginalPlanetType().equalsIgnoreCase("cultural") || planetReal.getOriginalPlanetType().equalsIgnoreCase("hazardous"))) {
                oneOfThree = true;
            }
            if ( oneOfThree) {
                String message = "Click Button To Explore";
                String drawColor = planetReal.getOriginalPlanetType();
                Button resolveExplore2 = Button.success("movedNExplored_filler_"+planet+"_"+drawColor, "Explore "+Helper.getPlanetRepresentation(planet, activeMap));
                List<Button> buttons = List.of(resolveExplore2);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);

            }

        }
    }
}
