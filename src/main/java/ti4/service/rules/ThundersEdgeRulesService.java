package ti4.service.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.RuleModel;

/**
 * There are a lot of new rules. I think compiling all of the new rules into embeds that we can post into tabletalk when they become relevant
 * will go a long way toward players not getting too lost with all the new stuff. It will help prevent constant questions in rules channels,
 * and should waylay hard feelings that could come from misunderstanding the new stuff.
 */
public class ThundersEdgeRulesService {

    private static List<MessageEmbed> getRuleEmbeds(String... rules) {
        return Arrays.stream(rules)
                .map(Mapper::getRule)
                .filter(Objects::nonNull)
                .map(RuleModel::getRepresentationEmbed)
                .filter(Objects::nonNull)
                .toList();
    }

    public static List<MessageEmbed> startOfDraftRules(Game game) {
        List<MessageEmbed> relevantRules =
                new ArrayList<>(getRuleEmbeds("expeditions", "breakthroughAndSynergy", "coexist"));

        Set<String> extraRules = new HashSet<>();
        for (var slice : game.getMiltyDraftManager().getSlices()) {
            for (var tile : slice.getTiles()) {
                if (tile.getTile().isScar()) extraRules.add("entropicScar");
                for (var planet : tile.getTile().getPlanetUnitHolders()) {
                    if (planet.isSpaceStation()) extraRules.add("spaceStations");
                    if (planet.getPlanetTypes().size() > 1) extraRules.add("dualTraitsSkips");
                    if (planet.getTechSpecialities().size() > 1) extraRules.add("dualTraitsSkips");
                }
            }
        }
        for (String rule : extraRules) relevantRules.addAll(getRuleEmbeds(rule));
        return relevantRules;
    }

    public static List<MessageEmbed> fractureSpawnedRuleEmbeds() {
        return getRuleEmbeds("fractureOverview", "ingressPlacement", "otherFractureRules", "neutralUnits");
    }

    public static void alertTabletalkWithRulesAtStartOfDraft(Game game) {
        String msg = "Hello " + game.getPing() + "!\n";
        msg +=
                "It looks like you are playing with Thunder's Edge. Since it's new, you might like some of the rules posted here for your convenience:";
        if (game.getTableTalkChannel() != null) {
            List<MessageEmbed> embeds = startOfDraftRules(game);
            MessageHelper.sendMessageToChannelWithEmbeds(game.getTableTalkChannel(), msg, embeds);
        }
    }

    public static void alertTabletalkWithFractureRules(Game game) {
        String msg = "Hello " + game.getPing() + "!\n";
        msg +=
                "It looks like The Fracture has spawned, and since it's a new feature you might like some of the rules posted here for your convenience:";
        if (game.getTableTalkChannel() != null) {
            List<MessageEmbed> fractureEmbeds = fractureSpawnedRuleEmbeds();
            MessageHelper.sendMessageToChannelWithEmbeds(game.getTableTalkChannel(), msg, fractureEmbeds);
        }
    }
}
