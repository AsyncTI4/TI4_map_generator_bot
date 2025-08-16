package ti4.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.sticker.Sticker;
import org.apache.commons.lang3.StringUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.Stickers;
import ti4.image.TileHelper;
import ti4.image.UnitTokenPosition;
import ti4.model.PlanetTypeModel.PlanetType;
import ti4.model.Source.ComponentSource;
import ti4.model.TechSpecialtyModel.TechSpecialty;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;

@Data
public class PlanetModel implements ModelInterface, EmbeddableModel {
    private String id;
    private String tileId;
    private String name;
    private String shortName;
    private Boolean shrinkName;
    private String shortNamePNAttach;
    private Boolean shrinkNamePNAttach;
    private List<String> aliases = new ArrayList<>();
    private Point positionInTile;
    private int resources;
    private int influence;
    private String factionHomeworld;
    private PlanetTypeModel.PlanetType planetType;
    private List<PlanetTypeModel.PlanetType> planetTypes;
    private String cardImagePath;
    private List<TechSpecialtyModel.TechSpecialty> techSpecialties;
    private String legendaryAbilityName;
    private String legendaryAbilityText;
    private String legendaryAbilityFlavourText;
    private String basicAbilityText;
    private String flavourText;
    private UnitTokenPosition unitPositions; // phase this out
    private PlanetLayoutModel planetLayout;
    private int spaceCannonDieCount;
    private int spaceCannonHitsOn;
    private List<String> searchTags = new ArrayList<>();
    private String contrastColor;
    private ComponentSource source;

    private String cachedStickerUrl;

    @JsonIgnore
    public boolean isValid() {
        return id != null && name != null && source != null;
    }

    @JsonIgnore
    public String getAlias() {
        return id;
    }

    public String getShortName() {
        return Optional.ofNullable(shortName).orElse(name);
    }

    public boolean getShrinkName() {
        return Optional.ofNullable(shrinkName).orElse(false);
    }

    public String getShortNamePNAttach() {
        return Optional.ofNullable(shortNamePNAttach).orElse(getShortName());
    }

    public boolean getShrinkNamePNAttach() {
        return Optional.ofNullable(shrinkNamePNAttach).orElse(getShrinkName());
    }

    @Deprecated
    public Point getPositionInTile() {
        if (positionInTile != null) return positionInTile;
        else if (planetLayout != null && planetLayout.getCenterPosition() != null) {
            return planetLayout.getCenterPosition();
        }
        return null;
    }

    @Deprecated
    public PlanetTypeModel.PlanetType getPlanetType() {
        if (planetType != null) {
            return planetType;
        }
        if (!getPlanetTypes().isEmpty()) {
            return getPlanetTypes().getFirst();
        }
        return PlanetTypeModel.PlanetType.NONE;
    }

    public List<PlanetTypeModel.PlanetType> getPlanetTypes() {
        List<PlanetType> types = new ArrayList<>();
        if (planetTypes != null) types.addAll(planetTypes);
        if (planetType != null) types.add(planetType);
        return types;
    }

    @JsonIgnore
    public String getNameNullSafe() {
        return Optional.ofNullable(name).orElse("");
    }

