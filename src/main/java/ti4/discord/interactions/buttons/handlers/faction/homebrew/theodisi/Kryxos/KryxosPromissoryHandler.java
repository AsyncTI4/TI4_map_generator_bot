package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Kryxos;

import java.util.ArrayList;
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
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class KryxosPromissoryHandler {
    private static final String EVOEDICT = "thpnkryxos";
    private static final String USE_EVOEDICT = "useEvolutionaryEdict_";
    private static final String PLACE_EVOEDICT = "placeEvolutionaryEdict_";

    public static void getEvolutionaryEdictButton(Player player, TechnologyModel techM) {
        if (player == null
                || techM == null
                || !techM.isUnitUpgrade()
                || player.ownsPromissoryNote(EVOEDICT)
                || !player.hasPlayablePromissoryInHand(EVOEDICT)) {
            return;
        }

        int prereqs = techM == null || techM.getRequirements().isEmpty()
                ? 0
                : techM.getRequirements().get().length();

        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + USE_EVOEDICT + techM.getAlias(),
                        "Use Evolutionary Edict",
                        FactionEmojis.kryxos),
                Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation()
                        + ", you may play _Evolutionary Edict_ to produce a unit in your home system matching the Unit Upgrade you just researched with its cost reduced by "
                        + prereqs + ".",
                buttons);
    }

    @ButtonHandler(USE_EVOEDICT)
    public static void startEvolutionaryEdict(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasPlayablePromissoryInHand(EVOEDICT)) {
            return;
        }

        String techID = buttonID.replace(USE_EVOEDICT, "");

        TechnologyModel tech = Mapper.getTech(techID);
        if (tech == null || !tech.isUnitUpgrade() || !player.hasTech(techID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile homeSystem = player.getHomeSystemTile();
        if (homeSystem == null) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Could not find Home System.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        UnitModel unit = Mapper.getUnitModelByTechUpgrade(techID);
        if (unit == null || Helper.getProductionValue(player, game, homeSystem, false) < 1) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Could not get unit.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteMessage(event);
        if (unit.getIsShip()) {
            produceEvolutionaryEdictUnit(event, game, player, tech, unit, homeSystem, "space");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Planet planet : homeSystem.getPlanetUnitHolders()) {
            if (!player.getPlanets().contains(planet.getName())) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PLACE_EVOEDICT + techID + "|" + planet.getName(),
                    "Produce 1 " + unit.getBaseType() + " on " + Helper.getPlanetRepresentation(planet.getName(), game),
                    FactionEmojis.kryxos));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged()
                            + ", you do not control a planet in your home system to produce that unit on.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", choose the home-system planet on which to produce your "
                        + unit.getBaseType() + ".",
                buttons);
    }

    @ButtonHandler(PLACE_EVOEDICT)
    public static void placeEvolutionaryEdictUnit(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace(PLACE_EVOEDICT, "").split("\\|", 2);
        if (game == null || player == null || parts.length != 2 || !player.hasPlayablePromissoryInHand(EVOEDICT)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        TechnologyModel tech = Mapper.getTech(parts[0]);
        UnitModel unit = Mapper.getUnitModelByTechUpgrade(parts[0]);
        Tile homeSystem = player.getHomeSystemTile();
        if (tech == null
                || unit == null
                || !tech.isUnitUpgrade()
                || !player.hasTech(parts[0])
                || homeSystem == null
                || !player.getPlanets().contains(parts[1])
                || homeSystem.getUnitHolderFromPlanet(parts[1]) == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteMessage(event);
        produceEvolutionaryEdictUnit(event, game, player, tech, unit, homeSystem, parts[1]);
    }

    private static void produceEvolutionaryEdictUnit(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            TechnologyModel tech,
            UnitModel unit,
            Tile homeSystem,
            String holder) {
        int discount = tech.getRequirements().orElse("").length();
        int cost = Math.max(0, (int) Math.ceil(unit.getCost()) - discount);
        String unitText = "1 " + unit.getAsyncId() + ("space".equals(holder) ? "" : " " + holder);
        AddUnitService.addUnits(event, homeSystem, game, player.getColor(), unitText);

        game.setStoredValue("producedUnitCostFor" + player.getFaction(), Integer.toString(cost));
        player.setTotalExpenses(player.getTotalExpenses() + cost);
        returnEvolutionaryEdict(game, player);

        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.getRepresentation() + " produced 1 " + unit.getBaseType() + " in their home system for " + cost
                        + " resources after the " + discount + "-resource _Evolutionary Edict_ discount.");
        List<Button> paymentButtons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, "res"));
        paymentButtons.add(Buttons.red("deleteButtons_evolutionaryEdict", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", please choose the planets you wish to exhaust to pay a cost of "
                        + cost + ".",
                paymentButtons);
    }

    private static void returnEvolutionaryEdict(Game game, Player player) {
        Player owner = game.getPNOwner(EVOEDICT);
        if (owner == null) {
            return;
        }
        player.removePromissoryNote(EVOEDICT);
        owner.setPromissoryNote(EVOEDICT);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, owner, false);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                "_Evolutionary Edict_ has been returned to " + owner.getRepresentationNoPing() + ".");
    }
}
