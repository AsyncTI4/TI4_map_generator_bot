package ti4.discord.interactions.buttons.handlers.faction.homebrew.tfbr;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.RelicHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.game.MonumentsService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class WhiteTfUnitHandler {
    private static final String FLAGSHIP = "whitetf_flagship";
    private static final String MECH = "whitetf_mech";
    private static final String MONUMENT = "whitetf_monument";
    private static final String USE_FLAGSHIP = "useWhitetfFlagship_";
    private static final String SELECT_FLAGSHIP_ABILITY = "selectWhitetfFlagshipAbility_";
    private static final String SELECT_OPPONENT_ABILITY = "selectWhitetfOpponentAbility_";
    private static final String USE_MECH = "useWhitetfMech_";

    public static void addFlagshipButton(List<Button> buttons, Game game, Player player, Player opponent, Tile tile) {
        if (!game.isTwilightsFallMode()
                || !player.ownsUnit(FLAGSHIP)
                || !ButtonHelper.doesPlayerHaveFSHere(FLAGSHIP, player, tile)) {
            return;
        }
        buttons.add(Buttons.green(
                player.factionButtonChecker() + USE_FLAGSHIP + opponent.getFaction() + "|" + tile.getPosition(),
                "Use Avarice Iudex (After Win)"));
    }

    @ButtonHandler(USE_FLAGSHIP)
    public static void useFlagship(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.replace(USE_FLAGSHIP, "").split("\\|", 2);
        Player opponent = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[1]) : null;
        if (opponent == null
                || tile == null
                || !game.isTwilightsFallMode()
                || !player.ownsUnit(FLAGSHIP)
                || !ButtonHelper.doesPlayerHaveFSHere(FLAGSHIP, player, tile)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        List<Button> buttons =
                getTfAbilityButtons(player, player, SELECT_FLAGSHIP_ABILITY + opponent.getFaction() + "|");
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You have no abilities to give.");
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose the ability to give to "
                        + opponent.getRepresentationNoPing() + ".",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SELECT_FLAGSHIP_ABILITY)
    public static void selectFlagshipAbility(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.replace(SELECT_FLAGSHIP_ABILITY, "").split("\\|", 2);
        Player opponent = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        String ability = payload.length == 2 ? payload[1] : null;
        if (opponent == null || !isTfAbility(player, ability)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        List<Button> buttons = getTfAbilityButtons(
                opponent, player, SELECT_OPPONENT_ABILITY + opponent.getFaction() + "|" + ability + "|");
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, opponent.getRepresentationNoPing() + " has no abilities to take.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose the ability to take from "
                        + opponent.getRepresentationNoPing() + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_OPPONENT_ABILITY)
    public static void selectOpponentAbility(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.replace(SELECT_OPPONENT_ABILITY, "").split("\\|", 3);
        Player opponent = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        String givenAbility = payload.length == 3 ? payload[1] : null;
        String takenAbility = payload.length == 3 ? payload[2] : null;
        if (opponent == null || !isTfAbility(player, givenAbility) || !isTfAbility(opponent, takenAbility)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        player.removeTech(givenAbility);
        opponent.removeTech(takenAbility);
        player.addTech(takenAbility);
        opponent.addTech(givenAbility);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " gave _"
                        + Mapper.getTech(givenAbility).getName() + "_ to "
                        + opponent.getRepresentationNoPing() + " and took _"
                        + Mapper.getTech(takenAbility).getName()
                        + "_ with the Avarice Iudex (the Goldos flagship).");
        offerMechRemoval(event, game, player, takenAbility);
        offerMechRemoval(event, game, opponent, givenAbility);
        ButtonHelper.deleteMessage(event);
    }

    public static void offerMechRemoval(GenericInteractionCreateEvent event, Game game, Player player, String ability) {
        if (!game.isTwilightsFallMode() || !isTfAbility(player, ability) || !player.ownsUnit(MECH)) {
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                for (UnitKey unitKey : holder.getUnitKeysForPlayer(player)) {
                    UnitModel unit = player.getUnitFromUnitKey(unitKey);
                    if (unit == null || !MECH.equals(unit.getId())) {
                        continue;
                    }
                    for (UnitState state : holder.getNonZeroUnitStates(unitKey)) {
                        buttons.add(Buttons.red(
                                player.factionButtonChecker() + USE_MECH + ability + "|" + tile.getPosition() + "|"
                                        + holder.getName() + "|" + state,
                                "Remove Crystal Sentinel from " + holder.getRepresentation(game)));
                    }
                }
            }
        }
        if (buttons.isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", you may remove a Crystal Sentinel and discard _"
                        + Mapper.getTech(ability).getName() + "_ to gain 1 relic.",
                buttons);
    }

    @ButtonHandler(USE_MECH)
    public static void useMech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.replace(USE_MECH, "").split("\\|", 4);
        Tile tile = payload.length == 4 ? game.getTileByPosition(payload[1]) : null;
        UnitHolder holder = tile == null || payload.length != 4
                ? null
                : tile.getUnitHolders().get(payload[2]);
        UnitState state = payload.length == 4 ? Units.findUnitState(payload[3]) : null;
        UnitKey mech = holder == null
                ? null
                : holder.getUnitKeysForPlayer(player).stream()
                        .filter(unitKey -> {
                            UnitModel unit = player.getUnitFromUnitKey(unitKey);
                            return unit != null && MECH.equals(unit.getId());
                        })
                        .findFirst()
                        .orElse(null);
        if (!game.isTwilightsFallMode()
                || !isTfAbility(player, payload.length == 4 ? payload[0] : null)
                || tile == null
                || holder == null
                || state == null
                || mech == null
                || holder.getUnitCountForState(mech, state) < 1) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        RemoveUnitService.removeUnit(event, tile, game, new ParsedUnit(mech, 1, holder.getName()), state);
        player.removeTech(payload[0]);
        RelicHelper.drawRelicAndNotify(player, event, game);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " removed a Crystal Sentinel and discarded _"
                        + Mapper.getTech(payload[0]).getName() + "_ to gain a relic.");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    public static void resolveMonumentRelicDraw(GenericInteractionCreateEvent event, Game game, Player player) {
        if (!game.isMonumentsMode()
                || !game.isTwilightsFallMode()
                || !MonumentsService.isMonumentOnBoard(game, player, MONUMENT)) {
            return;
        }
        Tile tile = MonumentsService.getMonumentTile(game, player, MONUMENT);
        UnitHolder holder = tile == null
                ? null
                : tile.getPlanetUnitHolders().stream()
                        .filter(planet -> planet.getUnitCount(UnitType.Monument, player) > 0)
                        .findFirst()
                        .orElse(null);
        if (tile == null || holder == null) {
            return;
        }
        player.gainTG(1, true);
        List<Button> buttons = List.of(Buttons.green(
                player.factionButtonChecker() + "produceOneUnitInTile_" + tile.getPosition() + "_whitetfMonument",
                "Produce 1 Unit"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " gained 1 trade good and may produce 1 unit on "
                        + holder.getRepresentation(game) + " with _The Alabaster Edifice_.",
                buttons);
    }

    private static List<Button> getTfAbilityButtons(Player abilityOwner, Player buttonOwner, String prefix) {
        List<Button> buttons = new ArrayList<>();
        for (String techID : abilityOwner.getTechs()) {
            if (!isTfAbility(abilityOwner, techID)) {
                continue;
            }
            TechnologyModel tech = Mapper.getTech(techID);
            buttons.add(Buttons.gray(buttonOwner.factionButtonChecker() + prefix + techID, tech.getName()));
        }
        return buttons;
    }

    private static boolean isTfAbility(Player player, String techID) {
        if (player == null || techID == null || !player.hasTech(techID)) {
            return false;
        }
        TechnologyModel tech = Mapper.getTech(techID);
        return tech != null && tech.getSource().isTwilightFallish();
    }
}
