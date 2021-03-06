package nl.vpro.poms.poms;

import io.restassured.RestAssured;
import lombok.extern.log4j.Log4j2;

import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static nl.vpro.api.client.utils.Config.Prefix.poms;
import static nl.vpro.poms.AbstractApiTest.CONFIG;
import static nl.vpro.poms.AbstractApiMediaBackendTest.MID;
import static org.assertj.core.api.Assertions.assertThat;


/**
 * On the POMS GUI environment itself there is an /import endpoint that is currently used by NEP to drop some things that used to be coming to use via the file system.
 *
 * It is actually a generic entry to several camel routes.
 *
 * @author Michiel Meeuwissen
 */
@Log4j2
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LetterBoxTest {

    private static final String IMPORT_URL = CONFIG.url(poms, "import/");
    private static final String USERNAME = CONFIG.configOption(poms, "letterbox-user").orElse("vpro-cms");
    private static final String PASSWORD = CONFIG.requiredOption(poms, "letterbox-password");

    @BeforeEach
    void setUp() {
        RestAssured.useRelaxedHTTPSValidation();
        RestAssured.urlEncodingEnabled = false;
    }
    private static String nepEndpoint = IMPORT_URL + "nep";

    private static String projectm = null;


    /**
     * Checks whether the call at root of the controller show a list of the available entries.
     */
    @BeforeAll
    static void getList() {

        String s =
            given()
                .auth().basic(USERNAME, PASSWORD)
                .log().ifValidationFails()
                .when()
                .  get(IMPORT_URL)
                .then()
                .  log().ifError()
                .  statusCode(200)
                .  extract().asString();

        String[] split = s.split("\n");
        for (String e : split) {
            String endpoint = e.split("\t", 2)[0];
            log.info(endpoint);
            if (endpoint.endsWith("/import/nep")) {
                nepEndpoint = endpoint;
            }
            if (endpoint.endsWith("/import/projectmauthority.restriction")) {
                projectm = endpoint;
            }
        }
        assertThat(nepEndpoint).isNotNull();
        assertThat(projectm).isNotNull();

    }

    /**
     * Tests whether indeed we are denied if we don't provide the correct password
     */
    @Test
    @Order(1)
    @Tag("nep")
    void security() {
        log.info("{}", USERNAME);
        String result = given()
            .auth()
            .  basic(USERNAME, "WRONG PASSWORD")
            .log()
            .  ifValidationFails()
            .when()
            .  body("<notify drm=\"false\"\n" +
                "        type=\"ONLINE\"\n" +
                "        mid=\"" + MID + "\" timestamp=\"2017-04-21T16:09:19\" xmlns=\"urn:vpro:media:notify:2017\" />")
            .  contentType("application/xml")
            .  post(nepEndpoint)
            . then()
            .    log().all()
            .    statusCode(401)
            .     extract()
            .  asString();

        log.info("{}", result);
    }

    /**
     * Whether notifies on /import/nep are accepted
     */
    @Test
    @Order(2)
    @Tag("nep")
    void postToNEP() {
        String result = given()
            .auth()
            .  basic(USERNAME, PASSWORD)
            .log().ifValidationFails()
            .when()
            .  body("<notify drm=\"false\"\n" +
                "        type=\"ONLINE\"\n" +
                "        mid=\"" + MID + "\" timestamp=\"2017-04-21T16:09:19\" xmlns=\"urn:vpro:media:notify:2017\" />")
            .  contentType("application/xml")
            .  post(nepEndpoint)
            . then()
            .    log().all()
            .    statusCode(200)
            .     extract()
            .  asString();

        log.info("result: {}", result);
    }

    /**
     * Whether errornous XML's are indeed rejected
     */
    @Test
    @Order(3)
    @Tag("nep")
    void postToNEPErrorneous() {
        String result = given()
            .auth()
            .  basic(USERNAME, PASSWORD)
            .log().ifValidationFails()
            .when()
            .  body("<notify drm=\"XXX\"\n" + // we don't understand this. A bad request will be issued
                "        type=\"ONLINE\"\n" +
                "        mid=\"" + MID + "\" timestamp=\"2017-04-21T16:09:19\" xmlns=\"urn:vpro:media:notify:2017\" />")
            .  contentType("application/xml")
            .  post(nepEndpoint)
            . then()
            .    log().all()
            .    statusCode(400)
            .     extract()
            .  asString();

        log.info("result: {}", result);
    }

    /**
     * Whether restrictions are accepted. (See also  MSE-4666)
     */
    @Test
    @Order(4)
    @Tag("restriction")
    void postRestriction() {
        String result = given()
            .auth()
            .  basic(USERNAME, PASSWORD)
            .log().ifValidationFails()
            .when()
            .  body("<?xml version=\"1.0\" encoding=\"utf-8\"?><restriction timestamp=\"2020-01-14T08:30:29\"><prid>" + MID + "</prid><pridexport>" + MID + "</pridexport><titel>Goedemorgen Nederland</titel><platform>internetvod</platform><encryptie><encryptielabel>DRM</encryptielabel></encryptie><tijdsbeperking><starttijd>2000-01-01T01:00:00</starttijd><eindtijd>2101-01-01T00:59:00</eindtijd></tijdsbeperking><omroepen><omroep>WNL</omroep></omroepen></restriction>")
            .  contentType("application/xml")
            .  post(projectm)
            . then()
            .    log().all()
            .    statusCode(200)
            .     extract()
            .  asString();
        log.info("Result {}", result);
    }

}
