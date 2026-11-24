package com.cineconecta.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

// The full context needs both databases: skip (instead of failing the build) without credentials.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ORACLE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "MONGO_URI", matches = ".+")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
