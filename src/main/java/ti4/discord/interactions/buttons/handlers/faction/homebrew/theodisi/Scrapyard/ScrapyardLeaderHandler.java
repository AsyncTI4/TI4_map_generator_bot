package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Scrapyard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.NamedCombatModifierModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParsedUnit;

@UtilityClass
public class ScrapyardLeaderHandler {
    private static final String DECLARE_RIKKA = "sendRikkaReminderMessage";
    private static final String SCRAPYARD_COMMANDER = "scrapyardcommander";
    private static final String DESTROY_HERO_UNIT = "scrapyardHeroDestroyUnit_";
    private static final String PAGE_HERO_DESTROY = "scrapyardHeroDestroyPage_";
    private static final String PLACE_HERO_WARSUN = "scrapyardHeroPlaceWarsun_";
    private static final String USE_COMMANDER = "useScrapyardCommander_";
    private static final String SELECT_COMMANDER_UNIT = "selectScrapyardCommanderUnit_";

    public static void sendRikkaButtons(Player target, Player agentOwner) {
        if (target == null || agentOwner == null || !agentOwner.hasUnexhaustedLeader("scrapyardagent")) {
            return;
        }

        List<Button> buttons = new ArrayList<>(List.of(
                Buttons.green(
                        agentOwner.factionButtonChecker() + DECLARE_RIKKA + target.getFaction(),
                        "Declare Rikka",
                        FactionEmojis.scrapyard),
                Buttons.red("deleteButtons", "Decline")));
        MessageHelper.sendMessageToChannelWithButtons(
                target == agentOwner ? target.getCorrectChannel() : agentOwner.getCardsInfoThread(),
                target.getRepresentation() + ", " + agentOwner.getRepresentationNoPing()
                        + " may exhaust _Rikka \"Razor Fang\" Corvin_ to let you swap the movement and combat values "
                        + "of 2 of your units until the end of this tactical action.",
                buttons);
    }

    @ButtonHandler(DECLARE_RIKKA)
    public static void declareRikkaUsage(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String factionName = buttonID.substring(DECLARE_RIKKA.length());
        Player target = game.getPlayerFromColorOrFaction(factionName);
        if (target == null) {
            return;
        }

        Player agentOwner = game.getPlayerFromLeader("scrapyardagent");
        if (agentOwner == null || agentOwner != player || !agentOwner.hasUnexhaustedLeader("scrapyardagent")) {
            return;
        }
        Leader agent = agentOwner.getLeader("scrapyardagent").orElse(null);
        if (agent == null) return;

        ExhaustLeaderService.exhaustLeader(game, agentOwner, agent);
        MessageHelper.sendMessageToChannel(
                target.getCorrectChannel(),
                target.getRepresentation() + " is using _Rikka \"Razor Fang\" Corvin_ to swap the movement and combat "
                        + "values of 2 of their units until the end of this tactical action. They will declare which units.");
        ButtonHelper.deleteMessage(event);
    }

