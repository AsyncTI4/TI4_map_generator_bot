package ti4.service.combat.sustain.homebrew;

import java.util.List;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.ponthous.PonthousUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.thurviali.ThurvialiUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.vanguard.VanguardUnitHandler;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;
import ti4.service.combat.sustain.CancelsTwoHitsRule;
import ti4.service.combat.sustain.SustainCatalog;
import ti4.service.combat.sustain.SustainContext;
import ti4.service.combat.sustain.SustainRule;

public final class TheodisiSustainRules implements SustainCatalog {

    public static final SustainCatalog CATALOG = new TheodisiSustainRules();

    private static final List<SustainRule> RULES = List.of(
            SustainRule.suppressor(
                    "radiant_grafting_flight",
                    (context, unit) -> ThurvialiUnitHandler.isStructureUnitAbilitySuppressed(
                            context.owner(), context.holder(), unit)),
            SustainRule.grant("ponthous_flagship", TheodisiSustainRules::fighterGivenSustainByOldGlory),
            SustainRule.grant("vanguard_mech", TheodisiSustainRules::shipGivenSustainByBulwark));

    private static final List<CancelsTwoHitsRule> CANCELS_TWO_HITS = List.of(new CancelsTwoHitsRule(
            "kryxos_flagship3",
            (context, unit) ->
                    unit.getUnitType() == UnitType.Flagship && context.owner().ownsUnit("kryxos_flagship3")));

    @Override
    public List<SustainRule> rules() {
        return RULES;
    }

    @Override
    public List<CancelsTwoHitsRule> cancelsTwoHits() {
        return CANCELS_TWO_HITS;
    }

    private static boolean fighterGivenSustainByOldGlory(SustainContext context, UnitModel unit) {
        return unit.getUnitType() == UnitType.Fighter
                && context.tile() != null
                && PonthousUnitHandler.getTemporaryFighterSustainRemaining(
                                context.game(), context.owner(), context.tile())
                        > 0;
    }

    private static boolean shipGivenSustainByBulwark(SustainContext context, UnitModel unit) {
        return VanguardUnitHandler.hasBulwarkSustain(
                context.game(), context.owner(), context.tile(), context.unitKeyOf(unit));
    }
}
