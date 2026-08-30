package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

/**
 * _Arbitration_ places 1 infantry from another player's reinforcements into coexistence on a non-home planet.
 *
 * <p>The card constrains <em>whose infantry</em>, not <em>whose planet</em>, which is what drives the pick order:
 * the infantry player is chosen first, and every step after that filters against them. These cases pin the
 * reasoning behind each of those filters, since the production code carries no comments explaining itself.
 *
 * <p>The fixture is the worked example from the bug report: Yssaril plays the card, and Sol or Argent supplies
 * the infantry. Tile 37 (Arinam/Meer) is a plain non-home system; tile 15 (Retillion/Shalloq) is Yssaril's home
 * system, reached through the faction's tile alias.
 */
class ArbitrationAcd2ButtonHandlerTest extends BaseTi4Test {

    private static final String NON_HOME_TILE = "37";
    private static final String YSSARIL_HOME_TILE = "15";

    private Game game;
    private Player yssaril;
    private Player sol;
    private Player argent;

    @BeforeEach
    void setUpThreePlayerGame() {
        game = new Game();
        game.setName("arbitration-test");
        yssaril = addPlayer("yssaril-id", "yssaril", "green");
        sol = addPlayer("sol-id", "sol", "blue");
        argent = addPlayer("argent-id", "argent", "red");
        game.setTile(new Tile(NON_HOME_TILE, "101"));
        game.setTile(new Tile(YSSARIL_HOME_TILE, "102"));
    }

    @Test
    void ownerButtons_includeThePlayerPlayingTheCard() {
        // The whole point of the bug report: Yssaril plays Arbitration, picks Sol as the infantry player, and
        // must still be offered their own holdings - the card never says whose planet it has to be.
        giveControlWithInfantry(yssaril, "meer");

        List<Button> ownerButtons = ArbitrationAcd2ButtonHandler.getPlanetOwnerButtons(game, yssaril, sol);

        assertThat(customIds(ownerButtons)).anyMatch(id -> id.endsWith("arbitrationOwner_sol_yssaril"));
    }

    @Test
    void ownerButtons_excludeTheInfantryPlayer() {
        // Placing Sol's infantry onto a planet Sol already controls is reinforcing, not coexisting, so Sol must
        // not appear as a category once they have been picked as the infantry player.
        giveControlWithInfantry(sol, "meer");
        giveControlWithInfantry(yssaril, "arinam");

        List<Button> ownerButtons = ArbitrationAcd2ButtonHandler.getPlanetOwnerButtons(game, yssaril, sol);

        assertThat(customIds(ownerButtons)).noneMatch(id -> id.endsWith("arbitrationOwner_sol_sol"));
        assertThat(customIds(ownerButtons)).anyMatch(id -> id.endsWith("arbitrationOwner_sol_yssaril"));
    }

    @Test
    void planetButtons_offerTheControllersPlanetsInThatCategory() {
        giveControlWithInfantry(yssaril, "meer");
        giveControlWithInfantry(argent, "arinam");

        List<Button> yssarilPlanets = ArbitrationAcd2ButtonHandler.getPlanetButtons(game, yssaril, sol, "yssaril");

        assertThat(customIds(yssarilPlanets)).anyMatch(id -> id.endsWith("arbitrationPlace_sol_meer"));
        assertThat(customIds(yssarilPlanets)).noneMatch(id -> id.endsWith("arbitrationPlace_sol_arinam"));
    }

    @Test
    void coexistenceTarget_requiresUnitsBelongingToSomebodyElse() {
        // "Into coexistence" needs somebody already standing on the planet. A controlled but empty planet would
        // let the infantry player simply take it, which is a placement the card does not grant.
        yssaril.addPlanet("meer");

        assertThat(ArbitrationAcd2ButtonHandler.isCoexistenceTarget(game, planet("meer"), sol))
                .as("a planet with a control token but no units is not a coexistence target")
                .isFalse();

        addInfantry(yssaril, "meer");

        assertThat(ArbitrationAcd2ButtonHandler.isCoexistenceTarget(game, planet("meer"), sol))
                .isTrue();
    }

    @Test
    void coexistenceTarget_ignoresTheInfantryPlayersOwnGarrison() {
        // Sol's own infantry cannot be the thing Sol coexists with, so a planet holding nothing but Sol's units
        // is not a target even though it is occupied.
        argent.addPlanet("meer");
        addInfantry(sol, "meer");

        assertThat(ArbitrationAcd2ButtonHandler.isCoexistenceTarget(game, planet("meer"), sol))
                .isFalse();

        addInfantry(argent, "meer");

        assertThat(ArbitrationAcd2ButtonHandler.isCoexistenceTarget(game, planet("meer"), sol))
                .as("Argent's infantry gives Sol somebody to coexist with")
                .isTrue();
    }

