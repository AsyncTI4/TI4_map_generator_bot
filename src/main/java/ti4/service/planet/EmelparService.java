package ti4.service.planet;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.RegexHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.BreakthroughModel;
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
        List<Button> buttons = new ArrayList<>();

        String prefix = "emelparReady_planet_";
        for (String planet : player.getExhaustedPlanets()) {
            buttons.add(Buttons.green(prefix + planet, Helper.getPlanetRepresentation(planet, game)));
        }

        prefix = "emelparReady_breakthrough_";
        BreakthroughModel btModel = player.getBreakthroughModel();
        if (btModel != null && player.isBreakthroughExhausted()) {
            buttons.add(Buttons.blue(
                    prefix + btModel.getAlias(), "Ready breakthrough " + btModel.getName(), player.getFactionEmoji()));
        }

        prefix = "emelparReady_leader_";
        for (Leader leader : player.getLeaders()) {
            if (leader.isExhausted()) {
                String leaderName =
                        leader.getLeaderModel().map(lm -> lm.getName()).orElse(leader.getId());
                buttons.add(Buttons.gray(
                        prefix + leader.getId(),
                        "Ready leader " + leaderName,
                        LeaderEmojis.getLeaderTypeEmoji(leader.getType())));
            }
        }

        prefix = "emelparReady_relic_";
        for (String relic : player.getExhaustedRelics()) {
            RelicModel model = Mapper.getRelic(relic);
            buttons.add(Buttons.red(prefix + relic, "Ready relic " + model.getName(), ExploreEmojis.Relic));
        }

        prefix = "emelparReady_tech_";
        for (String tech : player.getExhaustedTechs()) {
            TechnologyModel model = Mapper.getTech(tech);
            buttons.add(
                    Buttons.green(prefix + tech, "Ready tech " + model.getName(), model.getCondensedReqsEmojis(true)));
        }

        prefix = "emelparReady_legendary_";
        for (String planet : player.getExhaustedPlanetsAbilities()) {
            PlanetModel model = Mapper.getPlanet(planet);
            buttons.add(Buttons.blue(prefix + planet, "Ready ability " + model.getName(), MiscEmojis.LegendaryPlanet));
        }

        return buttons;
    }

    private static void postSummary(ButtonInteractionEvent event, Player player, String whatReadied) {
        String summary = player.getRepresentationUnfogged() + " readied " + whatReadied + " using "
                + emelpar().getLegendaryNameRepresentation();
        MessageHelper.sendMessageToEventChannel(event, summary);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("emelparReady_planet_")
    private static void readyPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "emelparReady_planet_" + RegexHelper.unitHolderRegex(game, "planet");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String planet = matcher.group("planet");
            player.refreshPlanet(planet);
            String readyItem = Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game);
            postSummary(event, player, readyItem);
        });
    }

    @ButtonHandler("emelparReady_breakthrough_")
    private static void readyBreakthrough(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "emelparReady_breakthrough_" + RegexHelper.breakthroughRegex(game);
        RegexService.runMatcher(regex, buttonID, matcher -> {
            player.setBreakthroughExhausted(false);
            String readyItem = player.getBreakthroughModel().getNameRepresentation();
            postSummary(event, player, readyItem);
        });
    }

    @ButtonHandler("emelparReady_leader_")
    private static void readyLeader(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "emelparReady_leader_" + RegexHelper.leaderRegex(game);
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String leaderID = matcher.group("leader");
            Leader leader = player.getLeaderByID(leaderID).orElse(null);
            if (leader != null) {
                leader.setExhausted(false);
                String readyMsg = leader.getLeaderModel()
                        .map(l -> l.getNameRepresentation())
                        .orElse(leaderID);
                postSummary(event, player, readyMsg);
            }
        });
    }

    @ButtonHandler("emelparReady_relic_")
    private static void readyRelic(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "emelparReady_relic_" + RegexHelper.relicRegex(game);
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String relicID = matcher.group("relic");
            RelicModel model = Mapper.getRelic(relicID);
            player.removeExhaustedRelic(relicID);
            String readyMsg = ExploreEmojis.Relic + " " + model.getName();
            postSummary(event, player, readyMsg);
        });
    }

    @ButtonHandler("emelparReady_tech_")
    private static void readyTechnology(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "emelparReady_tech_" + RegexHelper.techRegex(game);
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String techID = matcher.group("tech");
            player.refreshTech(techID);
            TechnologyModel model = Mapper.getTech(techID);
            String readyMsg = model.getNameRepresentation();
            postSummary(event, player, readyMsg);
        });
    }

    @ButtonHandler("emelparReady_legendary_")
    private static void readyLegendaryAbility(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "emelparReady_legendary_" + RegexHelper.unitHolderRegex(game, "planet");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String planet = matcher.group("planet");
            PlanetModel model = Mapper.getPlanet(planet);
            player.refreshPlanetAbility(planet);
            String readyMsg = model.getLegendaryNameRepresentation();
            postSummary(event, player, readyMsg);
        });
    }
}
