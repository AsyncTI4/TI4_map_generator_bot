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
import java.util.function.Function;
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
import ti4.service.game.MonumentsService;

@UtilityClass
public class FactionComponentsService {

    private static final Set<DraftCategory> UNIT_CATEGORIES =
            EnumSet.of(DraftCategory.UNIT, DraftCategory.MECH, DraftCategory.FLAGSHIP);

    public static void sendFactionComponents(MessageChannel channel, String factionAlias, boolean showMonument) {
        FactionDraftItem item = new FactionDraftItem(factionAlias);
        String header = "## " + item.getTitle(null) + " Components";
        FactionModel faction = Mapper.getFaction(factionAlias);
        List<DraftItem> components = faction == null ? List.of() : factionComponents(faction, item, showMonument);
        if (components.isEmpty()) {
            MessageHelper.sendMessageToChannel(channel, header + "\nNone.");
            return;
        }

        List<Color> accents = FrankenDraftBagService.getAccents();
        MessageV2Builder builder = new MessageV2Builder(channel);
        builder.append(header);
        for (DraftItem component : components) {
            appendCard(builder, accents, cardText(component));
            if (isMainBreakthrough(component, faction)) {
                extraComponentCards(faction).forEach(card -> appendCard(builder, accents, List.of(card)));
            }
        }
        builder.send();
    }

    private static List<DraftItem> factionComponents(
            FactionModel faction, FactionDraftItem item, boolean showMonument) {
        List<DraftItem> drafted = item.getAllComponentsIgnoringErrata();

        List<DraftItem> techs = new ArrayList<>(inCategories(drafted, Set.of(DraftCategory.TECH)));
        techs.addAll(techsOutsideTechList(faction));
        Set<String> techAliases = aliases(techs);

        List<DraftItem> units = new ArrayList<>();
        for (DraftItem unit : unitsInFactionOrder(faction, inCategories(drafted, UNIT_CATEGORIES))) {
            units.addAll(upgradeChain(unit, techAliases));
        }
        UnitModel monument = showMonument ? MonumentsService.getFactionMonument(faction) : null;
        if (monument != null) {
            units.add(DraftItem.generate(DraftCategory.MONUMENT, monument.getId()));
        }
        Set<String> unitCards = aliases(units);

        List<DraftItem> components = new ArrayList<>(inCategories(drafted, Set.of(DraftCategory.ABILITY)));
        components.addAll(abilityCardsOutsideAbilityList(faction));
        techs.stream().filter(tech -> !unitCards.contains(tech.getAlias())).forEach(components::add);
        components.addAll(units);
        Set<DraftCategory> placed = EnumSet.of(DraftCategory.ABILITY, DraftCategory.TECH);
        placed.addAll(UNIT_CATEGORIES);
        drafted.stream()
                .filter(component -> !placed.contains(component.getItemCategory()))
                .forEach(components::add);
        components.addAll(breakthroughsOutsideMain(faction));
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
        units.sort(Comparator.comparing((DraftItem unit) -> nextStage(Mapper.getUnit(unit.getItemId())) == null));
        return units;
    }

    private static List<DraftItem> upgradeChain(DraftItem base, Set<String> ownTechs) {
        List<DraftItem> chain = new ArrayList<>(List.of(base));
        Set<String> seen = new HashSet<>();
        UnitModel stage = nextStage(Mapper.getUnit(base.getItemId()));
        while (stage != null && seen.add(stage.getId())) {
            DraftItem card = stageCard(stage);
            if (card.getItemCategory() == DraftCategory.TECH && !ownTechs.contains(card.getAlias())) {
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

    private static List<DraftItem> abilityCardsOutsideAbilityList(FactionModel faction) {
        return Mapper.getAbilities().values().stream()
                .filter(ability -> faction.getAlias().equals(ability.getFaction()))
                .filter(ability -> ability.getSource() == faction.getSource())
                .filter(ability -> !faction.getAbilities().contains(ability.getId()))
                .sorted(Comparator.comparing(AbilityModel::getName))
                .map(ability -> DraftItem.generate(DraftCategory.ABILITY, ability.getId()))
                .toList();
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

    private static boolean isMainBreakthrough(DraftItem component, FactionModel faction) {
        return component.getItemCategory() == DraftCategory.BREAKTHROUGH
                && component.getItemId().equals(faction.getBreakthrough());
    }

    private static List<TextDisplay> extraComponentCards(FactionModel faction) {
        if ("arvaxibt".equals(faction.getBreakthrough())) {
            return List.of(ArvaxiBreakthroughHandler.mobilizationEngineCard());
        }
        return List.of();
    }

    private static void appendCard(
            MessageV2Builder builder, List<Color> accents, List<? extends ContainerChildComponent> card) {
        builder.append(Container.of(card).withAccentColor(accents.getFirst()));
        Collections.rotate(accents, -1);
    }

    private static List<ContainerChildComponent> cardText(DraftItem component) {
        List<ContainerChildComponent> card = new ArrayList<>(cardBody(component));
        String notes = notesOf(component);
        if (notes != null && !notes.isBlank()) {
            card.add(TextDisplay.of("-# [" + notes.trim().replace("\n", "\n-# ") + "]"));
        }
        return card;
    }

    private static List<TextDisplay> cardBody(DraftItem component) {
        UnitModel unit =
                component.getItemCategory() == DraftCategory.UNIT ? Mapper.getUnit(component.getItemId()) : null;
        if (unit == null) {
            return component.getPlainTextDisplays(null);
        }
        String body = UnitDraftItem.rulesText(unit).trim().replace("\n", "\n> ");
        return List.of(TextDisplay.of(component.getTitle(null) + System.lineSeparator() + "> " + body));
    }

    private static String notesOf(DraftItem component) {
        String id = component.getItemId();
        return switch (component.getItemCategory()) {
            case ABILITY -> notes(Mapper.getAbility(id), AbilityModel::getNotes);
            case TECH -> notes(Mapper.getTech(id), TechnologyModel::getNotes);
            case AGENT, COMMANDER, HERO -> notes(Mapper.getLeader(id), LeaderModel::getNotes);
            case MECH, FLAGSHIP, UNIT, MONUMENT -> notes(Mapper.getUnit(id), UnitModel::getNotes);
            case PN -> notes(Mapper.getPromissoryNote(id), PromissoryNoteModel::getNotes);
            case BREAKTHROUGH -> notes(Mapper.getBreakthrough(id), BreakthroughModel::getNotes);
            default -> null;
        };
    }

    private static <T> String notes(T model, Function<T, String> getNotes) {
        return model == null ? null : getNotes.apply(model);
    }
}
