package com.e24online.mdm.config;

import com.e24online.mdm.enums.PgObjectToStringConverter;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

import java.util.List;

@Configuration
@Profile("!aot")
public class JdbcConvertersConfig extends AbstractJdbcConfiguration {

    @Override
    protected @NonNull List<?> userConverters() {
        return List.of(PgObjectToStringConverter.INSTANCE);
    }

}

