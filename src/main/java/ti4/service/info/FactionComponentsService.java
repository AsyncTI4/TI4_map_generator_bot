package ti4.service.info;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.arvaxi.ArvaxiBreakthroughHandler;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.draft.items.FactionDraftItem;
import ti4.draft.items.UnitDraftItem;
import ti4.message.MessageHelper;
import ti4.message.componentsV2.MessageV2Builder;
import ti4.model.AbilityModel;
import ti4.model.BreakthroughModel;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.model.Mapper;
import ti4.model.PromissoryNoteModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.franken.FrankenDraftBagService;

@UtilityClass
public class FactionComponentsService {

    private static final Set<DraftCategory> BEFORE_UNITS = EnumSet.of(DraftCategory.ABILITY, DraftCategory.TECH);
    private static final Set<String> DRAFTED_UNIT_TYPES = Set.of("mech", "flagship");
    private static final Set<DraftCategory> UNIT_CATEGORIES =
            EnumSet.of(DraftCategory.UNIT, DraftCategory.MECH, DraftCategory.FLAGSHIP);

    public static void sendFactionComponents(MessageChannel channel, String factionAlias) {
        FactionDraftItem item = new FactionDraftItem(factionAlias);
        List<DraftItem> components = factionComponents(factionAlias, item);
        if (components.isEmpty()) {
            MessageHelper.sendMessageToChannel(channel, "## " + item.getTitle(null) + " Components\nNone.");
            return;
        }

        FactionModel faction = Mapper.getFaction(factionAlias);
        List<TextDisplay> extraCards = extraComponentCards(faction);
        List<Color> accents = FrankenDraftBagService.getAccents();
        MessageV2Builder builder = new MessageV2Builder(channel);
        builder.append("## " + item.getTitle(null) + " Components");
        for (DraftItem component : components) {
            appendCard(builder, accents, cardText(component));
            if (isMainBreakthrough(component, faction)) {
                extraCards.forEach(card -> appendCard(builder, accents, List.of(card)));
                extraCards = List.of();
            }
        }
        extraCards.forEach(card -> appendCard(builder, accents, List.of(card)));
        builder.send();
    }

    private static void appendCard(
            MessageV2Builder builder, List<Color> accents, List<? extends ContainerChildComponent> card) {
        builder.append(Container.of(card).withAccentColor(accents.getFirst()));
        Collections.rotate(accents, -1);
    }

    private static boolean isMainBreakthrough(DraftItem component, FactionModel faction) {
        return faction != null
                && component.getItemCategory() == DraftCategory.BREAKTHROUGH
                && component.getItemId().equals(faction.getBreakthrough());
    }

    private static List<TextDisplay> extraComponentCards(FactionModel faction) {
        if (faction != null && "arvaxibt".equals(faction.getBreakthrough())) {
            return List.of(ArvaxiBreakthroughHandler.mobilizationEngineCard());
        }
        return List.of();
    }

    private static List<ContainerChildComponent> cardText(DraftItem component) {
        List<ContainerChildComponent> card = new ArrayList<>(cardBody(component));
        String notes = notesOf(component);
        if (notes != null && !notes.isBlank()) {
            card.add(TextDisplay.of("-# [" + notes.trim().replace("\n", "\n-# ") + "]"));
        }
        return card;
    }

    private static String notesOf(DraftItem component) {
        String id = component.getItemId();
        return switch (component.getItemCategory()) {
            case ABILITY ->
                Optional.ofNullable(Mapper.getAbility(id))
                        .map(AbilityModel::getNotes)
                        .orElse(null);
            case TECH ->
                Optional.ofNullable(Mapper.getTech(id))
                        .map(TechnologyModel::getNotes)
                        .orElse(null);
            case AGENT, COMMANDER, HERO ->
                Optional.ofNullable(Mapper.getLeader(id))
                        .map(LeaderModel::getNotes)
                        .orElse(null);
            case MECH, FLAGSHIP, UNIT ->
                Optional.ofNullable(Mapper.getUnit(id)).map(UnitModel::getNotes).orElse(null);
            case PN ->
                Optional.ofNullable(Mapper.getPromissoryNote(id))
                        .map(PromissoryNoteModel::getNotes)
                        .orElse(null);
            case BREAKTHROUGH ->
                Optional.ofNullable(Mapper.getBreakthrough(id))
                        .map(BreakthroughModel::getNotes)
                        .orElse(null);
            default -> null;
        };
    }

    private static List<? extends ContainerChildComponent> cardBody(DraftItem component) {
        UnitModel unit =
                component.getItemCategory() == DraftCategory.UNIT ? Mapper.getUnit(component.getItemId()) : null;
        if (unit == null) {
            return new ArrayList<>(component.getPlainTextDisplays(null));
        }
        String body = UnitDraftItem.rulesText(unit).trim().replace("\n", "\n> ");
        return List.of(TextDisplay.of(component.getTitle(null) + System.lineSeparator() + "> " + body));
    }

