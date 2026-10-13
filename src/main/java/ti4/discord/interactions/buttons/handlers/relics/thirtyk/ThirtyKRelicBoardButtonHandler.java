package ti4.discord.interactions.buttons.handlers.relics.thirtyk;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thirtyk.ThirtyKRelicHooks;
import ti4.helpers.thirtyk.ThirtyKRelics;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
class ThirtyKRelicBoardButtonHandler {

    private static final int MAX_CHOICES = 24;

    @ButtonHandler("thirtyKChronoOwner_")
    public static void chronoOwner(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player owner = game.getPlayerFromColorOrFaction(buttonID.replace("thirtyKChronoOwner_", ""));
        if (owner == null) return;
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : ThirtyKRelics.infantryPlanets(game, owner, ThirtyKRelics.visiblePositions(game, player))) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + "thirtyKChronoPlanet_" + owner.getFaction() + "_"
                            + planet.getName(),
                    Helper.getPlanetRepresentation(planet.getName(), game)));
            if (buttons.size() == MAX_CHOICES) break;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", choose the planet to remove 1 infantry from.",
                ThirtyKRelicButtonHandler.withCancel(buttons));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thirtyKChronoPlanet_")
    public static void chronoPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace("thirtyKChronoPlanet_", "").split("_", 2);
        if (parts.length != 2) return;
        Player owner = game.getPlayerFromColorOrFaction(parts[0]);
        String planetName = parts[1];
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = game.getPlanetsInfo().get(planetName);
        if (owner == null || tile == null || planet == null || planet.getUnitCount(UnitType.Infantry, owner) < 1) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There is no such infantry to remove.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        RemoveUnitService.removeUnits(event, tile, game, owner.getColor(), "1 inf " + planetName);
        ThirtyKRelicHooks.rememberChronoStasis(game, owner, planetName);
        String planetRep = Helper.getPlanetRepresentation(planetName, game);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " removed 1 infantry of " + owner.getColorIfCanSeeStats(player)
                        + " from " + planetRep + " with _Chrono Stasis_.");
        if (owner != player) {
            String actor =
                    FoWHelper.identityOrColorIfCanSeeStats(game, player, owner, player.getRepresentationNoPing());
            MessageHelper.sendMessageToChannel(
                    owner.getCardsInfoThread(),
                    owner.getRepresentationUnfogged() + ", " + actor + " removed 1 of your infantry from " + planetRep
                            + " with _Chrono Stasis_. At the start of your next turn you may place it back.");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thirtyKChronoReturn_")
    public static void chronoReturn(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.replace("thirtyKChronoReturn_", "");
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = game.getPlanetsInfo().get(planetName);
        if (tile == null || planet == null || ThirtyKRelicHooks.blockedForChronoReturn(game, player, planet)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "The infantry cannot be placed back on that planet any more.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 inf " + planetName);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed 1 infantry back on "
                        + Helper.getPlanetRepresentation(planetName, game) + " after _Chrono Stasis_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thirtyKDeepNet_")
    public static void deepNetworks(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String choice = buttonID.replace("thirtyKDeepNet_", "");
        if (choice.startsWith("page")) {
            int page = Integer.parseInt(choice.replace("page", ""));
            MessageHelper.editMessageButtons(
                    event,
                    ThirtyKRelicButtonHandler.withCancel(ThirtyKRelics.page(
                            ThirtyKRelics.deepNetworkButtons(game, player),
                            player.factionButtonChecker() + "thirtyKDeepNet_",
                            page)));
            return;
        }
        boolean eligible = ThirtyKRelics.deepNetworkPlanets(game, player).stream()
                .anyMatch(planet -> planet.getName().equals(choice));
        Tile tile = game.getTileFromPlanet(choice);
        if (!eligible || tile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "That planet is no longer eligible.");
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 inf " + choice);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed 1 infantry on "
                        + Helper.getPlanetRepresentation(choice, game) + " with _Deep Networks_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thirtyKOverpop_")
    public static void overpopulation(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace("thirtyKOverpop_", "").split("_", 2);
        if (parts.length != 2) return;
        String planetName = parts[1];
        Tile tile = game.getTileFromPlanet(planetName);
        if (tile == null) return;
        String planetRep = Helper.getPlanetRepresentation(planetName, game);
        if ("add".equals(parts[0])) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "4 inf " + planetName);
            List<Button> pay = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, "res"));
            pay.add(Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets"));
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged() + " added 4 infantry to " + planetRep
                            + " with _Overpopulation Management_. Please spend 2 resources.",
                    pay);
        } else {
            RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "1 inf " + planetName);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " removed 1 infantry from " + planetRep
                            + " due to _Overpopulation Management_.");
        }
        ButtonHelper.deleteButtonsWithPartialID(event, "_" + planetName);
    }

    @ButtonHandler("thirtyKSocialGive_")
    public static void socialGive(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player holder = game.getPlayerFromColorOrFaction(buttonID.replace("thirtyKSocialGive_", ""));
        if (holder == null || !holder.hasRelic(ThirtyKRelics.SOCIAL_DISSONANCE)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<String> tokens = ThirtyKRelicHooks.socialDissonanceTokens(game);
        if (tokens.contains(player.getFaction())) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "You already have an ownership token on _Social Dissonance_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        tokens.add(player.getFaction());
        ThirtyKRelicHooks.setSocialDissonanceTokens(game, tokens);
        String giver = FoWHelper.identityOrColorIfCanSeeStats(game, player, holder, player.getRepresentationNoPing());
        MessageHelper.sendMessageToChannel(
                holder.getCardsInfoThread(),
                holder.getRepresentationUnfogged() + ", " + giver
                        + " placed an ownership token on _Social Dissonance_ (" + tokens.size() + " tokens).");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed an ownership token on _Social Dissonance_.");
        if (ThirtyKRelicHooks.socialDissonanceOverCapacity(tokens)) {
            offerTokenRemoval(game, player, tokens);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static void offerTokenRemoval(Game game, Player player, List<String> tokens) {
        List<Button> buttons = new ArrayList<>();
        for (String faction : new LinkedHashSet<>(tokens)) {
            Player owner = game.getPlayerFromColorOrFaction(faction);
            if (owner != null) {
                buttons.add(FoWHelper.fogSafeTargetButton(
                        player.factionButtonChecker() + "thirtyKSocialRemove_" + faction, "red", owner, player));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", _Social Dissonance_ now has more than 3 ownership tokens. Remove 1 token.",
                buttons);
    }

    @ButtonHandler("thirtyKSocialRemove_")
    public static void socialRemove(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String faction = buttonID.replace("thirtyKSocialRemove_", "");
        List<String> tokens = ThirtyKRelicHooks.socialDissonanceTokens(game);
        if (tokens.remove(faction)) {
            ThirtyKRelicHooks.setSocialDissonanceTokens(game, tokens);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " removed an ownership token from _Social Dissonance_ ("
                            + tokens.size() + " left).");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thirtyKDefiant_")
    public static void defiantPropaganda(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.replace("thirtyKDefiant_", "");
        Planet planet = game.getPlanetsInfo().get(planetName);
        if (planet == null || !player.hasRelicReady(ThirtyKRelics.DEFIANT_PROPAGANDA)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Player owner : ThirtyKRelicHooks.infantryOwners(game, planet)) {
            int most = Math.min(2, planet.getUnitCount(UnitType.Infantry, owner));
            for (int amount = 1; amount <= most; amount++) {
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + "thirtyKDefiantKill_" + amount + "_" + owner.getFaction() + "_"
                                + planetName,
                        "Destroy " + amount + " " + owner.getColorIfCanSeeStats(player) + " Infantry"));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", choose the infantry _Defiant Propaganda_ destroys on "
                        + Helper.getPlanetRepresentation(planetName, game) + ".",
                ThirtyKRelicButtonHandler.withCancel(buttons));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thirtyKDefiantKill_")
    public static void defiantKill(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace("thirtyKDefiantKill_", "").split("_", 3);
        if (parts.length != 3 || !player.hasRelicReady(ThirtyKRelics.DEFIANT_PROPAGANDA)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Player owner = game.getPlayerFromColorOrFaction(parts[1]);
        String planetName = parts[2];
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = game.getPlanetsInfo().get(planetName);
        if (owner == null || tile == null || planet == null) return;
        int amount = Math.min(Integer.parseInt(parts[0]), planet.getUnitCount(UnitType.Infantry, owner));
        if (amount < 1) return;
        ThirtyKRelics.exhaust(game, player, event, ThirtyKRelics.DEFIANT_PROPAGANDA);
        DestroyUnitService.destroyUnits(event, tile, game, owner.getColor(), amount + " inf " + planetName, false);
        String result = player.getRepresentationNoPing() + " destroyed " + amount + " infantry on "
                + Helper.getPlanetRepresentation(planetName, game) + " with _Defiant Propaganda_";
        UnitHolder home = ThirtyKRelics.firstHomePlanet(game, player);
        Tile homeTile = player.getHomeSystemTile();
        if (home != null && homeTile != null) {
            AddUnitService.addUnits(event, homeTile, game, player.getColor(), amount + " inf " + home.getName());
            result += " and placed " + amount + " infantry on " + Helper.getPlanetRepresentation(home.getName(), game);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), result + ".");
        if (owner != player) {
            String actor =
                    FoWHelper.identityOrColorIfCanSeeStats(game, player, owner, player.getRepresentationNoPing());
            MessageHelper.sendMessageToChannel(
                    owner.getCardsInfoThread(),
                    owner.getRepresentationUnfogged() + ", " + actor + " destroyed " + amount + " of your infantry on "
                            + Helper.getPlanetRepresentation(planetName, game) + " with _Defiant Propaganda_.");
        }
        ButtonHelper.deleteMessage(event);
    }
}
