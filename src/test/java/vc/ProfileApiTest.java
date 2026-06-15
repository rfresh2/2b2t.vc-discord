package vc;

import vc.api.crafthead.CraftheadRestClient;
import vc.api.mcprofile.MCProfileRestClient;
import vc.api.model.ProfileData;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProfileApiTest {

    private final Application app = new Application();

    private final CraftheadRestClient craftHead = new CraftheadRestClient(app.clientHttpRequestFactory());

    private final MCProfileRestClient mcProfile = new MCProfileRestClient(app.clientHttpRequestFactory());

//    @Test
    public void testGetProfile() {
        ProfileData rfresh2 = craftHead.getProfile("rfresh2");

        assertEquals("rfresh2", rfresh2.name());
        assertEquals(UUID.fromString("572e683c-888a-4a0d-bc10-5d9cfa76d892"), rfresh2.uuid());
    }

//    @Test
    public void testMCProfile() {
        ProfileData rfresh2 = mcProfile.getProfile("rfresh2");

        assertEquals("rfresh2", rfresh2.name());
        assertEquals(UUID.fromString("572e683c-888a-4a0d-bc10-5d9cfa76d892"), rfresh2.uuid());
    }
}
