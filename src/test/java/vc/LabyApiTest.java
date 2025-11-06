package vc;

import vc.api.LabyRestClient;

public class LabyApiTest {
    private final Application app = new Application();
    private final LabyRestClient api = new LabyRestClient(app.clientHttpRequestFactory());

//    @Test
    public void test() {
        var response = api.searchProfiles("Fit");
        var a = 0;
    }
}
