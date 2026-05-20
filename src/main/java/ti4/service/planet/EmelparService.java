package ti4.service.planet;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.RegexHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.BreakthroughModel;
import ti4.model.LeaderModel;
import ti4.model.PlanetModel;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.regex.RegexService;

@UtilityClass
public class EmelparService {

    public PlanetModel emelpar() {
        return Mapper.getPlanet("emelpar");
    }

    public List<Button> getReadyComponentButtons(Game game, Player player) {
        List<Button> buttons = getReadyComponentButtons(game, player, "emelparReady_", true);
        if (!game.isFowMode()) {
            buttons.add(Buttons.gray("getOtherFactionsEmelpar", "Ready Another Player's Components"));
        }
        return buttons;
    }

    public List<Button> getReadyComponentButtons(Game game, Player player, String prefix) {
        return getReadyComponentButtons(game, player, prefix, false);
    }

    private static List<Button> getReadyComponentButtons(
            Game game, Player player, String prefix, boolean includeOwningFactionInButtonId) {
        List<Button> buttons = new ArrayList<>();
        String ownerPrefix = includeOwningFactionInButtonId ? player.getFaction() + "_" : "";

        String componentPrefix = prefix + "planet_" + ownerPrefix;
        for (String planet : player.getExhaustedPlanets()) {
            buttons.add(Buttons.green(componentPrefix + planet, Helper.getPlanetRepresentation(planet, game)));
        }

        componentPrefix = prefix + "breakthrough_" + ownerPrefix;
        for (String bt : player.getBreakthroughIDs()) {
            if (player.isBreakthroughExhausted(bt) && player.isBreakthroughUnlocked(bt)) {
                BreakthroughModel btModel = Mapper.getBreakthrough(bt);
                String label = "Ready " + btModel.getName() + " Breakthrough";
                buttons.add(Buttons.blue(componentPrefix + bt, label, player.getFactionEmoji()));
            }
        }

        componentPrefix = prefix + "leader_" + ownerPrefix;
        for (Leader leader : player.getLeaders()) {
            if (leader.isExhausted()) {
                String leaderName =
                        leader.getLeaderModel().map(LeaderModel::getName).orElse(leader.getId());
                buttons.add(Buttons.gray(
                        componentPrefix + leader.getId(),
                        "Ready " + leaderName + (Constants.AGENT.equals(leader.getType()) ? " Agent" : " Leader"),
                        LeaderEmojis.getLeaderTypeEmoji(leader.getType())));
            }
        }

        componentPrefix = prefix + "relic_" + ownerPrefix;
        for (String relic : player.getExhaustedRelics()) {
            RelicModel model = Mapper.getRelic(relic);
            buttons.add(
                    Buttons.red(componentPrefix + relic, "Ready " + model.getName() + " Relic", ExploreEmojis.Relic));
        }

        componentPrefix = prefix + "tech_" + ownerPrefix;
        for (String tech : player.getExhaustedTechs()) {
            TechnologyModel model = Mapper.getTech(tech);
            buttons.add(Buttons.green(
                    componentPrefix + tech,
                    "Ready " + model.getName() + "Technology",
                    model.getCondensedReqsEmojis(true)));
        }

        componentPrefix = prefix + "legendary_" + ownerPrefix;
        for (String planet : player.getExhaustedPlanetsAbilities()) {
            PlanetModel model = Mapper.getPlanet(planet);
            buttons.add(Buttons.blue(
                    componentPrefix + planet, "Ready " + model.getName() + " Ability", MiscEmojis.LegendaryPlanet));
        }
        return buttons;
    }

    public static String readyComponent(Game game, Player player, String type, String detail) {
        switch (type) {
            case "planet" -> {
                player.refreshPlanet(detail);
                return Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(detail, game);
            }
            case "breakthrough" -> {
                player.setBreakthroughExhausted(detail, false);
                return player.getBreakthroughModel(detail).getNameRepresentation();
            }
            case "leader" -> {
                Leader leader = player.getLeaderByID(detail).orElse(null);
                if (leader == null) {
                    return null;
                }
                leader.setExhausted(false);
                return leader.getLeaderModel()
                        .map(LeaderModel::getNameRepresentation)
                        .orElse(detail);
            }
            case "relic" -> {
                RelicModel model = Mapper.getRelic(detail);
                player.removeExhaustedRelic(detail);
                return ExploreEmojis.Relic + " " + model.getName();
            }
            case "tech" -> {
                player.refreshTech(detail);
                TechnologyModel model = Mapper.getTech(detail);
                return model.getNameRepresentation();
            }
            case "legendary" -> {
                PlanetModel model = Mapper.getPlanet(detail);
                player.refreshPlanetAbility(detail);
                return model.getLegendaryNameRepresentation();
            }
            default -> {
                return null;
            }
        }
    }