    @Test
    void coexistenceTarget_excludesHomePlanets() {
        // The card says "non-home planet", and Retillion sits in Yssaril's home system.
        giveControlWithInfantry(yssaril, "retillion");

        assertThat(ArbitrationAcd2ButtonHandler.isCoexistenceTarget(game, planet("retillion"), sol))
                .isFalse();
    }

    @Test
    void planetButtons_bucketsAreDisjoint() {
        // A controlled planet belongs to its controller's bucket only. It must not also surface under the
        // unowned bucket, or the same planet would be offered twice by two different owner buttons.
        giveControlWithInfantry(yssaril, "meer");

        List<Button> unowned = ArbitrationAcd2ButtonHandler.getPlanetButtons(
                game, yssaril, sol, ArbitrationAcd2ButtonHandler.UNOWNED_PLANETS_KEY);

        assertThat(customIds(unowned)).noneMatch(id -> id.endsWith("arbitrationPlace_sol_meer"));
    }

    @Test
    void planetButtons_unownedBucketHoldsUncontrolledPlanetsWithUnits() {
        // Units can sit on a planet nobody controls. Those are still somebody to coexist with, so they belong
        // in a bucket of their own rather than falling out of the flow entirely.
        addInfantry(argent, "meer");

        List<Button> unowned = ArbitrationAcd2ButtonHandler.getPlanetButtons(
                game, yssaril, sol, ArbitrationAcd2ButtonHandler.UNOWNED_PLANETS_KEY);

        assertThat(customIds(unowned)).anyMatch(id -> id.endsWith("arbitrationPlace_sol_meer"));
    }

    @Test
    void infantryPlayerButtons_excludeThePlayerPlayingTheCard() {
        // "From another player's reinforcements" - Yssaril can never supply the infantry themselves.
        giveControlWithInfantry(yssaril, "meer");

        List<Button> infantryPlayers = ArbitrationAcd2ButtonHandler.getInfantryPlayerButtons(game, yssaril);

        assertThat(customIds(infantryPlayers)).noneMatch(id -> id.endsWith("arbitrationInfantry_yssaril"));
        assertThat(customIds(infantryPlayers))
                .anyMatch(id -> id.endsWith("arbitrationInfantry_sol"))
                .anyMatch(id -> id.endsWith("arbitrationInfantry_argent"));
    }

    @Test
    void infantryPlayerButtons_dropPlayersWithNowhereToPlace() {
        // Argent controls the only occupied planet, so Argent's own infantry has nowhere to go: every candidate
        // is either their planet or empty. Offering them would dead-end the flow one step later.
        giveControlWithInfantry(argent, "meer");

        List<Button> infantryPlayers = ArbitrationAcd2ButtonHandler.getInfantryPlayerButtons(game, yssaril);

        assertThat(customIds(infantryPlayers)).noneMatch(id -> id.endsWith("arbitrationInfantry_argent"));
        assertThat(customIds(infantryPlayers)).anyMatch(id -> id.endsWith("arbitrationInfantry_sol"));
    }

    @Test
    void buildingButtons_doesNotCreateTheNeutralPlayer() {
        // The neutral check deliberately avoids Game#getNeutral, which sets a neutral player up as a side
        // effect. Building a button list must not mutate the game.
        giveControlWithInfantry(yssaril, "meer");

        ArbitrationAcd2ButtonHandler.getInfantryPlayerButtons(game, yssaril);

        assertThat(game.getPlayerFromColorOrFaction("neutral"))
                .as("listing buttons must not conjure a neutral player into the game")
                .isNull();
    }

    private Player addPlayer(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(faction);
        player.setColor(color);
        player.addOwnedUnitByID("infantry");
        return player;
    }

    private void giveControlWithInfantry(Player player, String planetName) {
        player.addPlanet(planetName);
        addInfantry(player, planetName);
    }

    private void addInfantry(Player player, String planetName) {
        game.getTileFromPlanet(planetName)
                .addUnit(planetName, Units.getUnitKey(UnitType.Infantry, player.getColor()), 1);
    }

    private Planet planet(String planetName) {
        return game.getUnitHolderFromPlanet(planetName);
    }

    private List<String> customIds(List<Button> buttons) {
        return buttons.stream()
                .map(Button::getCustomId)
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
