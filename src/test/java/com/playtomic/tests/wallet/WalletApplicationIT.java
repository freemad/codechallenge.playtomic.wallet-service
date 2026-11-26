package com.playtomic.tests.wallet;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

/** Checks that the legacy wallet application context starts. */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class WalletApplicationIT {

	/** Checks that the legacy Spring application context loads. */
	@Test
	public void emptyTest() {
	}
}
