package com.e24online.mdm.records;

import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public enum PgObjectToStringConverter implements Converter<PGobject, String> {
    INSTANCE;

    @Override
    public String convert(PGobject source) {
        return source != null ? source.getValue() : null;
    }
}