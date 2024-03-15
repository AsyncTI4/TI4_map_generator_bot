package ti4.helpers;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;

public class DiscordantStarsHelper {
    public static void checkGardenWorlds(Game activeGame) {
        for (Player player : activeGame.getPlayers().values()) {
            if (player.hasAbility(Constants.GARDEN_WORLDS)) {
                for (Tile tile : activeGame.getTileMap().values()) {
                    for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                        if (unitHolder instanceof Planet planet) {
                            if (player.getPlanets().contains(planet.getName())) {
                                if (planet.hasGroundForces(player) && planet.getTokenList().contains(Constants.GARDEN_WORLDS_PNG)) {
                                    planet.removeToken(Constants.GARDEN_WORLDS_PNG);
                                } else if (!planet.hasGroundForces(player)) {
                                    planet.addToken(Constants.GARDEN_WORLDS_PNG);
                                }
                            } else if (planet.getTokenList().contains(Constants.GARDEN_WORLDS_PNG)) {
                                planet.removeToken(Constants.GARDEN_WORLDS_PNG);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void checkSigil(Game activeGame) { //Edyn Mech adds Sigil tokens under them
        for (Player player : activeGame.getPlayers().values()) {
            if (player.ownsUnit("edyn_mech")) {
                for (Tile tile : activeGame.getTileMap().values()) {
                    if (player.hasMechInSystem(tile)) {
                        tile.addToken(Constants.SIGIL, Constants.SPACE);
                    } else {
                        tile.removeToken(Constants.SIGIL, Constants.SPACE);
                    }
                }
            }
        }
    }

    public static void checkOlradinMech(Game activeMap) {
        for (Player player : activeMap.getPlayers().values()) {
            String tokenToAdd;
            if (player.ownsUnit("olradin_mech_positive")) {
                tokenToAdd = Constants.OLRADIN_MECH_INF_PNG;
            } else if (player.ownsUnit("olradin_mech_negative")) {
                tokenToAdd = Constants.OLRADIN_MECH_RES_PNG;
            } else {
                continue;
            }

            for (Tile tile : activeMap.getTileMap().values()) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    if (unitHolder instanceof Planet planet) {
                        if (player.getPlanets().contains(planet.getName())) {
                            if (!oneMechCheck(planet.getName(), activeMap, player)
                                && ((planet.getTokenList().contains(Constants.OLRADIN_MECH_INF_PNG)) || (planet.getTokenList().contains(Constants.OLRADIN_MECH_RES_PNG)))) {
                                planet.removeToken(Constants.OLRADIN_MECH_INF_PNG);
                                planet.removeToken(Constants.OLRADIN_MECH_RES_PNG);
                            } else if (oneMechCheck(planet.getName(), activeMap, player)) {
                                planet.addToken(tokenToAdd);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean oneMechCheck(String planetName, Game activeMap, Player player) {
        Tile tile = activeMap.getTile(AliasHandler.resolveTile(planetName));
        if (tile == null) return false;
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        int numMechs = 0;

        String colorID = Mapper.getColorID(player.getColor());
        if (unitHolder.getUnits() != null) {
            numMechs = unitHolder.getUnitCount(UnitType.Mech, colorID);
        }
        return numMechs == 1;
    }

    public static void handleOlradinPoliciesWhenExhaustingPlanets(Game activeGame, Player player, String planet) {
        if (activeGame == null || !activeGame.isDiscordantStarsMode() || !"action".equalsIgnoreCase(activeGame.getCurrentPhase()) || player == null || !player.hasOlradinPolicies()) return;
        PlanetModel planetModel = Mapper.getPlanet(planet);
        if (planetModel == null) return;
        UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
        Planet planetHolder = (Planet) unitHolder;
        if (planetHolder == null)
            return;

        boolean hasAbility = planetHolder.isHasAbility()
                || planetHolder.getTokenList().stream().anyMatch(token -> token.contains("nanoforge")
                        || token.contains("legendary") || token.contains("consulate"));
        if (hasAbility) {
            resolveEnvironmentPreserveAbility(player, planetModel, activeGame);
            resolveEconomyEmpowerAbility(player, activeGame, planetModel);
            resolvePeopleConnectAbility(player, planetModel, activeGame);
            return;
        }

        for(String type : ButtonHelper.getTypeOfPlanet(activeGame, planet)){
            switch (type) {
                case "hazardous" -> {
                    resolveEnvironmentPreserveAbility(player, planetModel, activeGame);
                }
                case "industrial" -> {
                    resolveEconomyEmpowerAbility(player, activeGame, planetModel);
                    resolveEconomyExploitAbility(player,  planetModel, activeGame);
                }
                case "cultural" -> {
                    resolvePeopleConnectAbility(player, planetModel, activeGame);
                }
                default -> {
                    return;
                }
            }
        }

    }

    private static void resolveEconomyExploitAbility(Player player, PlanetModel planetModel,Game activeGame) {
        if (!player.getHasUsedEconomyExploitAbility() && player.hasAbility("policy_the_economy_exploit")) { //add a fighter with ships
            player.setHasUsedEconomyExploitAbility(true);
            String msg = player.getRepresentation() +" Due to your exhausting of "+planetModel.getAutoCompleteName() +" you can resolve the following ability: **The Economy - Exploit (+)**: You may place 1 " + Emojis.fighter + "Fighter from your reinforcements in a system that contains 1 or more of your ships.";
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
            List<Button> buttons = new ArrayList<>(); 
            buttons.addAll(Helper.getTileWithShipsPlaceUnitButtons(player, activeGame, "ff", "placeOneNDone_skipbuild"));
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Resolve ability", buttons);
        }
    }

    private static void resolvePeopleConnectAbility(Player player, PlanetModel planetModel, Game activeGame) {
        UnitHolder uh = ButtonHelper.getUnitHolderFromPlanetName(planetModel.getId(), activeGame);
        
        if (!player.getHasUsedPeopleConnectAbility() && player.hasAbility("policy_the_people_connect") && uh != null && uh.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
            String msg = player.getRepresentation() +" Due to your exhausting of "+planetModel.getAutoCompleteName() +" you can resolve the following ability: **The People - Connect (+)**: You may move 1 " + Emojis.infantry + "Infantry on "+ planetModel.getName()+" to another planet you control.";
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
            List<Button> buttons = ButtonHelperAbilities.offerOlradinConnectButtons(player, activeGame, planetModel.getId());
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Resolve ability", buttons);
        }
    }

    private static void resolveEconomyEmpowerAbility(Player player,Game activeGame, PlanetModel planetModel) {
        if (!player.getHasUsedEconomyEmpowerAbility() && player.hasAbility("policy_the_economy_empower")) {
            player.setHasUsedEconomyEmpowerAbility(true);
            String msg = player.getRepresentation() +" Due to your exhausting of "+planetModel.getAutoCompleteName() +" you can resolve the following ability: **The Economy - Empower (+)**: You gain 1 " + Emojis.comm + "commodity.\n";
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
            Button getCommButton = Button.primary("gain_1_comms", "Gain 1 Commodity")
                        .withEmoji(Emoji.fromFormatted(Emojis.comm));
            MessageHelper.sendMessageToChannelWithButton(ButtonHelper.getCorrectChannel(player, activeGame), "Resolve ability", getCommButton);
        }
    }

    private static void resolveEnvironmentPreserveAbility(Player player, PlanetModel planetModel, Game activeGame) {
        if (!player.getHasUsedEnvironmentPreserveAbility() && player.hasAbility("policy_the_environment_preserve")) {
            List<Button> buttons = ButtonHelperAbilities.getOlradinPreserveButtons(activeGame, player, planetModel.getId());
            if(buttons.size() > 0){
                String msg = player.getRepresentation() +" Due to your exhausting of "+planetModel.getAutoCompleteName() +" you can resolve the following ability: **The Environment - Preserve (+)**: You may reveal the top card of the planets types exploration deck; if it is a relic fragment, gain it, otherwise discard that card.";
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Resolve ability", buttons);
            }
        }
    }
}
