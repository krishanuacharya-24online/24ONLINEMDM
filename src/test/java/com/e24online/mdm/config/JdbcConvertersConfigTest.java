package com.e24online.mdm.config;

import com.e24online.mdm.enums.PgObjectToStringConverter;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class JdbcConvertersConfigTest {

    @SuppressWarnings("unchecked")
    @Test
    void userConverters_containsPgObjectConverterAndConvertsValues() throws Exception {
        JdbcConvertersConfig config = new JdbcConvertersConfig();

        List<?> converters = config.userConverters();
        assertEquals(1, converters.size());
        assertSame(PgObjectToStringConverter.INSTANCE, converters.getFirst());

        Converter<PGobject, String> converter = (Converter<PGobject, String>) converters.getFirst();
        PGobject pg = new PGobject();
        pg.setType("jsonb");
        pg.setValue("{\"a\":1}");

        assertEquals("{\"a\":1}", converter.convert(pg));
        assertNull(converter.convert(null));
        assertNotNull(converter);
    }
}