    private static List<DraftItem> factionComponents(String factionAlias, FactionDraftItem item) {
        List<DraftItem> components = new ArrayList<>(item.getAllComponentsIgnoringErrata());
        FactionModel faction = Mapper.getFaction(factionAlias);
        if (faction == null) {
            return components;
        }
        components.addAll(
                leadingCount(components, Set.of(DraftCategory.ABILITY)), abilityCardsOutsideAbilityList(faction));
        components.addAll(
                leadingCount(components, Set.of(DraftCategory.ABILITY, DraftCategory.TECH)),
                techsOutsideTechList(faction));
        List<DraftItem> units = new ArrayList<>(baseSpecificUnits(faction));
        components.stream()
                .filter(component -> UNIT_CATEGORIES.contains(component.getItemCategory()))
                .forEach(units::add);
        components.removeIf(component -> UNIT_CATEGORIES.contains(component.getItemCategory()));
        components.addAll(leadingCount(components, BEFORE_UNITS), units);
        components.addAll(breakthroughsOutsideMain(faction));
        return groupUpgradeChains(components);
    }

    private static List<DraftItem> techsOutsideTechList(FactionModel faction) {
        return Mapper.getTechs().values().stream()
                .filter(tech ->
                        tech.getFaction().filter(faction.getAlias()::equals).isPresent())
                .filter(tech -> tech.getSource() == faction.getSource())
                .filter(tech -> !faction.getFactionTech().contains(tech.getAlias()))
                .sorted(Comparator.comparing(TechnologyModel::getName))
                .map(tech -> DraftItem.generate(DraftCategory.TECH, tech.getAlias()))
                .toList();
    }

    private static List<DraftItem> breakthroughsOutsideMain(FactionModel faction) {
        return Mapper.getBreakthroughs().values().stream()
                .filter(bt -> bt.getFaction().filter(faction.getAlias()::equals).isPresent())
                .filter(bt -> bt.getSource() == faction.getSource())
                .filter(bt -> !bt.getAlias().equals(faction.getBreakthrough()))
                .sorted(Comparator.comparing(BreakthroughModel::getName))
                .map(bt -> DraftItem.generate(DraftCategory.BREAKTHROUGH, bt.getAlias()))
                .toList();
    }

    private static List<DraftItem> groupUpgradeChains(List<DraftItem> components) {
        Set<String> ownCards = new HashSet<>();
        components.forEach(component -> ownCards.add(component.getAlias()));
        Map<String, List<DraftItem>> chainByBase = new HashMap<>();
        Set<String> chainedCards = new HashSet<>();
        for (DraftItem component : components) {
            if (!UNIT_CATEGORIES.contains(component.getItemCategory())) {
                continue;
            }
            List<DraftItem> chain = upgradeChain(component, ownCards);
            chainByBase.put(component.getAlias(), chain);
            chain.forEach(member -> chainedCards.add(member.getAlias()));
        }
        List<DraftItem> ordered = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        for (DraftItem component : components) {
            List<DraftItem> chain = chainByBase.get(component.getAlias());
            if (chain == null && chainedCards.contains(component.getAlias())) {
                continue;
            }
            for (DraftItem item : chain == null ? List.of(component) : chain) {
                if (placed.add(item.getAlias())) {
                    ordered.add(item);
                }
            }
        }
        return ordered;
    }

    private static List<DraftItem> upgradeChain(DraftItem base, Set<String> ownCards) {
        List<DraftItem> chain = new ArrayList<>(List.of(base));
        Set<String> seen = new HashSet<>();
        UnitModel stage = nextStage(Mapper.getUnit(base.getItemId()));
        while (stage != null && seen.add(stage.getId())) {
            DraftItem card = stageCard(stage);
            if (card.getItemCategory() == DraftCategory.TECH && !ownCards.contains(card.getAlias())) {
                break;
            }
            chain.add(card);
            stage = nextStage(stage);
        }
        return chain;
    }

    private static UnitModel nextStage(UnitModel unit) {
        if (unit == null) {
            return null;
        }
        return unit.getUpgradesToUnitId()
                .map(Mapper::getUnit)
                .orElseGet(() -> Mapper.getUnits().values().stream()
                        .filter(candidate -> candidate
                                .getUpgradesFromUnitId()
                                .filter(unit.getId()::equals)
                                .isPresent())
                        .findFirst()
                        .orElse(null));
    }

    private static DraftItem stageCard(UnitModel stage) {
        return stage.getRequiredTechId()
                .filter(Mapper::isValidTech)
                .map(techId -> DraftItem.generate(DraftCategory.TECH, techId))
                .orElseGet(() -> DraftItem.generate(DraftCategory.UNIT, stage.getId()));
    }

    private static int leadingCount(List<DraftItem> components, Set<DraftCategory> categories) {
        return (int) components.stream()
                .takeWhile(component -> categories.contains(component.getItemCategory()))
                .count();
    }

    private static List<DraftItem> abilityCardsOutsideAbilityList(FactionModel faction) {
        return Mapper.getAbilities().values().stream()
                .filter(ability -> faction.getAlias().equals(ability.getFaction()))
                .filter(ability -> ability.getSource() == faction.getSource())
                .filter(ability -> !faction.getAbilities().contains(ability.getId()))
                .sorted(Comparator.comparing(AbilityModel::getName))
                .map(ability -> DraftItem.generate(DraftCategory.ABILITY, ability.getId()))
                .toList();
    }

    private static List<DraftItem> baseSpecificUnits(FactionModel faction) {
        return faction.getUnits().stream()
                .map(Mapper::getUnit)
                .filter(unit -> unit != null && unit.getFaction().isPresent())
                .filter(unit -> !DRAFTED_UNIT_TYPES.contains(unit.getBaseType()))
                .map(UnitModel::getId)
                .map(unitId -> DraftItem.generate(DraftCategory.UNIT, unitId))
                .toList();
    }
}
