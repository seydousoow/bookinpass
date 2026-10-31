package com.bookinpass.backend.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClaudeClientTest {

    private static final String ANSWER = """
            {"stop_reason":"end_turn","content":[{"type":"text","text":"{\\"verdict\\":\\"approve\\"}"}]}""";

    private final JsonMapper json = JsonMapper.builder().build();
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.anthropic.com");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RestClient rest = builder.defaultHeader("x-api-key", "sk-ant-test").build();

    private ObjectNode schema() {
        return (ObjectNode) json.readTree("{\"type\":\"object\",\"properties\":{\"verdict\":{\"type\":\"string\"}},\"required\":[\"verdict\"],\"additionalProperties\":false}");
    }

    @Test
    void cheapestRequestWithoutThinkingNorFallback() {
        ClaudeClient claude = new ClaudeClient(rest, json, "claude-haiku-5-5", false, false);
        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "sk-ant-test"))
                .andExpect(headerDoesNotExist("anthropic-beta"))
                .andExpect(jsonPath("$.model").value("claude-haiku-5-5"))
                .andExpect(jsonPath("$.thinking.type").value("disabled"))
                .andExpect(jsonPath("$.output_config.effort").value("low"))
                .andExpect(jsonPath("$.output_config.format.type").value("json_schema"))
                .andExpect(jsonPath("$.fallbacks").doesNotExist())
                .andExpect(jsonPath("$.temperature").doesNotExist())
                .andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

        assertThat(claude.structured("Moderate.", "<review>Great</review>", schema(), "low", 1000).path("verdict").asString()).isEqualTo("approve");
        server.verify();
    }

    @Test
    void refusalIsReported() {
        ClaudeClient claude = new ClaudeClient(rest, json, "claude-haiku-5-5", false, false);
        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withSuccess("{\"stop_reason\":\"refusal\",\"stop_details\":{\"category\":\"cyber\"},\"content\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> claude.structured("Moderate.", "text", schema(), "low", 1000)).isInstanceOf(ClaudeClient.Refusal.class);
    }

    @Test
    void truncatedAnswerIsAnError() {
        ClaudeClient claude = new ClaudeClient(rest, json, "claude-haiku-5-5", false, false);
        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withSuccess("{\"stop_reason\":\"max_tokens\",\"content\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> claude.structured("Moderate.", "text", schema(), "low", 1000)).isInstanceOf(IllegalStateException.class);
    }
}
