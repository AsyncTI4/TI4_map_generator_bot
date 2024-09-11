package ti4.helpers;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
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
    public static void checkGardenWorlds(Game game) {
        Player player = Helper.getPlayerFromAbility(game, "garden_worlds");
        if (player == null) {
            return;
        }

        for (Tile tile : game.getTileMap().values()) {
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

    public static void checkSigil(Game game) { //Edyn Mech adds Sigil tokens under them
        Player player = Helper.getPlayerFromUnit(game, "edyn_mech");
        if (player == null) {
            return;
        }

        for (Tile tile : game.getTileMap().values()) {
            if (player.hasMechInSystem(tile)) {
                tile.addToken(Constants.SIGIL, Constants.SPACE);
            } else {
                tile.removeToken(Constants.SIGIL, Constants.SPACE);
            }
        }

    }

    public static void checkOlradinMech(Game activeMap) {
        for (Player player : activeMap.getPlayers().values()) {
            String tokenToAdd;
            String tokenToRemove;
            if (player.ownsUnit("olradin_mech_positive")) {
                tokenToAdd = Constants.OLRADIN_MECH_INF_PNG;
                tokenToRemove = Constants.OLRADIN_MECH_RES_PNG;
            } else if (player.ownsUnit("olradin_mech_negative")) {
                tokenToAdd = Constants.OLRADIN_MECH_RES_PNG;
                tokenToRemove = Constants.OLRADIN_MECH_INF_PNG;
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
                                planet.removeToken(tokenToRemove);
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

    public static void handleOlradinPoliciesWhenExhaustingPlanets(Game game, Player player, String planet) {
        if (game == null || !"action".equalsIgnoreCase(game.getPhaseOfGame()) || player == null || !player.hasOlradinPolicies()) return;
        PlanetModel planetModel = Mapper.getPlanet(planet);
        if (planetModel == null) return;
        UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        Planet planetHolder = (Planet) unitHolder;
        if (planetHolder == null)
            return;

        boolean hasAbility = planetHolder.isLegendary();
        if (hasAbility) {
            resolveEnvironmentPreserveAbility(player, planetModel, game);
            resolveEconomyEmpowerAbility(player, game, planetModel);
            resolvePeopleConnectAbility(player, planetModel, game);
            return;
        }

        for (String type : ButtonHelper.getTypeOfPlanet(game, planet)) {
            switch (type) {
                case "hazardous" -> {
                    resolveEnvironmentPreserveAbility(player, planetModel, game);
                }
                case "industrial" -> {
                    resolveEconomyEmpowerAbility(player, game, planetModel);
                    resolveEconomyExploitAbility(player, planetModel, game);
                }
                case "cultural" -> {
                    resolvePeopleConnectAbility(player, planetModel, game);
                }
                default -> {
                    return;
                }
            }
        }

    }

    private static void resolveEconomyExploitAbility(Player player, PlanetModel planetModel, Game game) {
        if (!player.getHasUsedEconomyExploitAbility() && player.hasAbility("policy_the_economy_exploit")) { //add a fighter with ships
            player.setHasUsedEconomyExploitAbility(true);
            String msg = player.getRepresentation() + " Due to your exhausting of " + planetModel.getAutoCompleteName() + " you may resolve the following ability: **The Economy - Exploit (+)**: You may place 1 "
                + Emojis.fighter + "Fighter from your reinforcements in a system that contains 1 or more of your ships.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            List<Button> buttons = new ArrayList<>();
            buttons.addAll(Helper.getTileWithShipsPlaceUnitButtons(player, game, "ff", "placeOneNDone_skipbuild"));
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Resolve ability", buttons);
        }
    }

    private static void resolvePeopleConnectAbility(Player player, PlanetModel planetModel, Game game) {
        UnitHolder uh = ButtonHelper.getUnitHolderFromPlanetName(planetModel.getId(), game);

        if (!player.getHasUsedPeopleConnectAbility() && player.hasAbility("policy_the_people_connect") && uh != null && uh.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
            String msg = player.getRepresentation() + " Due to your exhausting of " + planetModel.getAutoCompleteName() + " you may resolve the following ability: **The People - Connect (+)**: You may move 1 "
                + Emojis.infantry + "Infantry on " + planetModel.getName() + " to another planet you control.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            List<Button> buttons = ButtonHelperAbilities.offerOlradinConnectButtons(player, game, planetModel.getId());
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Resolve ability", buttons);
        }
    }

    private static void resolveEconomyEmpowerAbility(Player player, Game game, PlanetModel planetModel) {
        if (!player.getHasUsedEconomyEmpowerAbility() && player.hasAbility("policy_the_economy_empower")) {
            player.setHasUsedEconomyEmpowerAbility(true);
            String msg = player.getRepresentation() + " Due to your exhausting of " + planetModel.getAutoCompleteName() + " you may resolve the following ability: **The Economy - Empower (+)**: You gain 1 " + Emojis.comm + "commodity.\n";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            Button getCommButton = Buttons.blue("gain_1_comms", "Gain 1 Commodity")
                .withEmoji(Emoji.fromFormatted(Emojis.comm));
            MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(), "Resolve ability", getCommButton);
        }
    }

    private static void resolveEnvironmentPreserveAbility(Player player, PlanetModel planetModel, Game game) {
        if (!player.getHasUsedEnvironmentPreserveAbility() && player.hasAbility("policy_the_environment_preserve")) {
            List<Button> buttons = ButtonHelperAbilities.getOlradinPreserveButtons(game, player, planetModel.getId());
            if (buttons.size() > 0) {
                String msg = player.getRepresentation() + " Due to your exhausting of " + planetModel.getAutoCompleteName()
                    + " you may resolve the following ability: **The Environment - Preserve (+)**: You may reveal the top card of the planets types exploration deck; if it is a relic fragment, gain it, otherwise discard that card.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Resolve ability", buttons);
            }
        }
    }
}
