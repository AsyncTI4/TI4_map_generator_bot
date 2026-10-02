package ti4.service.franken;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.draft.DraftCategory;
import ti4.game.Game;
import ti4.image.Mapper;
import ti4.model.AbilityModel;
import ti4.model.BreakthroughModel;
import ti4.model.DraftErrataModel;
import ti4.model.LeaderModel;
import ti4.model.PromissoryNoteModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;

@UtilityClass
public class FrankenAlternateTextService {

    public static String getAlternateText(DraftCategory category, String itemId) {
        DraftErrataModel errata = Mapper.getFrankenErrata(category + ":" + itemId);
        return errata == null ? "" : errata.getAlternateText().trim();
    }

    public static boolean hasAlternateText(Game game, DraftCategory category, String itemId) {
        return game != null
                && game.isFrankenGame()
                && !getAlternateText(category, itemId).isBlank();
    }

    public static String formatInlineText(String text) {
        return text.replace("\n> ", "\n").replace("\n", "\n> ");
    }

    public static String getRepresentationWithAlternateText(
            Game game,
            DraftCategory category,
            String itemId,
            String titleRepresentation,
            String defaultRepresentation) {
        if (!hasAlternateText(game, category, itemId)) {
            return defaultRepresentation;
        }
        return titleRepresentation + "\n> " + formatInlineText(getAlternateText(category, itemId));
    }

    public static MessageEmbed getAbilityEmbed(Game game, AbilityModel model) {
        MessageEmbed embed = model.getRepresentationEmbed();
        if (!hasAlternateText(game, DraftCategory.ABILITY, model.getAlias())) {
            return embed;
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.copyFrom(embed);
        eb.setDescription(getAlternateText(DraftCategory.ABILITY, model.getAlias()));
        return eb.build();
    }

    public static MessageEmbed getTechnologyEmbed(
            Game game, TechnologyModel model, boolean includeID, boolean includeRequirements) {
        MessageEmbed embed = model.getRepresentationEmbed(includeID, includeRequirements);
        if (!hasAlternateText(game, DraftCategory.TECH, model.getAlias())) {
            return embed;
        }

        StringBuilder description = new StringBuilder();
        if (includeRequirements) {
            description
                    .append("*Requirements: ")
                    .append(model.getRequirementsEmoji())
                    .append("*\n");
        }
        description.append(getAlternateText(DraftCategory.TECH, model.getAlias()));

        EmbedBuilder eb = new EmbedBuilder();
        eb.copyFrom(embed);
        eb.setDescription(description.toString());
        return eb.build();
    }

    public static MessageEmbed getBreakthroughEmbed(Game game, BreakthroughModel model, boolean includeID) {
        MessageEmbed embed = model.getRepresentationEmbed(includeID);
        if (!hasAlternateText(game, DraftCategory.BREAKTHROUGH, model.getAlias())) {
            return embed;
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.copyFrom(embed);
        eb.setDescription("SYNERGY: " + model.getSynergyEmojis() + "\n"
                + getAlternateText(DraftCategory.BREAKTHROUGH, model.getAlias()));
        return eb.build();
    }

    public static MessageEmbed getUnitEmbed(Game game, UnitModel model, boolean includeAliases) {
        MessageEmbed embed = model.getRepresentationEmbed(includeAliases);
        DraftCategory category = getUnitCategory(model.getAlias());
        if (category == null || !hasAlternateText(game, category, model.getAlias())) {
            return embed;
        }

        List<MessageEmbed.Field> existingFields = embed.getFields();
        EmbedBuilder eb = new EmbedBuilder();
        eb.copyFrom(embed);
        eb.clearFields();

        int abilityFieldIndex = findUnitAbilityFieldIndex(existingFields, model);
        for (int i = 0; i < existingFields.size(); i++) {
            MessageEmbed.Field field = existingFields.get(i);
            if (i == abilityFieldIndex) {
                eb.addField(field.getName(), getAlternateText(category, model.getAlias()), field.isInline());
            } else {
                eb.addField(field.getName(), field.getValue(), field.isInline());
            }
        }

        if (abilityFieldIndex < 0) {
            eb.addField("Ability:", getAlternateText(category, model.getAlias()), false);
        }
        return eb.build();
    }

    private static int findUnitAbilityFieldIndex(List<MessageEmbed.Field> fields, UnitModel model) {
        String abilityText = model.getAbility().orElse("");
        String abilityTextWithNotes =
                model.getNotes() == null ? abilityText : abilityText + "\n-# [" + model.getNotes() + "]";

        for (int i = 0; i < fields.size(); i++) {
            MessageEmbed.Field field = fields.get(i);
            String fieldName = field.getName();
            String fieldValue = field.getValue();

            if (fieldName != null && fieldName.toLowerCase().contains("ability")) {
                return i;
            }
            if (fieldValue == null) {
                continue;
            }
            if (!abilityText.isBlank() && (fieldValue.equals(abilityText) || fieldValue.startsWith(abilityText))) {
                return i;
            }
            if (!abilityTextWithNotes.isBlank() && fieldValue.equals(abilityTextWithNotes)) {
                return i;
            }
        }
        return -1;
    }

    public static MessageEmbed getLeaderEmbed(
            Game game,
            LeaderModel model,
            boolean includeID,
            boolean includeFactionType,
            boolean showUnlockConditions,
            boolean includeFlavourText,
            boolean useTwilightsFallText) {
        MessageEmbed embed = model.getRepresentationEmbed(
                includeID, includeFactionType, showUnlockConditions, includeFlavourText, useTwilightsFallText);
        DraftCategory category = getLeaderCategory(model);
        if (category == null || !hasAlternateText(game, category, model.getAlias())) {
            return embed;
        }

        List<MessageEmbed.Field> fields = embed.getFields();
        if (fields.isEmpty()) {
            return embed;
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.copyFrom(embed);
        eb.clearFields();
        MessageEmbed.Field firstField = fields.getFirst();
        eb.addField(firstField.getName(), getAlternateText(category, model.getAlias()), firstField.isInline());
        for (int i = 1; i < fields.size(); i++) {
            MessageEmbed.Field field = fields.get(i);
            eb.addField(field.getName(), field.getValue(), field.isInline());
        }
        return eb.build();
    }

    public static MessageEmbed getPromissoryNoteEmbed(
            Game game, PromissoryNoteModel model, boolean justShowName, boolean includeID, boolean includeHelpfulText) {
        MessageEmbed embed = model.getRepresentationEmbed(justShowName, includeID, includeHelpfulText);
        if (justShowName || !hasAlternateText(game, DraftCategory.PN, model.getAlias())) {
            return embed;
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.copyFrom(embed);
        eb.setDescription(getAlternateText(DraftCategory.PN, model.getAlias()));
        return eb.build();
    }

    public static DraftCategory getLeaderCategory(LeaderModel model) {
        return switch (model.getType()) {
            case "agent" -> DraftCategory.AGENT;
            case "commander" -> DraftCategory.COMMANDER;
            case "hero" -> DraftCategory.HERO;
            default -> null;
        };
    }

    public static DraftCategory getUnitCategory(String unitId) {
        for (DraftCategory category : List.of(DraftCategory.FLAGSHIP, DraftCategory.MECH, DraftCategory.UNIT)) {
            if (Mapper.getFrankenErrata(category + ":" + unitId) != null) {
                return category;
            }
        }
        return null;
    }
}
