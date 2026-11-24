package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.stoneborn;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class StonebornComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "stoneborn";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(
                ExtraCards.components(DraftCategory.TECH, "thstoneborng", DraftCategory.TECH, "thstoneborngu"),
                ExtraCards.components(DraftCategory.TECH, "thstoneborndn", DraftCategory.TECH, "thstoneborndnu"),
                ExtraCards.components(
                        DraftCategory.MECH, "stoneborn_mechdormant", DraftCategory.UNIT, "stoneborn_mechunleashed"),
                ExtraCards.components(
                        DraftCategory.AGENT, "stonebornagentdormant", DraftCategory.AGENT, "stonebornagentunleashed"),
                ExtraCards.components(
                        DraftCategory.COMMANDER,
                        "stoneborncommanderdormant",
                        DraftCategory.COMMANDER,
                        "stoneborncommanderunleashed"),
                ExtraCards.components(
                        DraftCategory.HERO, "stonebornherodormant", DraftCategory.HERO, "stonebornherounleashed"));
    }
}
