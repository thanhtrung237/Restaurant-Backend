package com.group.restaurant.enums;

import jakarta.persistence.AttributeConverter;
import java.util.stream.Stream;

public abstract class AbstractEnumConverter<E extends Enum<E> & BaseEnum<E>>
        implements AttributeConverter<E, Integer> {
    private final Class<E> clazz;

    protected AbstractEnumConverter(Class<E> clazz) {
        this.clazz = clazz;
    }

    @Override
    public Integer convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public E convertToEntityAttribute(Integer dbData) {
        if (dbData == null) return null;
        return Stream.of(clazz.getEnumConstants())
                .filter(e -> e.getValue() == dbData)
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }
}
