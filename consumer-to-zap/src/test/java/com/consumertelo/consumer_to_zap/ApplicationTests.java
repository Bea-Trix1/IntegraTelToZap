package com.consumertelo.consumer_to_zap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"twilio.auth.token=test-auth-token",
		"twilio.account.sid=ACtest",
		"app.security.api-key=test-api-key"
})
class ApplicationTests {

	@Test
	void contextLoads() {
	}

}
