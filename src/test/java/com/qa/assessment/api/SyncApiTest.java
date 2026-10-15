package com.qa.assessment.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SyncApiTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final String URL = "http://127.0.0.1:" + MockSyncServer.PORT + "/sync-survey";

    @BeforeAll static void startServer() throws Exception { MockSyncServer.start(); }
    @AfterAll static void stopServer() { MockSyncServer.stop(); }
    @BeforeEach void resetStore() { MockSyncServer.clearData(); }

    @Test void sameLocalIdFromDifferentDevicesDoesNotOverwrite() throws Exception {
        assertEquals(201, post(payload("sub-ankit-001", "device-ankit", "School_1", "Good")).statusCode());
        HttpResponse<String> response = post(payload("sub-pooja-001", "device-pooja", "School_1", "Needs repair"));
        assertEquals(201, response.statusCode());
        assertEquals("sub-pooja-001", json(response).path("submissionId").asText());
        assertEquals("accepted", json(response).path("status").asText());
    }

    @Test void identicalRetryIsIdempotent() throws Exception {
        ObjectNode body = payload("sub-retry-001", "device-retry", "School_1", "Good");
        assertEquals(201, post(body).statusCode());
        HttpResponse<String> retry = post(body);
        assertEquals(200, retry.statusCode());
        assertEquals("already_accepted", json(retry).path("status").asText());
    }

    @Test void sameSubmissionIdWithChangedBodyReturnsConflict() throws Exception {
        ObjectNode body = payload("sub-conflict-001", "device-conflict", "School_1", "Good");
        assertEquals(201, post(body).statusCode());
        body.putObject("answers").put("building_condition", "Poor");
        HttpResponse<String> response = post(body);
        assertEquals(409, response.statusCode());
        assertEquals("idempotency_conflict", json(response).path("error").asText());
    }

    @Test void missingDeviceIdIsRejected() throws Exception {
        ObjectNode body = payload("sub-invalid-001", "device-invalid", "School_1", "Good");
        body.remove("deviceId");
        HttpResponse<String> response = post(body);
        assertEquals(400, response.statusCode());
        assertEquals("missing_required_field", json(response).path("error").asText());
    }

    @Test void sameDeviceAndLocalIdCannotCreateSecondSubmissionSilently() throws Exception {
        assertEquals(201, post(payload("sub-local-001", "device-same", "School_1", "Good")).statusCode());
        HttpResponse<String> response = post(payload("sub-local-002", "device-same", "School_1", "Poor"));
        assertEquals(409, response.statusCode());
        assertEquals("device_local_id_conflict", json(response).path("error").asText());
    }

    private static ObjectNode payload(String submissionId, String deviceId, String localId, String condition) {
        ObjectNode body = JSON.createObjectNode();
        body.put("submissionId", submissionId); body.put("deviceId", deviceId);
        body.put("localId", localId); body.put("entityName", "Govt Primary School");
        body.put("schemaVersion", "v1"); body.putObject("answers").put("building_condition", condition);
        body.put("capturedAt", "2026-10-14T10:00:00+05:30");
        return body;
    }

    private static HttpResponse<String> post(JsonNode body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode json(HttpResponse<String> response) throws Exception { return JSON.readTree(response.body()); }
}
