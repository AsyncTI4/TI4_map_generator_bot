package ti4.service.info;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.draft.items.FactionDraftItem;
import ti4.message.MessageHelper;
import ti4.message.componentsV2.MessageV2Builder;
import ti4.model.BreakthroughModel;
import ti4.model.FactionModel;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.franken.FrankenDraftBagService;
import ti4.service.game.MonumentsService;

@UtilityClass
public class FactionComponentsService {

    private static final Set<DraftCategory> UNIT_CATEGORIES =
            EnumSet.of(DraftCategory.UNIT, DraftCategory.MECH, DraftCategory.FLAGSHIP);

    public static void sendFactionComponents(MessageChannel channel, String factionAlias, boolean showMonument) {
        String header = "## " + new FactionDraftItem(factionAlias).getTitle(null) + " Components";
        List<List<ContainerChildComponent>> cards = cards(factionAlias, showMonument);
        if (cards.isEmpty()) {
            MessageHelper.sendMessageToChannel(channel, header + "\nNone.");
            return;
        }

        List<Color> accents = FrankenDraftBagService.getAccents();
        MessageV2Builder builder = new MessageV2Builder(channel);
        builder.append(header);
        cards.forEach(card -> appendCard(builder, accents, card));
        builder.send();
    }

    static List<List<ContainerChildComponent>> cards(String factionAlias, boolean showMonument) {
        FactionModel faction = Mapper.getFaction(factionAlias);
        if (faction == null) {
            return List.of();
        }
        Map<String, List<ExtraCards>> extrasByAnchor = extrasByAnchor(factionAlias);
        List<List<ContainerChildComponent>> cards = new ArrayList<>();
        for (DraftItem component : factionComponents(faction, new FactionDraftItem(factionAlias), showMonument)) {
            addWithExtras(cards, component, extrasByAnchor);
        }
        return cards;
    }

    static Set<String> unplacedExtraAnchors(String factionAlias) {
        FactionModel faction = Mapper.getFaction(factionAlias);
        Map<String, List<ExtraCards>> extrasByAnchor = extrasByAnchor(factionAlias);
        Set<String> anchors = new HashSet<>(extrasByAnchor.keySet());
        if (faction == null) {
            return anchors;
        }
        for (DraftItem component : factionComponents(faction, new FactionDraftItem(factionAlias), true)) {
            anchors.remove(component.getAlias());
        }
        extrasByAnchor.values().stream()
                .flatMap(List::stream)
                .flatMap(extra -> extra.components().stream())
                .forEach(component -> anchors.remove(component.getAlias()));
        return anchors;
    }

    private static Map<String, List<ExtraCards>> extrasByAnchor(String factionAlias) {
        Map<String, List<ExtraCards>> extrasByAnchor = new LinkedHashMap<>();
        FactionComponentExtrasCatalog.EXTRAS.stream()
                .filter(extras -> extras.faction().equals(factionAlias))
                .flatMap(extras -> extras.extras().stream())
                .forEach(extra -> extrasByAnchor
                        .computeIfAbsent(extra.afterCard(), anchor -> new ArrayList<>())
                        .add(extra));
        return extrasByAnchor;
    }

    private static void addWithExtras(
            List<List<ContainerChildComponent>> cards,
            DraftItem component,
            Map<String, List<ExtraCards>> extrasByAnchor) {
        cards.add(cardText(component));
        for (ExtraCards extra : extrasByAnchor.getOrDefault(component.getAlias(), List.of())) {
            extra.components().forEach(extraComponent -> addWithExtras(cards, extraComponent, extrasByAnchor));
            extra.cards().forEach(card -> cards.add(List.of(card)));
        }
    }

