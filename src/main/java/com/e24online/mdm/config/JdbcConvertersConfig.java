package com.e24online.mdm.config;

import org.jspecify.annotations.NonNull;
import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

import java.util.List;

@Configuration
@Profile("!aot")
public class JdbcConvertersConfig extends AbstractJdbcConfiguration {

    @Override
    protected @NonNull List<?> userConverters() {
        return List.of(PgObjectToStringConverter.INSTANCE);
    }

    @ReadingConverter
    enum PgObjectToStringConverter implements Converter<PGobject, String> {
        INSTANCE;

        @Override
        public String convert(PGobject source) {
            return source != null ? source.getValue() : null;
        }
    }
}

