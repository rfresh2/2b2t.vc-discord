package vc;

import vc.api.CraftheadRestClient;
import vc.api.MinetoolsRestClient;
import vc.api.model.ProfileData;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CraftheadApiTest {

    private final Application app = new Application();

    private final CraftheadRestClient api = new CraftheadRestClient(app.clientHttpRequestFactory());

    private final MinetoolsRestClient api2 = new MinetoolsRestClient(app.clientHttpRequestFactory());

//    @Test
    public void testGetProfile() {
        ProfileData rfresh2 = api.getProfile("rfresh2");

        assertEquals("rfresh2", rfresh2.name());
        assertEquals(UUID.fromString("572e683c-888a-4a0d-bc10-5d9cfa76d892"), rfresh2.uuid());
    }

//    @Test
    public void testMinetools() {
        ProfileData rfresh2 = api2.getProfileFromUsername("rfresh2");

        assertEquals("rfresh2", rfresh2.name());
        assertEquals(UUID.fromString("572e683c-888a-4a0d-bc10-5d9cfa76d892"), rfresh2.uuid());
    }
}
