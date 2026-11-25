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
    private static final String MODEL = "gemini-3.5-flash";

    private final Client client;
    private final ChatModel model;
    private final GoogleGenAiChatOptions defaults;

    public GeminiAssistantModel(@Value("${GEMINI_API_KEY:}") String apiKey) {
        client = apiKey.isBlank() ? null : Client.builder().apiKey(apiKey.trim()).build();
        defaults = GoogleGenAiChatOptions.builder().model(MODEL).maxOutputTokens(1200).build();
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
                .toolChoice(new ToolChoice(ToolChoice.Mode.AUTO, List.of()))
                .maxOutputTokens(512)
                .build();
    }

    @PreDestroy
    void close() {
        if (client != null) client.close();
    }
}
