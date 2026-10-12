package ti4.helpers;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
public class BlueReverieHelper {
    public static void checkXinHarmony(Game game) {
        for (Player player : game.getRealPlayers()) {
            checkXinHarmony(game, player);
        }
    }

    public static void checkXinHarmony(Game game, Player player) {
        if (!player.hasAbility("harmony") || player.getStarbalanceCounter() == player.getSteelbalanceCounter()) {
            return;
        }

        String suffix = player.getStarbalanceCounter() > player.getSteelbalanceCounter() ? "star" : "steel";
        replaceHarmonyUnit(player, suffix);
        replaceHarmonyTech(player, "dsxing", suffix);
        replaceHarmonyTech(player, "dsxiny", suffix);
    }

    private static void replaceHarmonyUnit(Player player, String suffix) {
        String desired = "xin_mech" + suffix;
        List<String> variants = List.of("xin_mech", "xin_mechsteel", "xin_mechstar");

        if (player.ownsUnit(desired) || variants.stream().noneMatch(player::ownsUnit)) {
            return;
        }

        variants.stream().filter(player::ownsUnit).forEach(player::removeOwnedUnitByID);
        player.addOwnedUnitByID(desired);
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentation() + " flipped _Sentinel_ due to **Harmony**.",
                Mapper.getUnit(desired).getRepresentationEmbed());
    }

    private static void replaceHarmonyTech(Player player, String baseTech, String suffix) {
        List<String> variants = List.of(baseTech, baseTech + "steel", baseTech + "star");
        String currentTech =
                variants.stream().filter(player::hasTech).findFirst().orElse(null);

        if (currentTech == null) {
            return;
        }

        String desired = baseTech + suffix;
        if (desired.equals(currentTech)) {
            return;
        }

        boolean exhausted = player.getExhaustedTechs().contains(currentTech);
        variants.forEach(player::removeTech);
        player.getExhaustedTechs().removeAll(variants);
        player.addTech(desired);

        if (exhausted) {
            player.getExhaustedTechs().add(desired);
        }
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentation() + " flipped _"
                        + Mapper.getTech(desired).getName() + "_ due to **Harmony**.",
                Mapper.getTech(desired).getRepresentationEmbed());
    }

    public static boolean hasXinCommanderUnlock(Player player, Game game) {
        List<Planet> occupiedPlanets = game.getTileMap().values().stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .filter(planet -> FoWHelper.playerHasUnitsOnPlanet(player, planet))
                .toList();

        boolean hasLegendaryPlanet = occupiedPlanets.stream().anyMatch(Planet::isLegendary);

        boolean hasThreeDifferentTraits = occupiedPlanets.stream()
                .filter(planet -> planet.getPlanetTypes().contains("cultural"))
                .anyMatch(cultural -> occupiedPlanets.stream()
                        .filter(planet ->
                                planet != cultural && planet.getPlanetTypes().contains("industrial"))
                        .anyMatch(industrial -> occupiedPlanets.stream()
                                .anyMatch(planet -> planet != cultural
                                        && planet != industrial
                                        && planet.getPlanetTypes().contains("hazardous"))));

        return hasLegendaryPlanet && hasThreeDifferentTraits;
    }

    public static void offerShameixBanePrompts(Game game, Player activatingPlayer, Tile tile) {
        for (Player toldarPlayer : game.getRealPlayers()) {
            if (toldarPlayer == activatingPlayer
                    || !FoWHelper.playerHasUnitsInSystem(toldarPlayer, tile)
                    || !requiresPromissoryForShameixBane(toldarPlayer, activatingPlayer)) {
                continue;
            }
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(
                        toldarPlayer.getCorrectChannel(),
                        toldarPlayer.getRepresentation() + ", _Shameix's Bane_ has been triggered.");
            }
            List<Button> promissoryButtons = ButtonHelper.getForcedPNSendButtons(game, toldarPlayer, activatingPlayer);
            if (promissoryButtons.isEmpty()) {
                MessageHelper.sendMessageToChannel(
                        activatingPlayer.getCorrectChannel(),
                        activatingPlayer.getRepresentation()
                                + " triggered _Shameix's Bane_ but has no eligible promissory note to send.");
                continue;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    activatingPlayer.getCorrectChannel(),
                    activatingPlayer.getRepresentationUnfogged()
                            + ", you triggered _Shameix's Bane_. Choose 1 promissory note to send.",
                    promissoryButtons);
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(
                        activatingPlayer.getCorrectChannel(),
                        activatingPlayer.getRepresentation()
                                + ", you owe a promissory note to the player with units here.");
            } else {
                MessageHelper.sendMessageToChannel(
                        activatingPlayer.getCorrectChannel(),
                        activatingPlayer.getRepresentation() + ", you owe a promissory note to "
                                + toldarPlayer.getRepresentation() + " from triggering _Shameix's Bane_.");
            }
        }
    }

    private static boolean requiresPromissoryForShameixBane(Player toldarPlayer, Player activatingPlayer) {
        return (toldarPlayer.hasUnlockedBreakthrough("toldarbthonor")
                        && activatingPlayer.getTotalVictoryPoints() > toldarPlayer.getTotalVictoryPoints())
                || (toldarPlayer.hasUnlockedBreakthrough("toldarbtdishonor")
                        && activatingPlayer.getTotalVictoryPoints() < toldarPlayer.getTotalVictoryPoints());
    }
}
