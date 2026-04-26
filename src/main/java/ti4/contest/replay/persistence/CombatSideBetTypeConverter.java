package ti4.contest.replay.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ti4.contest.replay.core.CombatSideBetType;

@Converter(autoApply = true)
public class CombatSideBetTypeConverter implements AttributeConverter<CombatSideBetType, String> {

    @Override
    public String convertToDatabaseColumn(CombatSideBetType attribute) {
        return attribute == null ? null : attribute.key();
    }

    @Override
    public CombatSideBetType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CombatSideBetType.fromKey(dbData);
    }
}
