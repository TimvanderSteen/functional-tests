package nl.vpro.poms.backend;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import lombok.extern.log4j.Log4j2;

import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.*;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.JAXB;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import nl.vpro.domain.media.*;
import nl.vpro.domain.media.search.*;
import nl.vpro.domain.media.update.ProgramUpdate;
import nl.vpro.domain.media.update.SegmentUpdate;
import nl.vpro.junit.extensions.AllowUnavailable;
import nl.vpro.junit.extensions.TestMDC;
import nl.vpro.testutils.Utils;

import static io.restassured.RestAssured.given;
import static nl.vpro.api.client.utils.Config.Prefix.npo_backend_api;
import static nl.vpro.domain.Xmlns.NAMESPACE_CONTEXT;
import static nl.vpro.poms.AbstractApiMediaBackendTest.MID;
import static nl.vpro.poms.AbstractApiTest.CONFIG;
import static nl.vpro.rs.media.MediaBackendRestService.ERRORS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.hamcrest.Matchers.*;



/*
 * 2018-08-17:
 * 5.9-SNAPSHOT @ dev : allemaal ok
 */

/**
 * Basic tests which do not even use our client.
 *
 * @author Daan Debie
 * @author Michiel Meeuwissen
 */
@TestMethodOrder(MethodOrderer.Alphanumeric.class)
@Log4j2
@ExtendWith({AllowUnavailable.class, TestMDC.class})
public class MediaTest {

    private static final Instant NOW = Instant.now();
    private static final LocalDate TODAY = NOW.atZone(Schedule.ZONE_ID).toLocalDate();
    private static final LocalDateTime TIME = NOW.atZone(Schedule.ZONE_ID).toLocalDateTime();
    private static final Duration ACCEPTABLE = Duration.ofMinutes(5);

    private static final String MEDIA_URL = CONFIG.url(npo_backend_api, "media/media");
    private static final String FIND_URL = CONFIG.url(npo_backend_api, "media/find");
    private static final String USERNAME = CONFIG.requiredOption(npo_backend_api, "user");
    private static final String PASSWORD = CONFIG.requiredOption(npo_backend_api, "password");
    private static final String ERRORS_EMAIL = CONFIG.configOption(npo_backend_api, "errors_email").orElse("digitaal-techniek@vpro.nl");
    private static final String BASE_CRID = "crid://apitests";
    private static final String TITLE_PREFIX = "API_FUNCTIONAL_TEST_";
    private static String dynamicSuffix;
    private static String cridIdFromSuffix;
    private static String clipMid;

    @BeforeAll
    public static void setUpShared() {
        dynamicSuffix = TIME.toString();
        cridIdFromSuffix = dynamicSuffix.replaceAll("\\D", "");
    }

    @BeforeEach
    public void setUp() {
        RestAssured.useRelaxedHTTPSValidation();
        RestAssured.urlEncodingEnabled = false;
    }

    @Test
    @Tag("clip")
    @Tag("clips")
    public void test01PostClip() {
        List<Segment> segments = Collections.singletonList(createSegment(null, dynamicSuffix, null));
        ProgramUpdate clip =
            ProgramUpdate.create(
                createClip(null, dynamicSuffix, segments));

        clipMid = given()
            .auth().basic(USERNAME, PASSWORD)
            .contentType(ContentType.XML)
            .body(clip)
            .queryParam("errors", ERRORS_EMAIL)
            .log().all()
            .when()
            .  post(MEDIA_URL)
            .then()
            .  log().all()
            .  statusCode(202)
            .  body(startsWith("POMS_VPRO"))
            .  extract().asString();
    }

    @Test
    @Tag("cridclip")
    @Tag("clips")
    public void test02PostClipWithCrid() {
        String clipCrid = clipCrid(cridIdFromSuffix);
        List<Segment> segments = Collections.singletonList(createSegment(null, dynamicSuffix, null));
        ProgramUpdate clip = ProgramUpdate.create(createClip(clipCrid, dynamicSuffix, segments));
        log.info("Created clip with crid {}", clipCrid);

        given()
            .auth().basic(USERNAME, PASSWORD)
            .contentType(ContentType.XML)
            .body(clip)
            .queryParam("errors", ERRORS_EMAIL)
            .log().all()
            .when()
            .  post(MEDIA_URL)
            .then()
            .  log().all()
            .  statusCode(202)
            .  body(equalTo(clipCrid));
    }

    @Test
    @Tag("segment")
    public void test03PostSegment() {
        SegmentUpdate segment = SegmentUpdate.create(createSegment(null, dynamicSuffix, clipMid));

        given()
            .  auth().basic(USERNAME, PASSWORD)
            .  contentType(ContentType.XML)
            .  body(segment)
            .  queryParam(ERRORS, ERRORS_EMAIL)
            .  log().all()
            .when()
            .  post(MEDIA_URL)
            .then()
            .  log().all()
            .  statusCode(202)
            .  body(startsWith("POMS_VPRO"));
    }
    @Test
    @Tag("clip")
    @Tag("clips")
    public void test05RetrieveClip() {
        assumeThat(clipMid).isNotNull();
        Boolean result = Utils.waitUntil(ACCEPTABLE, () -> {
            try {
                given()
                    .auth().basic(USERNAME, PASSWORD)
                    .contentType("application/xml")
                    .queryParam(ERRORS, ERRORS_EMAIL)
                    .log().all()
                    .when()
                    .get(MEDIA_URL + "/" + clipMid)
                    .then()
                    .log().all()
                    .statusCode(200)
                    .body(hasXPath(
                        "/u:program/u:title[@type='MAIN']/text()", NAMESPACE_CONTEXT,
                        equalTo(TITLE_PREFIX + dynamicSuffix)
                    ))
                    .body(hasXPath("/u:program/@deleted", NAMESPACE_CONTEXT, emptyOrNullString()))
                ;
                return Boolean.TRUE;
            } catch (AssertionError ae) {
                log.info(ae.getMessage());
                return null;
            }
        },
            Utils.Check.<Boolean>builder()
                .predicate(r -> r)
                .description("Getting from api returns 200")
                .build()
        );

        assertThat(result).isTrue();
    }


