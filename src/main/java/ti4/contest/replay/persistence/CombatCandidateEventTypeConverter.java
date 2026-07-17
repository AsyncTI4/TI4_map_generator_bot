package ti4.contest.replay.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ti4.contest.replay.core.CombatCandidateEventType;

@Converter(autoApply = true)
public class CombatCandidateEventTypeConverter implements AttributeConverter<CombatCandidateEventType, String> {

    @Override
    public String convertToDatabaseColumn(CombatCandidateEventType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public CombatCandidateEventType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CombatCandidateEventType.valueOf(dbData);
    }
}
