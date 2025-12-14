package ti4.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;

class LeaderModelTest extends ModelTest<LeaderModel> {

    @Override
    public Map<String, LeaderModel> getModels() {
        return Mapper.getLeaders();
    }

    @Test
    void testLeaders() {
        System.out.println("Validating `" + count() + "` leaders");
        for (LeaderModel model : getModelList()) {
            assertTrue(model.isValid(), model.getAlias() + ": invalid");
            assertTrue(validateFaction(model), model.getAlias() + ": invalid FactionID");
            assertTrue(validateHomebrewReplacesID(model), model.getAlias() + ": invalid HomebrewReplacesID");
            assertTrue(validateLeaderEmoji(model), ": doesn't have a linked leader emoji");
        }
    }

    private boolean validateFaction(LeaderModel model) {
        if (model.getFaction().isEmpty()) return true;
        if (Mapper.isValidFaction(model.getFaction())
                || "keleres".equals(model.getFaction())
                || "fogalliance".equals(model.getFaction())
                || "generic".equals(model.getFaction())) return true;
        System.out.println("Tech **" + model.getAlias() + "** failed validation due to invalid FactionID: `"
                + model.getFaction() + "`");
        return false;
    }

    private boolean validateHomebrewReplacesID(LeaderModel model) {
        if (model.getHomebrewReplacesID().isEmpty()) return true;
        if (Mapper.isValidLeader(model.getHomebrewReplacesID().get())) return true;
        System.out.println("Tech **" + model.getAlias() + "** failed validation due to invalid HomebrewReplacesID ID: `"
                + model.getHomebrewReplacesID().get() + "`");
        return false;
    }

    private boolean validateLeaderEmoji(LeaderModel model) {
        // supported sources
        List<ComponentSource> srcs = List.of(
                ComponentSource.ds,
                ComponentSource.base,
                ComponentSource.pok,
                ComponentSource.thunders_edge,
                ComponentSource.blue_reverie);
        if (!srcs.contains(model.getSource())) return true;

        TI4Emoji e = LeaderEmojis.getLeaderEmoji(model.getAlias());
        if (MiscEmojis.goodDogs().contains(e))
            System.out.println(model.getAlias().toLowerCase() + " does not have a mapped emoji.");
        return true;
    }
}
