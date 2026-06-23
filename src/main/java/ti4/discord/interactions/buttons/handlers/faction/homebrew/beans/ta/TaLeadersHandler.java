package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.leader.ExhaustLeaderService;

@UtilityClass
public class TaLeadersHandler {
    private static final String AGENT_ID = "taagent";
    private static final String LEN_SELECT_TARGET_PREFIX = "taagent_selectTarget";
    private static final String LEN_CHOOSE_PLANET_PREFIX = "taagent_choosePlanet_";
    private static final String LEN_APPLY_PREFIX = "taagent_apply_";
    private static final String LEN_PREDECLARE_PREFIX = "taagentPredeclare_";
    private static final String COMMANDER_CONVERT_PREFIX = "taCommanderConvert_";
    private static final String HERO_ATTACH_PREFIX = "taHeroAttachGrand_";

    public static void resolveTaCommander(Player player, Tile tile, Game game, String planetName) {
        if (tile == null || player == null) {
            return;
        }

        Planet planet = tile.getUnitHolderFromPlanet(planetName);
        player.gainCommodities(1);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                "Gained 1 "
                        + MiscEmojis.comm
                        + " from exploring "
                        + Helper.getPlanetRepresentationPlusEmoji(planetName)
                        + " due to _Zul_, the Ta Commander.");
        if (planet.hasAttachment()
                || TaAbilityHandler.planetHasAnyDesignAttached(tile, planetName) && player.getCommodities() > 0) {
            List<Button> buttons = List.of(Buttons.green(
                    player.factionButtonChecker() + COMMANDER_CONVERT_PREFIX + tile.getPosition() + "|" + planetName,
                    "Convert Commodity to Trade Good",
                    MiscEmojis.comm));

            String typeOfAttachment =
                    (TaAbilityHandler.planetHasAnyDesignAttached(tile, planetName) ? "a design, " : "an attachment, ");

            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", because "
                            + Helper.getPlanetRepresentation(planetName, game)
                            + " has "
                            + typeOfAttachment
                            + "you may convert the recently gained commodity to a trade good.",
                    buttons);
        }
    }

    @ButtonHandler(COMMANDER_CONVERT_PREFIX)
    public static void resolveTaCommanderConvert(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null || !buttonID.startsWith(COMMANDER_CONVERT_PREFIX)) {
            return;
        }

        String payload = buttonID.substring(COMMANDER_CONVERT_PREFIX.length());
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String tilePosition = parts[0];
        String planetName = parts[1];
        Tile tile = game.getTileByPosition(tilePosition);
        Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(planetName);

        if (tile == null
                || planet == null
                || player.getCommodities() < 1
                || !game.playerHasLeaderUnlockedOrAlliance(player, "tacommander")
                || !(planet.hasAttachment() || TaAbilityHandler.planetHasAnyDesignAttached(tile, planetName))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.setCommodities(player.getCommodities() - 1);
        player.gainTG(1);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " converted 1 "
                        + MiscEmojis.comm
                        + " to 1 "
                        + MiscEmojis.tg
                        + " due to _Zul_, the Ta Commander.");
        ButtonHelper.deleteMessage(event);
    }

    public static Button getLenCardsInfoButton() {
        return Buttons.gray(LEN_SELECT_TARGET_PREFIX, "Use Len", FactionEmojis.ta);
    }

    @ButtonHandler(LEN_SELECT_TARGET_PREFIX)
    public static void offerLenTargetButtons(ButtonInteractionEvent event, Game game, Player player) {
        if (!canUseLen(game, player)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + ", _Len_ can only be declared during an active agenda vote.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (getEligibleLenPlanets(game, target).isEmpty()) {
                continue;
            }
            buttons.add(Buttons.gray(
                    LEN_CHOOSE_PLANET_PREFIX + target.getFaction(), " voting planets", target.getFactionEmoji()));
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + ", there are no eligible voting planets for _Len_ right now.");
            return;
        }

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose the player whose voting planet will be doubled by _Len_.",
                buttons);
    }

    @ButtonHandler(LEN_CHOOSE_PLANET_PREFIX)
    public static void offerLenPlanetButtons(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        if (!canUseLen(game, player)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + ", _Len_ can only be declared during an active agenda vote.");
            return;
        }

        String targetFaction = buttonID.substring(LEN_CHOOSE_PLANET_PREFIX.length());
        Player target = game.getPlayerFromColorOrFaction(targetFaction);
        if (target == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<String> eligiblePlanets = getEligibleLenPlanets(game, target);
        if (eligiblePlanets.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    target.getRepresentationNoPing() + " has no eligible voting planets for _Len_ right now.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String planet : eligiblePlanets) {
            Planet planetModel = game.getPlanetsInfo().get(planet);
            int influence = planetModel == null ? 0 : planetModel.getInfluence();
            String planetName =
                    Mapper.getPlanet(planet) != null && Mapper.getPlanet(planet).getName() != null
                            ? Mapper.getPlanet(planet).getName()
                            : StringUtils.capitalize(planet);
            buttons.add(Buttons.gray(
                    LEN_APPLY_PREFIX + target.getFaction() + ";" + planet,
                    planetName + " (+" + influence + " vote" + (influence == 1 ? "" : "s") + ")"));
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose which of " + target.getRepresentationNoPing()
                        + "'s planets _Len_ will double for this agenda.",
                buttons);
    }

    @ButtonHandler(LEN_APPLY_PREFIX)
    public static void applyLenToPlanet(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        if (!canUseLen(game, player)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + ", _Len_ can only be declared during an active agenda vote.");
            return;
        }

        String payload = buttonID.substring(LEN_APPLY_PREFIX.length());
        String[] parts = payload.split(";", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        String planet = parts[1];
        if (target == null || !getEligibleLenPlanets(game, target).contains(planet)) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + ", that planet is no longer eligible for _Len_.");
            return;
        }

        Leader agent = player.getLeader(AGENT_ID).orElse(null);
        if (agent == null || agent.isExhausted()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ExhaustLeaderService.exhaustLeader(game, player, agent);
        game.setStoredValue(
                getLenPredeclareKey(player), game.getCurrentAgendaInfo() + ";" + target.getFaction() + ";" + planet);

        Planet planetModel = game.getPlanetsInfo().get(planet);
        int influence = planetModel == null ? 0 : planetModel.getInfluence();
        String planetRepresentation = Helper.getPlanetRepresentationPlusEmoji(planet);
        String ownerMessage = player.getRepresentationNoPing() + " exhausted _Len_ to give "
                + target.getRepresentationNoPing() + "'s vote on " + planetRepresentation + " +"
                + influence + " vote" + (influence == 1 ? "" : "s") + " this agenda.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), ownerMessage);

        ButtonHelper.deleteMessage(event);
    }

    public static int getLenPredeclaredVoteBonus(Game game, Player player, String planet) {
        if (game == null || player == null || StringUtils.isBlank(planet)) {
            return 0;
        }

        int bonus = 0;
        for (Player potentialOwner : game.getRealPlayers()) {
            String storedValue = game.getStoredValue(getLenPredeclareKey(potentialOwner));
            if (StringUtils.isBlank(storedValue)) {
                continue;
            }

            String[] parts = storedValue.split(";", 3);
            if (parts.length != 3
                    || !StringUtils.equals(parts[0], game.getCurrentAgendaInfo())
                    || !StringUtils.equals(parts[1], player.getFaction())
                    || !StringUtils.equals(parts[2], planet)) {
                continue;
            }

            Planet planetModel = game.getPlanetsInfo().get(planet);
            if (planetModel != null) {
                bonus += planetModel.getInfluence();
            }
        }
        return bonus;
    }

    public static String getLenVoteLabelSuffix(Game game, Player player, String planet) {
        return getLenPredeclaredVoteBonus(game, player, planet) > 0 ? " [Len]" : "";
    }

    public static void clearLenPredeclareForPlanet(Game game, Player player, String planet) {
        if (game == null || player == null || StringUtils.isBlank(planet)) {
            return;
        }

        for (Player potentialOwner : game.getRealPlayers()) {
            String key = getLenPredeclareKey(potentialOwner);
            String storedValue = game.getStoredValue(key);
            if (StringUtils.isBlank(storedValue)) {
                continue;
            }

            String[] parts = storedValue.split(";", 3);
            if (parts.length == 3
                    && StringUtils.equals(parts[0], game.getCurrentAgendaInfo())
                    && StringUtils.equals(parts[1], player.getFaction())
                    && StringUtils.equals(parts[2], planet)) {
                game.removeStoredValue(key);
            }
        }
    }

    public static void clearAllLenPredeclaresForPlayer(Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        for (Player potentialOwner : game.getRealPlayers()) {
            String key = getLenPredeclareKey(potentialOwner);
            String storedValue = game.getStoredValue(key);
            if (StringUtils.isBlank(storedValue)) {
                continue;
            }

            String[] parts = storedValue.split(";", 3);
            if (parts.length == 3
                    && StringUtils.equals(parts[0], game.getCurrentAgendaInfo())
                    && StringUtils.equals(parts[1], player.getFaction())) {
                game.removeStoredValue(key);
            }
        }
    }

    private static boolean canUseLen(Game game, Player player) {
        return game != null
                && player != null
                && player.hasUnexhaustedLeader(AGENT_ID)
                && game.getPhaseOfGame().toLowerCase().contains("agenda")
                && StringUtils.isNotBlank(game.getCurrentAgendaInfo());
    }

    private static List<String> getEligibleLenPlanets(Game game, Player target) {
        List<String> eligiblePlanets = new ArrayList<>();
        if (game == null || target == null) {
            return eligiblePlanets;
        }

        for (String planet : target.getReadiedPlanets()) {
            if (AgendaHelper.getSpecificPlanetsVoteWorth(target, game, planet) > 0) {
                eligiblePlanets.add(planet);
            }
        }
        return eligiblePlanets;
    }

    private static String getLenPredeclareKey(Player player) {
        return LEN_PREDECLARE_PREFIX + player.getFaction();
    }

    public static void postHeroButtons(Game game, Player player, GenericInteractionCreateEvent event) {
        if (game == null || player == null || event == null) {
            return;
        }

        Tile hs = player.getHomeSystemTile();
        if (hs == null) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Planet planet : hs.getPlanetUnitHolders()) {
            String planetName = planet.getName();
            if (!player.getPlanetsAllianceMode().contains(planetName)) {
                continue;
            }
            if (TaAbilityHandler.planetHasAnyDesignAttached(hs, planetName)) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + HERO_ATTACH_PREFIX + hs.getPosition() + "|" + planetName,
                    "Attach Grand Design (Pinnacle) to " + Helper.getPlanetRepresentation(planetName, game)));
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged() + ", there are no eligible home planets for _Zat_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", choose a planet in your home system to attach _Grand Design (Pinnacle)_ to.",
                buttons);
    }

    @ButtonHandler(HERO_ATTACH_PREFIX)
    public static void resolveTaHeroAttach(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null || !buttonID.startsWith(HERO_ATTACH_PREFIX)) {
            return;
        }

        String payload = buttonID.substring(HERO_ATTACH_PREFIX.length());
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String tilePosition = parts[0];
        String planetName = parts[1];

        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null) {
            return;
        }

        Planet planet = tile.getUnitHolderFromPlanet(planetName);
        if (planet == null) {
            return;
        }

        if (!player.getPlanetsAllianceMode().contains(planetName)
                || player.getHomeSystemTile() == null
                || !tile.getPosition().equals(player.getHomeSystemTile().getPosition())
                || TaAbilityHandler.planetHasAnyDesignAttached(tile, planetName)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", that planet is no longer eligible for _Zat_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String tokenPath = Mapper.getAttachmentImagePath("designgrand");
        if (tokenPath != null) {
            tile.addToken(tokenPath, planetName);
            TaUnitHandler.offerTaMechDeploy(event, player, game, tile, planetName);
        }

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation()
                        + " used _Zat_ to attach _Grand Design (Pinnacle)_ to "
                        + planet.getRepresentation(game));
        ButtonHelper.deleteMessage(event);

        resolveTaHeroFollowup(event, player, game);
    }

    public static void resolveTaHeroFollowup(GenericInteractionCreateEvent event, Player player, Game game) {
        if (player == null || game == null || event == null) {
            return;
        }

        List<String> designPlanets = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Tile tile = game.getTileFromPlanet(planetName);
            if (tile == null) {
                continue;
            }

            if (TaAbilityHandler.planetHasAnyDesignAttached(tile, planetName)) {
                designPlanets.add(planetName);
            }
        }

        if (designPlanets.isEmpty()) {
            return;
        }

        List<String> readiedPlanets = new ArrayList<>();
        for (String planetName : designPlanets) {
            if (!player.getExhaustedPlanets().contains(planetName)) {
                continue;
            }

            player.refreshPlanet(planetName);
            readiedPlanets.add(planetName);
        }

        if (!readiedPlanets.isEmpty()) {
            StringBuilder readiedList = new StringBuilder();
            for (int i = 0; i < readiedPlanets.size(); i++) {
                String planetName = readiedPlanets.get(i);
                readiedList.append(Helper.getPlanetRepresentation(planetName, game));

                if (i < readiedPlanets.size() - 1) {
                    readiedList.append(", ");
                }
            }

            String message =
                    player.getRepresentationUnfogged() + " readied the following planets due to _Zat_: " + readiedList;

            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        }

        for (String planetName : designPlanets) {
            Tile tile = game.getTileFromPlanet(planetName);
            Planet planet = game.getPlanetsInfo().get(planetName);
            if (tile == null || planet == null) {
                continue;
            }

            List<Button> exploreButtons = ButtonHelper.getPlanetExplorationButtons(game, planet, player);
            if (exploreButtons == null || exploreButtons.isEmpty()) {
                continue;
            }

            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    "You may explore " + Helper.getPlanetRepresentation(planetName, game) + " due to _Zat_.",
                    exploreButtons);
        }
    }
}
