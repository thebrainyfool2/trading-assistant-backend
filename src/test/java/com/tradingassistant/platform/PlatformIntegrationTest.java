package com.tradingassistant.platform;

import com.jayway.jsonpath.JsonPath;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.tradingassistant.platform.security.Role;
import com.tradingassistant.platform.user.UserDocument;
import com.tradingassistant.platform.user.UserService;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end platform tests over the real security chain, the real controllers
 * and a real MongoDB wire protocol server.
 *
 * <p>The in-memory server is a transport-level fake: the production repositories,
 * indexes and credential rules are the ones under test. The trading engine is
 * intentionally not registered, exactly as in a deployment without broker
 * credentials.</p>
 */
@SpringBootTest(properties = {
        "app.platform.scheduling.enabled=false",
        "app.security.bcrypt-strength=4",
        "app.security.issuer=trading-assistant-test"
})
@AutoConfigureMockMvc
@Import(PlatformIntegrationTest.FakeMongoConfiguration.class)
class PlatformIntegrationTest {

    private static final MongoServer SERVER;
    private static final String CONNECTION_STRING;

    static {
        SERVER = new MongoServer(new MemoryBackend());
        CONNECTION_STRING = SERVER.bindAndGetConnectionString()
                + "/trading_assistant_test";
    }