    public static void addCommanderButton(
            List<Button> buttons, Game game, Player player, Tile tile, String unitHolderName) {
        if (buttons == null
                || game == null
                || player == null
                || tile == null
                || !game.playerHasLeaderUnlockedOrAlliance(player, SCRAPYARD_COMMANDER)) {
            return;
        }
        if (buttons.stream()
                .map(Button::getCustomId)
                .filter(java.util.Objects::nonNull)
                .anyMatch(id -> id.startsWith(player.factionButtonChecker() + USE_COMMANDER))) {
            return;
        }
        UnitHolder holder = tile.getUnitHolders().get(unitHolderName);
        String selected = game.getStoredValue(commanderUsedKey(player));
        if (holder == null || selected.equals(tile.getPosition() + "|" + holder.getName())) {
            return;
        }
        boolean hasEligibleUnit = holder.getUnitKeysForPlayer(player).stream()
                .map(unitKey -> player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder))
                .anyMatch(unit -> unit != null
                        && Mapper.getCombatModifiers().containsKey("scrapyard_commander_plus2_" + unit.getAsyncId()));
        if (hasEligibleUnit) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + USE_COMMANDER + tile.getPosition() + "|" + holder.getName(),
                    "Use Nix \"Stray\" Calder",
                    FactionEmojis.scrapyard));
        }
    }

    @ButtonHandler(USE_COMMANDER)
    public static void useCommander(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(USE_COMMANDER.length()).split("\\|", 2);
        Tile tile = values.length == 2 ? game.getTileByPosition(values[0]) : null;
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(values[1]);
        if (holder == null || !game.playerHasLeaderUnlockedOrAlliance(player, SCRAPYARD_COMMANDER)) {
            return;
        }
        Set<String> unitTypes = new HashSet<>();
        List<Button> buttons = new ArrayList<>();
        for (UnitKey unitKey : holder.getUnitKeysForPlayer(player)) {
            UnitModel unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
            if (unit == null
                    || !unitTypes.add(unit.getAsyncId())
                    || !Mapper.getCombatModifiers().containsKey("scrapyard_commander_plus2_" + unit.getAsyncId())) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_COMMANDER_UNIT + tile.getPosition() + "|" + holder.getName()
                            + "|" + unit.getAsyncId(),
                    unit.getName(),
                    unit.getUnitEmoji()));
        }
        if (buttons.isEmpty()) {
            return;
        }
        buttons.sort(Comparator.comparing(Button::getLabel));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose a unit type for Nix \"Stray\" Calder.",
                buttons);
    }

    @ButtonHandler(SELECT_COMMANDER_UNIT)
    public static void selectCommanderUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(SELECT_COMMANDER_UNIT.length()).split("\\|", 3);
        Tile tile = values.length == 3 ? game.getTileByPosition(values[0]) : null;
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(values[1]);
        UnitModel unit =
                holder == null ? null : player.getPriorityUnitByAsyncID(values.length == 3 ? values[2] : "", holder);
        if (unit == null
                || !game.playerHasLeaderUnlockedOrAlliance(player, SCRAPYARD_COMMANDER)
                || !Mapper.getCombatModifiers().containsKey("scrapyard_commander_plus2_" + unit.getAsyncId())) {
            return;
        }
        game.setStoredValue(
                commanderKey(player), tile.getPosition() + "|" + holder.getName() + "|" + unit.getAsyncId());
        game.setStoredValue(commanderUsedKey(player), tile.getPosition() + "|" + holder.getName());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " chose " + unit.getUnitEmoji() + " " + unit.getName()
                        + " for Nix \"Stray\" Calder. Its next combat-round roll receives +2.");
    }

    public static void addCommanderModifier(
            List<NamedCombatModifierModel> modifiers,
            Game game,
            Player player,
            Tile tile,
            UnitHolder holder,
            CombatRollType rollType) {
        if (modifiers == null
                || game == null
                || player == null
                || tile == null
                || holder == null
                || rollType != CombatRollType.combatround
                || !game.playerHasLeaderUnlockedOrAlliance(player, "scrapyardcommander")) {
            return;
        }
        String[] values = game.getStoredValue(commanderKey(player)).split("\\|", 3);
        if (values.length != 3
                || !tile.getPosition().equals(values[0])
                || !holder.getName().equals(values[1])) {
            return;
        }
        var modifier = Mapper.getCombatModifiers().get("scrapyard_commander_plus2_" + values[2]);
        if (modifier == null) {
            game.removeStoredValue(commanderKey(player));
            return;
        }
        modifiers.add(new NamedCombatModifierModel(modifier, "Nix \"Stray\" Calder, the Scrapyard commander"));
        game.removeStoredValue(commanderKey(player));
    }

    public static void clearCommanderModifiers(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(commanderKey(player));
            game.removeStoredValue(commanderUsedKey(player));
        }
    }

    public static void resolveScrapyardHero(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return;
        }
        game.setStoredValue(heroCostKey(player), "0");
        sendHeroDestroyButtons(null, game, player, 0);
    }

    @ButtonHandler(PAGE_HERO_DESTROY)
    public static void pageHeroDestroyButtons(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int page;
        try {
            page = Integer.parseInt(
                    buttonID.substring(PAGE_HERO_DESTROY.length()).replace("page", ""));
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        sendHeroDestroyButtons(event, game, player, page);
    }

    @ButtonHandler(DESTROY_HERO_UNIT)
    public static void destroyHeroUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(DESTROY_HERO_UNIT.length()).split("\\|", 4);
        Tile tile = values.length == 4 ? game.getTileByPosition(values[0]) : null;
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(values[1]);
        UnitKey unitKey = holder == null
                ? null
                : holder.getUnitKeysForPlayer(player).stream()
                        .filter(key -> key.asyncID().equals(values[2]))
                        .findFirst()
                        .orElse(null);
        UnitState state;
        try {
            state = values.length == 4 ? UnitState.valueOf(values[3]) : null;
        } catch (IllegalArgumentException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
        if (holder == null
                || unitKey == null
                || state == null
                || unit == null
                || unit.getCost() <= 0
                || holder.getUnitCountForState(unitKey, state) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        DestroyUnitService.destroyUnit(event, tile, game, new ParsedUnit(unitKey, 1, holder.getName()), false, state);
        float totalCost = Float.parseFloat(game.getStoredValue(heroCostKey(player))) + unit.getCost();
        game.setStoredValue(heroCostKey(player), Float.toString(totalCost));
        if (totalCost < 12) {
            sendHeroDestroyButtons(event, game, player, 0);
            return;
        }

        UnitModel warSun = player.getPriorityUnitByAsyncID("ws", null);
        String warSunTech = warSun == null ? "ws" : warSun.getRequiredTechId().orElse("ws");
        player.addTech(warSunTech);
        game.removeStoredValue(heroCostKey(player));
        game.setStoredValue(heroWarsunKey(player), "2");
        ButtonHelper.deleteMessage(event);
        sendHeroWarsunButtons(event, game, player);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " destroyed units with a combined cost of " + formatCost(totalCost)
                        + ", gained their _War Sun_ technology, and must place 2 War Suns with _Piece de Resistance - Custom Job_.");
    }

    @ButtonHandler(PLACE_HERO_WARSUN)
    public static void placeHeroWarsun(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(PLACE_HERO_WARSUN.length()));
        int remaining;
        try {
            remaining = Integer.parseInt(game.getStoredValue(heroWarsunKey(player)));
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (tile == null
                || remaining < 1
                || !tile.getPlanetUnitHolders().stream()
                        .anyMatch(planet -> planet.getUnitCount(ti4.helpers.Units.UnitType.Spacedock, player) > 0)
                || FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 ws");
        remaining--;
        if (remaining == 0) {
            game.removeStoredValue(heroWarsunKey(player));
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(heroWarsunKey(player), Integer.toString(remaining));
        ButtonHelper.deleteMessage(event);
        sendHeroWarsunButtons(event, game, player);
    }

    private static void sendHeroDestroyButtons(ButtonInteractionEvent event, Game game, Player player, int page) {
        float destroyedCost;
        try {
            destroyedCost = Float.parseFloat(game.getStoredValue(heroCostKey(player)));
        } catch (NumberFormatException e) {
            destroyedCost = 0;
        }
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                for (UnitKey unitKey : holder.getUnitKeysForPlayer(player)) {
                    UnitModel unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
                    if (unit == null || unit.getCost() <= 0) {
                        continue;
                    }
                    for (UnitState state : holder.getNonZeroUnitStates(unitKey)) {
                        buttons.add(Buttons.red(
                                player.factionButtonChecker() + DESTROY_HERO_UNIT + tile.getPosition() + "|"
                                        + holder.getName() + "|" + unitKey.asyncID() + "|" + state.name(),
                                "Destroy " + unit.getName() + " (" + formatCost(unit.getCost()) + ")",
                                unit.getUnitEmoji()));
                    }
                }
            }
        }
        buttons.sort(Comparator.comparing(Button::getLabel));
        String message =
                player.getRepresentation() + ", choose units to destroy for _Piece de Resistance - Custom Job_. "
                        + "Destroyed cost: " + formatCost(destroyedCost) + "/12.";
        List<Button> displayed = NewStuffHelper.buttonPagination(
                buttons, null, player.factionButtonChecker() + PAGE_HERO_DESTROY, 25, page, false);
        if (event == null) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, displayed);
        } else {
            MessageHelper.editMessageWithButtons(event, message, displayed);
        }
    }

    private static void sendHeroWarsunButtons(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = game.getTileMap().values().stream()
                .filter(tile -> tile.getPlanetUnitHolders().stream()
                        .anyMatch(planet -> planet.getUnitCount(ti4.helpers.Units.UnitType.Spacedock, player) > 0))
                .filter(tile -> !FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + PLACE_HERO_WARSUN + tile.getPosition(),
                        "Place War Sun in " + tile.getRepresentationForButtons(game, player)))
                .toList();
        String message =
                player.getRepresentation() + ", choose a system containing your space dock and no other player's units "
                        + "in which to place a War Sun.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    private static String commanderKey(Player player) {
        return "scrapyardCommanderNextRound" + player.getFaction();
    }

    private static String commanderUsedKey(Player player) {
        return "scrapyardCommanderUsed" + player.getFaction();
    }

    private static String heroCostKey(Player player) {
        return "scrapyardHeroDestroyedCost" + player.getFaction();
    }

    private static String heroWarsunKey(Player player) {
        return "scrapyardHeroWarsuns" + player.getFaction();
    }

    private static String formatCost(float cost) {
        return cost == Math.round(cost) ? Integer.toString(Math.round(cost)) : Float.toString(cost);
    }
}
