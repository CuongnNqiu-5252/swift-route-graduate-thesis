package com.swiftroute.orderservice.service.assistant;

import java.util.List;

import com.google.genai.Client;
import jakarta.annotation.PreDestroy;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions.ToolChoice;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GeminiAssistantModel {
    private final Client client;
    private final ChatModel model;
    private final GoogleGenAiChatOptions defaults;

    public GeminiAssistantModel(@Value("${GEMINI_API_KEY:}") String apiKey,
            @Value("${GEMINI_MODEL:gemini-3.1-flash-lite}") String modelName) {
        client = apiKey.isBlank() ? null : Client.builder().apiKey(apiKey.trim()).build();
        defaults = GoogleGenAiChatOptions.builder().model(modelName).maxOutputTokens(900).build();
        model = client == null ? null : GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .options(defaults)
                .build();
    }

    ChatModel model() {
        return model;
    }

    GoogleGenAiChatOptions toolSelectionOptions(List<ToolCallback> tools) {
        return defaults.mutate().toolCallbacks(tools)
                .toolChoice(new ToolChoice(ToolChoice.Mode.ANY, List.of()))
                .maxOutputTokens(512)
                .build();
    }

    @PreDestroy
    void close() {
        if (client != null) client.close();
    }
}
