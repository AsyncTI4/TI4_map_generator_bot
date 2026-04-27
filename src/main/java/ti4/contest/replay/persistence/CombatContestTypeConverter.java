package ti4.contest.replay.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ti4.contest.replay.core.CombatContestType;

@Converter(autoApply = true)
public class CombatContestTypeConverter implements AttributeConverter<CombatContestType, String> {

    @Override
    public String convertToDatabaseColumn(CombatContestType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public CombatContestType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CombatContestType.valueOf(dbData);
    }
}