    /**
     * Overrides the production Mongo client with the in-memory server. The
     * production configuration still supplies the URI; this bean makes the
     * tests independent of it.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class FakeMongoConfiguration {

        @Bean
        @Primary
        MongoClient testMongoClient() {
            return MongoClients.create(CONNECTION_STRING);
        }
    }

    @AfterAll
    static void stopServer() {
        SERVER.shutdownNow();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserService userService;

    @BeforeEach
    void cleanDatabase() {
        mongoTemplate.getDb().drop();
    }

    @Test
    void registrationIssuesTokensAndProfile() throws Exception {
        String response = register("alice@example.com", "Alice", "password123");
        assertThat((String) JsonPath.read(response, "$.data.accessToken")).isNotBlank();
        assertThat((String) JsonPath.read(response, "$.data.refreshToken")).isNotBlank();
        assertThat((String) JsonPath.read(response, "$.data.user.email"))
                .isEqualTo("alice@example.com");
        assertThat((java.util.List<String>) JsonPath.read(response, "$.data.user.roles"))
                .containsExactly("USER");

        String token = JsonPath.read(response, "$.data.accessToken");
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Alice"));
    }

    @Test
    void duplicateRegistrationIsAConflict() throws Exception {
        register("dup@example.com", "First", "password123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("DUP@example.com", "Second", "password123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void invalidRegistrationBodyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("not-an-email", "", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details").isNotEmpty());
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        register("login@example.com", "Login", "password123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"login@example.com\","
                                + "\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void loginRaisesNotificationAndRefreshRotates() throws Exception {
        register("rotate@example.com", "Rotate", "password123");
        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"rotate@example.com\","
                                + "\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String access = JsonPath.read(loginBody, "$.data.accessToken");
        String refresh = JsonPath.read(loginBody, "$.data.refreshToken");

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[*].type", hasItem("LOGIN_ALERT")));

        String rotated = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(rotated, "$.data.refreshToken"))
                .isNotEqualTo(refresh);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String response = register("logout@example.com", "Logout", "password123");
        String refresh = JsonPath.read(response, "$.data.refreshToken");
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revoked").value(true));
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedAccessTokenIsRejected() throws Exception {
        String response = register("tamper@example.com", "Tamper", "password123");
        String token = JsonPath.read(response, "$.data.accessToken");
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void passwordChangeRequiresCurrentPassword() throws Exception {
        String response = register("password@example.com", "Password", "password123");
        String token = JsonPath.read(response, "$.data.accessToken");
        mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password\","
                                + "\"newPassword\":\"newpassword123\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\","
                                + "\"newPassword\":\"newpassword123\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"password@example.com\","
                                + "\"password\":\"newpassword123\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"password@example.com\","
                                + "\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void watchlistsArePersistedAndPrivate() throws Exception {
        String aliceToken = JsonPath.read(
                register("owner@example.com", "Owner", "password123"),
                "$.data.accessToken");
        String bobToken = JsonPath.read(
                register("other@example.com", "Other", "password123"),
                "$.data.accessToken");

        String created = mockMvc.perform(post("/api/v1/watchlists")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Momentum\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String watchlistId = JsonPath.read(created, "$.data.id");

        mockMvc.perform(post("/api/v1/watchlists/" + watchlistId + "/items")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbolToken\":\"2885\",\"symbol\":\"RELIANCE\","
                                + "\"segment\":\"NSE_CASH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));

        mockMvc.perform(get("/api/v1/watchlists/" + watchlistId)
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/watchlists")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Momentum"))
                .andExpect(jsonPath("$.data[0].items[0].symbol").value("RELIANCE"));

        mockMvc.perform(delete("/api/v1/watchlists/" + watchlistId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/watchlists")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void preferencesAreCreatedWithDarkThemeDefaults() throws Exception {
        String token = JsonPath.read(
                register("prefs@example.com", "Prefs", "password123"),
                "$.data.accessToken");
        mockMvc.perform(get("/api/v1/settings/preferences")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.theme").value("DARK"))
                .andExpect(jsonPath("$.data.defaultInterval").value("FIFTEEN_MINUTE"))
                .andExpect(jsonPath("$.data.notificationsEnabled").value(true));

        mockMvc.perform(put("/api/v1/settings/preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"DARK\",\"defaultInterval\":\"FIVE_MINUTE\","
                                + "\"defaultStrategyId\":\"EMA_TREND\","
                                + "\"notificationsEnabled\":true,"
                                + "\"orderNotifications\":false,"
                                + "\"positionNotifications\":true,"
                                + "\"loginAlerts\":true,"
                                + "\"systemMessages\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultInterval").value("FIVE_MINUTE"))
                .andExpect(jsonPath("$.data.orderNotifications").value(false));
    }

    @Test
    void orderPlacementReportsEngineUnavailableWithoutBroker() throws Exception {
        String token = JsonPath.read(
                register("order@example.com", "Order", "password123"),
                "$.data.accessToken");
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"strategyId\":\"EMA_TREND\",\"segment\":\"NSE_CASH\","
                                + "\"symbolToken\":\"2885\",\"side\":\"BUY\","
                                + "\"type\":\"MARKET\",\"timeInForce\":\"DAY\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("ENGINE_UNAVAILABLE"));

        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/v1/history/orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void portfolioAndAnalysisAreReadableWithoutEngine() throws Exception {
        String token = JsonPath.read(
                register("read@example.com", "Read", "password123"),
                "$.data.accessToken");
        mockMvc.perform(get("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.positions.length()").value(0));
        mockMvc.perform(get("/api/v1/positions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/v1/market/overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engineAvailable").value(false))
                .andExpect(jsonPath("$.data.markets.length()").value(0));
        mockMvc.perform(get("/api/v1/strategy/definitions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/v1/options/assessments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/risk/assessments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void administratorEndpointsRequireTheAdminRole() throws Exception {
        String token = JsonPath.read(
                register("user@example.com", "User", "password123"),
                "$.data.accessToken");
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(put("/api/v1/settings/application")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maintenanceMode\":true,"
                                + "\"maximumWatchlistItems\":50,"
                                + "\"historyRetentionDays\":90}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCanManageUsersAndSettings() throws Exception {
        String adminEmail = "admin@example.com";
        register(adminEmail, "Admin", "password123");
        UserDocument admin = userService.findByEmail(adminEmail).orElseThrow();
        userService.updateRoles(admin.id(), Set.of(Role.USER, Role.ADMIN));
        String adminToken = login(adminEmail, "password123");

        String other = register("member@example.com", "Member", "password123");
        String memberId = JsonPath.read(other, "$.data.user.id");

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(put("/api/v1/users/" + memberId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\","
                                + "\"password\":\"password123\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/settings/application")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maintenanceMode\":true,"
                                + "\"maximumWatchlistItems\":50,"
                                + "\"historyRetentionDays\":90}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maintenanceMode").value(true));

        mockMvc.perform(get("/api/v1/settings/application")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maximumWatchlistItems").value(50));
    }

    @Test
    void lastActiveAdministratorCannotBeDisabled() throws Exception {
        String email = "sole-admin@example.com";
        register(email, "Sole Admin", "password123");
        UserDocument admin = userService.findByEmail(email).orElseThrow();
        userService.updateRoles(admin.id(), Set.of(Role.ADMIN));
        String token = login(email, "password123");

        mockMvc.perform(put("/api/v1/users/" + admin.id() + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void systemStatusReportsDatabaseAndEngineState() throws Exception {
        mockMvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.database").value("UP"))
                .andExpect(jsonPath("$.data.engine").value("DISABLED"))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void authenticatedUnknownResourceReturnsNotFound() throws Exception {
        String token = JsonPath.read(
                register("unknown@example.com", "Unknown", "password123"),
                "$.data.accessToken");
        mockMvc.perform(get("/api/v1/not-a-route")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void notificationsCanBeReadAndMarkedRead() throws Exception {
        String registration = register("notify@example.com", "Notify", "password123");
        String token = login("notify@example.com", "password123");
        String notificationId = mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        java.util.List<String> ids = JsonPath.read(notificationId,
                "$.data.items[*].id");
        assertThat(ids).isNotEmpty();

        mockMvc.perform(put("/api/v1/notifications/" + ids.getFirst() + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));
        mockMvc.perform(put("/api/v1/notifications/read-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unread").value(0));

        assertThat((String) JsonPath.read(registration, "$.data.user.id")).isNotBlank();
    }

    private String register(String email, String displayName, String password)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, displayName, password)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    private static String registerBody(String email, String displayName, String password) {
        return "{\"email\":\"" + email + "\",\"displayName\":\"" + displayName
                + "\",\"password\":\"" + password + "\"}";
    }
}