    @Test
    @Tag("cridclip")
    @Tag("clips")
    public void test06RetrieveClipWithCrid() throws UnsupportedEncodingException {

        String clipCrid = clipCrid(cridIdFromSuffix);
        log.info("Retrieving clip with crid {}", clipCrid);
        String encodedClipCrid = URLEncoder.encode(clipCrid, "UTF-8");
        given()
            .auth().basic(USERNAME, PASSWORD)
            .contentType("application/xml")
            .queryParam(ERRORS, ERRORS_EMAIL)
            .log().all()
            .when()
            .  get(MEDIA_URL + "/" + encodedClipCrid)
            .then()
            .  log().all()
            .  statusCode(200)
            .  body(hasXPath("/u:program/u:title[@type='MAIN']/text()",
                NAMESPACE_CONTEXT, equalTo(TITLE_PREFIX + dynamicSuffix)))
            .  body(hasXPath("/u:program/@deleted", NAMESPACE_CONTEXT, emptyOrNullString()));
    }

    @Test
    @Tag("clips")
    public void test07FindClips() {
        MediaForm search = MediaForm.builder()
            .pager(MediaPager.builder().max(50).build())
            .broadcaster("VPRO")
            .creationRange(
                new InstantRange(TODAY.atStartOfDay(), TODAY.atStartOfDay().plusHours(24))
            )
            .build();

        TitleForm form = new TitleForm(TITLE_PREFIX + dynamicSuffix, false);
        search.addTitle(form);
        given()
            .auth().basic(USERNAME, PASSWORD)
            .contentType(ContentType.XML)
            .body(search)
            .log().all()
            .when()
            .  post(FIND_URL)
            .then()
            .  log().all()
            .  statusCode(200)
            .  body(hasXPath("/s:list/@totalCount", NAMESPACE_CONTEXT, equalTo("2")))

        ;
    }

    @Test
    @Tag("clip")
    public void test08DeleteClip() {
        given()
            .auth().basic(USERNAME, PASSWORD)
            .queryParam(ERRORS, ERRORS_EMAIL)
            .log().all()
            .when()
            .  delete(MEDIA_URL + "/" + clipMid)
            .then()
            .  log().all()
            .  statusCode(202);


        Utils.waitUntil(ACCEPTABLE, () -> {
            try {
                given()
                    .auth()
                    .basic(USERNAME, PASSWORD)
                    .contentType(ContentType.XML)
                    .queryParam(ERRORS, ERRORS_EMAIL)
                    .log().all()
                    .when()
                    .get(MEDIA_URL + "/" + clipMid)
                    .then()
                    .log().all()
                    .statusCode(200)
                    .body(hasXPath("/u:program/@deleted", NAMESPACE_CONTEXT, equalTo("true")));
                return false;
            } catch (AssertionError ae) {
                log.info(ae.getMessage());
                return false;
            }
        });
    }


    @Test
    public void test10Retrieve404() {
        log.info(given()
            .auth().basic(USERNAME, PASSWORD)
            .log().all()
            .when()
            .  get(MEDIA_URL + "/BESTAATNIET")
            .then()
            .  log().all()
            .statusCode(404)
            .extract()
            .asString());
    }

    /**
     * See also MSE-4676
     */
    @Test
    public void test20StreamingStatus() {
        String streamingStatusEndpoint = CONFIG.url(npo_backend_api, "media/streamingstatus");
        String result = given()
            .auth().basic(USERNAME, PASSWORD)
            .log().all()
            .when()
            .  get(streamingStatusEndpoint + "/" + MID)
            .then()
            .  log().all()
            .statusCode(200)
            .contentType(ContentType.XML)
            .extract()
            .asString();



        StreamingStatus streamingStatus = JAXB.unmarshal(new StringReader(result), StreamingStatusImpl.class);
        log.info("{} -> {}", result, streamingStatus);
        assertThat(streamingStatus).isNotNull();
    }


    private Program createClip(String crid, String dynamicSuffix, List<Segment> segments) {

        return MediaTestDataBuilder.program()
            .validNew()
            .crids(crid)
            .clearBroadcasters()
            .broadcasters("VPRO")
            .type(ProgramType.CLIP)
            .segments(segments)
            .ageRating(AgeRating.ALL)
            .title(TITLE_PREFIX + dynamicSuffix)
            .build();
    }



    private Segment createSegment(String crid, String dynamicSuffix, String midRef) {
        return MediaTestDataBuilder.segment()
            .validNew()
            .crids(crid)
            .clearBroadcasters()
            .broadcasters("VPRO")
            .title(TITLE_PREFIX + "(1) " + dynamicSuffix)
            .midRef(midRef)
            .ageRating(AgeRating.ALL)
            .build();
    }

    private String crid(String type, String dynamicSuffix) {
        return BASE_CRID + "/" + type + "/" + dynamicSuffix;
    }

    private String clipCrid(String dynamicSuffix) {
        return crid("clip", dynamicSuffix);
    }
}
