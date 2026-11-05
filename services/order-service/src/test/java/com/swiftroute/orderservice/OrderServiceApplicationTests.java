package com.swiftroute.orderservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_ORDER_SERVICE_INTEGRATION_TESTS", matches = "true")
class OrderServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