    @ButtonHandler(value = "getOtherFactionsEmelpar", save = false)
    private static void getOtherFactionsEmelpar(ButtonInteractionEvent event, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (Player player2 : game.getRealPlayers()) {
            buttons.add(Buttons.gray(
                    "getEmelparButtons_" + player2.getFaction(),
                    player2.getFactionModel().getShortName(),
                    player2.getFactionEmoji()));
        }
        String msg = "Choose the faction you wish to ready a component for.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(value = "getEmelparButtons_", save = false)
    private static void getEmelparButtons(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String msg = player.getRepresentationUnfogged() + ", please choose a component to ready.";
        Player player2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = getReadyComponentButtons(game, player2);

        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    private static void postSummary(ButtonInteractionEvent event, Player player, String whatReadied) {
        String summary = player.getRepresentationUnfogged() + " readied " + whatReadied + " using "
                + emelpar().getLegendaryNameRepresentation() + ".";
        MessageHelper.sendMessageToEventChannel(event, summary);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("emelparReady_planet_")
    private static void readyPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player player2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[2]);
        buttonID = buttonID.replace(player2.getFaction() + "_", "");
        String regex = "emelparReady_planet_" + RegexHelper.unitHolderRegex(game, "planet");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String planet = matcher.group("planet");
            String readyItem = readyComponent(game, player2, "planet", planet);
            postSummary(event, player, readyItem);
        });
    }

    @ButtonHandler("emelparReady_breakthrough_")
    private static void readyBreakthrough(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "emelparReady_breakthrough_" + RegexHelper.breakthroughRegex(game);
        Player player2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[2]);
        buttonID = buttonID.replace(player2.getFaction() + "_", "");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String bt = matcher.group("breakthrough");
            String readyItem = readyComponent(game, player2, "breakthrough", bt);
            postSummary(event, player, readyItem);
        });
    }

    @ButtonHandler("emelparReady_leader_")
    private static void readyLeader(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "emelparReady_leader_" + RegexHelper.leaderRegex(game);
        Player player2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[2]);
        buttonID = buttonID.replace(player2.getFaction() + "_", "");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String leaderID = matcher.group("leader");
            String readyMsg = readyComponent(game, player2, "leader", leaderID);
            if (readyMsg != null) {
                postSummary(event, player, readyMsg);
            }
        });
    }

    @ButtonHandler("emelparReady_relic_")
    private static void readyRelic(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "emelparReady_relic_" + RegexHelper.relicRegex(game);
        Player player2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[2]);
        buttonID = buttonID.replace(player2.getFaction() + "_", "");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String relicID = matcher.group("relic");
            String readyMsg = readyComponent(game, player2, "relic", relicID);
            postSummary(event, player, readyMsg);
        });
    }

    @ButtonHandler("emelparReady_tech_")
    private static void readyTechnology(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "emelparReady_tech_" + RegexHelper.techRegex(game);
        Player player2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[2]);
        buttonID = buttonID.replace(player2.getFaction() + "_", "");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String techID = matcher.group("tech");
            String readyMsg = readyComponent(game, player2, "tech", techID);
            postSummary(event, player, readyMsg);
        });
    }

    @ButtonHandler("emelparReady_legendary_")
    private static void readyLegendaryAbility(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "emelparReady_legendary_" + RegexHelper.unitHolderRegex(game, "planet");
        Player player2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[2]);
        buttonID = buttonID.replace(player2.getFaction() + "_", "");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String planet = matcher.group("planet");
            String readyMsg = readyComponent(game, player2, "legendary", planet);
            postSummary(event, player, readyMsg);
        });
    }
}
