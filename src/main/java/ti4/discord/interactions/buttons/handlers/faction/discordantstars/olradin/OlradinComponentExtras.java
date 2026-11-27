package ti4.discord.interactions.buttons.handlers.faction.discordantstars.olradin;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class OlradinComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "olradin";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(
                ExtraCards.components(
                        DraftCategory.ABILITY,
                        "policies",
                        DraftCategory.ABILITY,
                        "policy_the_economy_empower",
                        "policy_the_economy_exploit",
                        "policy_the_environment_plunder",
                        "policy_the_environment_preserve",
                        "policy_the_people_connect",
                        "policy_the_people_control"),
                ExtraCards.components(
                        DraftCategory.MECH,
                        "olradin_mech",
                        DraftCategory.UNIT,
                        "olradin_mech_positive",
                        "olradin_mech_negative"));
    }
}
