package ti4.service.combat;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.service.combat.sustain.CancelsTwoHitsRule;
import ti4.service.combat.sustain.OfficialSustainRules;
import ti4.service.combat.sustain.SustainCatalog;
import ti4.service.combat.sustain.SustainRule;
import ti4.service.combat.sustain.homebrew.BlueReverieSustainRules;
import ti4.service.combat.sustain.homebrew.DiscordantStarsSustainRules;
import ti4.service.combat.sustain.homebrew.FlagshippingSustainRules;
import ti4.service.combat.sustain.homebrew.TheodisiSustainRules;
import ti4.service.combat.sustain.twilightsfall.TwilightsFallSustainRules;

@UtilityClass
public class SustainCatalogs {

    private static final List<SustainCatalog> CATALOGS = List.of(
            OfficialSustainRules.CATALOG,
            BlueReverieSustainRules.CATALOG,
            DiscordantStarsSustainRules.CATALOG,
            TheodisiSustainRules.CATALOG,
            FlagshippingSustainRules.CATALOG,
            TwilightsFallSustainRules.CATALOG);

    private static final List<SustainRule> RULES =
            CATALOGS.stream().flatMap(catalog -> catalog.rules().stream()).toList();

    private static final List<CancelsTwoHitsRule> CANCELS_TWO_HITS = CATALOGS.stream()
            .flatMap(catalog -> catalog.cancelsTwoHits().stream())
            .toList();

    public static List<SustainCatalog> all() {
        return CATALOGS;
    }

    public static List<SustainRule> rules() {
        return RULES;
    }

    public static List<CancelsTwoHitsRule> cancelsTwoHits() {
        return CANCELS_TWO_HITS;
    }
}