    private static List<DraftItem> factionComponents(
            FactionModel faction, FactionDraftItem item, boolean showMonument) {
        List<DraftItem> drafted = item.getAllComponentsIgnoringErrata();

        List<DraftItem> techs = inCategories(drafted, Set.of(DraftCategory.TECH));
        Set<String> techAliases = aliases(techs);

        List<DraftItem> units = new ArrayList<>();
        for (DraftItem unit : unitsInFactionOrder(faction, inCategories(drafted, UNIT_CATEGORIES))) {
            units.addAll(upgradeChain(unit, faction, techAliases));
        }
        UnitModel monument = showMonument ? MonumentsService.getFactionMonument(faction) : null;
        if (monument != null) {
            units.add(DraftItem.generate(DraftCategory.MONUMENT, monument.getId()));
        }
        Set<String> unitCards = aliases(units);

        List<DraftItem> components = new ArrayList<>(inCategories(drafted, Set.of(DraftCategory.ABILITY)));
        techs.stream().filter(tech -> !unitCards.contains(tech.getAlias())).forEach(components::add);
        components.addAll(units);
        Set<DraftCategory> placed = EnumSet.of(DraftCategory.ABILITY, DraftCategory.TECH);
        placed.addAll(UNIT_CATEGORIES);
        for (DraftItem component : drafted) {
            if (placed.contains(component.getItemCategory())) {
                continue;
            }
            components.add(component);
            if (isMainBreakthrough(component, faction)) {
                components.addAll(otherBreakthroughSides(faction));
            }
        }
        return components;
    }

    private static List<DraftItem> unitsInFactionOrder(FactionModel faction, List<DraftItem> listedUnits) {
        Map<String, DraftItem> listedById = new LinkedHashMap<>();
        listedUnits.forEach(unit -> listedById.put(unit.getItemId(), unit));
        List<DraftItem> units = new ArrayList<>();
        for (String unitId : faction.getUnits()) {
            DraftItem listed = listedById.remove(unitId);
            UnitModel unit = Mapper.getUnit(unitId);
            if (listed != null) {
                units.add(listed);
            } else if (unit != null && unit.getFaction().isPresent()) {
                units.add(DraftItem.generate(DraftCategory.UNIT, unitId));
            }
        }
        units.addAll(listedById.values());
        Set<String> upgradable = new HashSet<>();
        units.stream()
                .filter(unit -> nextStage(Mapper.getUnit(unit.getItemId())) != null)
                .forEach(unit -> upgradable.add(unit.getItemId()));
        units.sort(Comparator.comparing((DraftItem unit) -> !upgradable.contains(unit.getItemId())));
        return units;
    }

    private static List<DraftItem> upgradeChain(DraftItem base, FactionModel faction, Set<String> listedTechs) {
        List<DraftItem> chain = new ArrayList<>(List.of(base));
        Set<String> seen = new HashSet<>();
        UnitModel stage = nextStage(Mapper.getUnit(base.getItemId()));
        while (stage != null && seen.add(stage.getId())) {
            DraftItem card = stageCard(stage);
            if (card.getItemCategory() == DraftCategory.TECH && !isOwnTech(card, faction, listedTechs)) {
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

    private static boolean isOwnTech(DraftItem tech, FactionModel faction, Set<String> listedTechs) {
        if (listedTechs.contains(tech.getAlias())) {
            return true;
        }
        TechnologyModel model = Mapper.getTech(tech.getItemId());
        return model != null
                && model.getFaction().filter(faction.getAlias()::equals).isPresent();
    }

    private static boolean isMainBreakthrough(DraftItem component, FactionModel faction) {
        return component.getItemCategory() == DraftCategory.BREAKTHROUGH
                && component.getItemId().equals(faction.getBreakthrough());
    }

    private static List<DraftItem> otherBreakthroughSides(FactionModel faction) {
        return Mapper.getBreakthroughs().values().stream()
                .filter(bt -> bt.getFaction().filter(faction.getAlias()::equals).isPresent())
                .filter(bt -> bt.getSource() == faction.getSource())
                .filter(bt -> !bt.getAlias().equals(faction.getBreakthrough()))
                .sorted(Comparator.comparing(BreakthroughModel::getName))
                .map(bt -> DraftItem.generate(DraftCategory.BREAKTHROUGH, bt.getAlias()))
                .toList();
    }

    private static List<DraftItem> inCategories(List<DraftItem> items, Set<DraftCategory> categories) {
        return items.stream()
                .filter(item -> categories.contains(item.getItemCategory()))
                .toList();
    }

    private static Set<String> aliases(List<DraftItem> items) {
        Set<String> aliases = new HashSet<>();
        items.forEach(item -> aliases.add(item.getAlias()));
        return aliases;
    }

    private static void appendCard(
            MessageV2Builder builder, List<Color> accents, List<? extends ContainerChildComponent> card) {
        builder.append(Container.of(card).withAccentColor(accents.getFirst()));
        Collections.rotate(accents, -1);
    }

    private static List<ContainerChildComponent> cardText(DraftItem component) {
        return new ArrayList<>(component.getPlainTextDisplays(null));
    }
}
