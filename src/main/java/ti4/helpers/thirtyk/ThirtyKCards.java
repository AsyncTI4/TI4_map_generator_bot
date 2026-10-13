package ti4.helpers.thirtyk;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import ti4.image.Mapper;
import ti4.model.ActionCardModel;
import ti4.model.ExploreModel;
import ti4.model.RelicModel;
import ti4.model.SecretObjectiveModel;
import ti4.model.Source.ComponentSource;

@UtilityClass
public class ThirtyKCards {

    private static final Pattern COPY_NUMBER = Pattern.compile("(30k|jmsc)\\d+$");

    public static String baseId(String cardId) {
        if (cardId == null) return "";
        return COPY_NUMBER.matcher(cardId).replaceAll("$1");
    }

    public static boolean isActionCard(String alias) {
        ActionCardModel card = Mapper.getActionCard(alias);
        return card != null && card.getSource() == ComponentSource.thirtyk;
    }

    public static boolean isExplore(String id) {
        ExploreModel card = Mapper.getExplore(id);
        return card != null && card.getSource() == ComponentSource.thirtyk;
    }

    public static boolean isRelic(String alias) {
        RelicModel card = Mapper.getRelic(alias);
        return card != null && card.getSource() == ComponentSource.thirtyk;
    }

    public static boolean isSecretObjective(String alias) {
        SecretObjectiveModel card = Mapper.getSecretObjective(alias);
        return card != null && card.getSource() == ComponentSource.thirtyk;
    }
}
