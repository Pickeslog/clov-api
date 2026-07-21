package com.korit.clovapi;

import com.korit.clovapi.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimezoneIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void databaseSessionUsesUtc() {
        assertEquals("+00:00", jdbcTemplate.queryForObject("SELECT @@session.time_zone", String.class));
    }
}