    @JsonIgnore
    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeAliases) {
        EmbedBuilder eb = new EmbedBuilder();

        StringBuilder sb = new StringBuilder();
        sb.append(getEmoji()).append("__").append(name).append("__");
        eb.setTitle(sb.toString());

        if (getPlanetType() != null) {
            switch (getPlanetType()) {
                case HAZARDOUS -> eb.setColor(Color.red);
                case INDUSTRIAL -> eb.setColor(Color.green);
                case CULTURAL -> eb.setColor(Color.blue);
                default -> eb.setColor(Color.white);
            }
        }

        TileModel tile = TileHelper.getTileById(tileId);
        sb = new StringBuilder();
        sb.append(getInfResEmojis()).append(getPlanetTypeEmoji()).append(getTechSpecialtyEmoji());
        if (tile != null) sb.append("\nSystem: ").append(tile.getName());
        eb.setDescription(sb.toString());
        if (basicAbilityText != null) eb.addField("Ability:", basicAbilityText, false);
        if (legendaryAbilityName != null)
            eb.addField(MiscEmojis.LegendaryPlanet + legendaryAbilityName, legendaryAbilityText, false);
        if (legendaryAbilityFlavourText != null) eb.addField("", legendaryAbilityFlavourText, false);
        if (flavourText != null) eb.addField("", flavourText, false);

        sb = new StringBuilder();
        sb.append("ID: ").append(id);
        if (includeAliases) sb.append("\nAliases: ").append(aliases);
        eb.setFooter(sb.toString());

        if (getStickerOrEmojiURL() != null) eb.setThumbnail(getStickerOrEmojiURL());

        return eb.build();
    }

    @JsonIgnore
    public MessageEmbed getLegendaryEmbed() {
        if (StringUtils.isBlank(legendaryAbilityName)) return null; // no ability name, no embed
        if (StringUtils.isBlank(legendaryAbilityText)) return null; // no ability text, no embed

        EmbedBuilder eb = new EmbedBuilder();

        eb.setTitle(MiscEmojis.LegendaryPlanet + "__" + legendaryAbilityName + "__");
        eb.setColor(Color.black);

        eb.setDescription(legendaryAbilityText);
        if (getStickerOrEmojiURL() != null) eb.setThumbnail(getStickerOrEmojiURL());
        return eb.build();
    }

    @JsonIgnore
    private String getInfResEmojis() {
        return MiscEmojis.getResourceEmoji(resources) + MiscEmojis.getInfluenceEmoji(influence);
    }

    @JsonIgnore
    private String getPlanetTypeEmoji() {
        return switch (getPlanetType()) {
            case HAZARDOUS -> ExploreEmojis.Hazardous.toString();
            case INDUSTRIAL -> ExploreEmojis.Industrial.toString();
            case CULTURAL -> ExploreEmojis.Cultural.toString();
            default -> "";
        };
    }

    @JsonIgnore
    private String getTechSpecialtyEmoji() {
        if (techSpecialties == null || techSpecialties.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (TechSpecialty techSpecialty : techSpecialties) {
            switch (techSpecialty) {
                case BIOTIC -> sb.append(TechEmojis.BioticTech);
                case CYBERNETIC -> sb.append(TechEmojis.CyberneticTech);
                case PROPULSION -> sb.append(TechEmojis.PropulsionTech);
                case WARFARE -> sb.append(TechEmojis.WarfareTech);
                case UNITSKIP -> sb.append(TechEmojis.UnitTechSkip);
                case NONUNITSKIP -> sb.append(TechEmojis.NonUnitTechSkip);
            }
        }
        return sb.toString();
    }

    @JsonIgnore
    private String getTechSpecialtyStringRepresentation() {
        if (techSpecialties == null || techSpecialties.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (TechSpecialty techSpecialty : techSpecialties) {
            switch (techSpecialty) {
                case BIOTIC -> sb.append("G");
                case CYBERNETIC -> sb.append("Y");
                case PROPULSION -> sb.append("B");
                case WARFARE -> sb.append("R");
                case UNITSKIP -> sb.append("U");
                case NONUNITSKIP -> sb.append("?");
            }
        }
        return sb.toString();
    }

    @JsonIgnore
    public boolean isLegendary() {
        return legendaryAbilityName != null;
    }

    @JsonIgnore
    public TI4Emoji getEmoji() {
        return PlanetEmojis.getPlanetEmoji(id);
    }

    @JsonIgnore
    public String getEmojiURL() {
        TI4Emoji emoji = getEmoji();
        if (getEmoji().equals(PlanetEmojis.SemLore) && !"semlore".equals(getId())) {
            return null;
        }
        if (emoji.asEmoji() instanceof CustomEmoji customEmoji) {
            return customEmoji.getImageUrl();
        }
        return null;
    }

    @JsonIgnore
    public String getStickerOrEmojiURL() {
        if (cachedStickerUrl != null) {
            return cachedStickerUrl;
        }

        long ident = Stickers.getPlanetSticker(id);
        if (ident == -1) {
            cachedStickerUrl = getEmojiURL();
        } else {
            cachedStickerUrl = AsyncTI4DiscordBot.jda
                    .retrieveSticker(Sticker.fromId(ident))
                    .complete()
                    .getIconUrl();
        }

        return cachedStickerUrl;
    }

    public boolean search(String searchString) {
        return name.toLowerCase().contains(searchString)
                || id.toLowerCase().contains(searchString)
                || source.toString().contains(searchString)
                || searchTags.contains(searchString);
    }

    @JsonIgnore
    public String getAutoCompleteName() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" (").append(resources).append("/").append(influence);
        if (!getTechSpecialtyStringRepresentation().isBlank())
            sb.append(" ").append(getTechSpecialtyStringRepresentation());
        sb.append(")");
        return sb.toString();
    }
}
