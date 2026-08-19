package org.rentez.paymentservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
