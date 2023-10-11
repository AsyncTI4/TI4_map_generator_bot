package ti4.helpers;

import org.apache.commons.lang3.StringUtils;

import ti4.generator.Mapper;
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
            String tokenToAdd = null;
            if (player.ownsUnit("olradin_mech_positive")) {
                tokenToAdd = Constants.OLRADIN_MECH_INF_PNG;
            } else if (player.ownsUnit("olradin_mech_negative")) {
                tokenToAdd = Constants.OLRADIN_MECH_RES_PNG;
            } else {
                continue;
            }

            for (Tile tile : activeMap.getTileMap().values()) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    if (unitHolder != null && unitHolder instanceof Planet) {
                        Planet planet = (Planet) unitHolder;
                        if (planet != null && player.getPlanets().contains(planet.getName())) {                   
                            if (!oneMechCheck(planet.getName(), activeMap, player) && ((planet.getTokenList().contains(Constants.OLRADIN_MECH_INF_PNG)) || (planet.getTokenList().contains(Constants.OLRADIN_MECH_RES_PNG)))) {
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
        String mechKey = colorID + "_mf.png";

        if (unitHolder.getUnits() != null) {
            if (unitHolder.getUnits().get(mechKey) != null) {
                numMechs = unitHolder.getUnits().get(mechKey);
            }
        }
        return numMechs == 1;
    }

    public static void handleOlradinPoliciesWhenExhaustingPlanets(Game activeGame, Player player, String planet) {
        if (activeGame == null || !activeGame.isDiscordantStarsMode() || !"action".equalsIgnoreCase(activeGame.getCurrentPhase()) || player == null || !player.hasOlradinPolicies()) return;
        PlanetModel planetModel = Mapper.getPlanet(planet);
        if (planetModel == null) return;
        StringBuilder sb = new StringBuilder();

        if (planetModel.isLegendary()) {
            sb.append("You exhausted ").append(Emojis.LegendaryPlanet).append(planetModel.getName()).append(" and triggered the following abilities:\n");
            resolveEnvironmentPreserveAbility(player, planetModel, sb);
            resolveEconomyEmpowerAbility(player, sb);
            resolvePeopleConnectAbility(player, planetModel, sb);
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, sb.toString());
            sb.append("Please be mindful that these abilities can only be used 'Once per Action' and this suggestion *may* be incorrectly timed.\nPlease resolve these abilities yourself and report them to all players.");
            return;
        }

        switch (planetModel.getPlanetType()) {
            case HAZARDOUS -> {
                sb.append("You exhausted ").append(Emojis.Hazardous).append(planetModel.getName()).append(" and triggered the following abilities:\n");
                resolveEnvironmentPreserveAbility(player, planetModel, sb);
            }
            case INDUSTRIAL -> {
                sb.append("You exhausted ").append(Emojis.Industrial).append(planetModel.getName()).append(" and triggered the following abilities:\n");
                resolveEconomyEmpowerAbility(player, sb);
                resolveEconomyExploitAbility(player, sb);
            }
            case CULTURAL -> {
                sb.append("You exhausted ").append(Emojis.Cultural).append(planetModel.getName()).append(" and triggered the following abilities:\n");
                resolvePeopleConnectAbility(player, planetModel, sb);
            }
            default -> {
                return;
            }
        }
        sb.append("Please be mindful that these abilities can only be used 'Once per Action' and this suggestion *may* be incorrectly timed.\nPlease resolve these abilities yourself and report them to all players.");
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, sb.toString());
    }

    private static void resolveEconomyExploitAbility(Player player, StringBuilder sb) {
        if (!player.getHasUsedEconomyExploitAbility() && player.hasAbility("policy_the_economy_exploit")) { //add a fighter with ships
            player.setHasUsedEconomyExploitAbility(true);
            sb.append("**The Economy - Exploit (+)**: You may place 1 " + Emojis.fighter + "Fighter from your reinforcements in a system that contains 1 or more of your ships.\n");
        }
    }

    private static void resolvePeopleConnectAbility(Player player, PlanetModel planetModel, StringBuilder sb) {
        if (!player.getHasUsedPeopleConnectAbility() && player.hasAbility("policy_the_people_connect")) {
            player.setHasUsedPeopleConnectAbility(true);
            sb.append("> **The People - Connect (+)**: You may move 1 " + Emojis.infantry + "Infantry on " + planetModel.getName() + " to another planet you control.\n");
        }
    }

    private static void resolveEconomyEmpowerAbility(Player player, StringBuilder sb) {
        if (!player.getHasUsedEconomyEmpowerAbility() && player.hasAbility("policy_the_economy_empower")) {
            player.setHasUsedEconomyEmpowerAbility(true);
            sb.append("> **The Economy - Empower (+)**: You gain 1 " + Emojis.comm + "commodity.\n");
        }
    }

    private static void resolveEnvironmentPreserveAbility(Player player, PlanetModel planetModel, StringBuilder sb) {
        if (!player.getHasUsedEnvironmentPreserveAbility() && player.hasAbility("policy_the_environment_preserve")) {
            player.setHasUsedEnvironmentPreserveAbility(true);
            String planetType = planetModel.getPlanetType().toString();
            String fragmentType = Helper.getEmojiFromDiscord(StringUtils.left(planetType, 1) + "frag");
            sb.append("> **The Environment - Preserve (+)**: You may reveal the top card of the " + Helper.getEmojiFromDiscord(planetType) + planetType + " deck; if it is a " + fragmentType + " relic fragment, gain it, otherwise discard that card.\n");
        }
    }
}
