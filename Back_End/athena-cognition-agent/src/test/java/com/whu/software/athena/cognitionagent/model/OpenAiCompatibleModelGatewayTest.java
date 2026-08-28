package com.whu.software.athena.cognitionagent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProperties;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProviderException;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleModelGatewayTest {

    @Test
    void sendsSharedRequestAndReturnsJsonUsageAndCost() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();
            ArrayNode choices = root.putArray("choices");
            choices.addObject().putObject("message").put(
                    "content", "{\"result\":\"ok\"}");
            root.putObject("usage")
                    .put("prompt_tokens", 100)
                    .put("completion_tokens", 20)
                    .put("total_tokens", 120);
            byte[] body = mapper.writeValueAsBytes(root);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            ModelResponse response = new OpenAiCompatibleModelGateway(
                    new ObjectMapper(), properties(server)).complete(
                    new ModelRequest("test-prompt-v1", "system boundary",
                            "{\"allowed\":true}", 200));

            assertEquals("ok", response.output().path("result").asText());
            assertEquals(120, response.totalTokens());
            assertEquals(0.00028d, response.estimatedCost(), 0.0000001d);
            assertEquals("Bearer test-key", authorization.get());
            assertTrue(requestBody.get().contains("test-prompt-v1"));
            assertTrue(requestBody.get().contains("system boundary"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsNonJsonModelContent() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            IntentModelProviderException exception = assertThrows(
                    IntentModelProviderException.class,
                    () -> new OpenAiCompatibleModelGateway(
                            new ObjectMapper(), properties(server)).complete(
                            new ModelRequest("v1", "system", "{}", 100)));
            assertEquals("MODEL_OUTPUT_INVALID", exception.errorCode().name());
        } finally {
            server.stop(0);
        }
    }

    private IntentModelProperties properties(HttpServer server) {
        IntentModelProperties value = new IntentModelProperties();
        value.setApiKey("test-key");
        value.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        value.setModelName("test-model");
        value.setTimeoutMs(5000);
        value.setInputCostPerMillionTokens(2.0d);
        value.setOutputCostPerMillionTokens(4.0d);
        return value;
    }
}
