package com.ecommerce.agent;

import com.ecommerce.model.AgentResult;
import com.ecommerce.model.UserProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * User profile agent for behavioral signals, RFM scoring, and segmentation.
 */
@Component
public class UserProfileAgent extends BaseAgent {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are an e-commerce user profiling expert. Analyze the user's behavior data and generate a structured profile.
            Return JSON in this format:
            {"segments":["active"],"preferred_categories":["smartphones"],"price_range":[0,10000],
             "rfm_score":{"recency":0.8,"frequency":0.5,"monetary":0.6},
             "real_time_tags":{"active_time":"evening"}}
            Return JSON only.""";

    public UserProfileAgent(ChatClient.Builder chatClientBuilder) {
        super("user_profile", 5.0, 2);
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        String userId = (String) params.get("userId");
        Map<String, Object> behavior = collectBehavior(userId, params);

        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("User ID: " + userId + "\nBehavior data: " + objectMapper.writeValueAsString(behavior))
                .call()
                .content();

        UserProfile profile = parseProfile(userId, response);

        Map<String, Object> data = new HashMap<>();
        data.put("raw_analysis", response);
        data.put("profile", profile);

        return AgentResult.builder()
                .agentName(name)
                .success(true)
                .data(data)
                .confidence(0.85)
                .build();
    }

    private Map<String, Object> collectBehavior(String userId, Map<String, Object> params) {
        Map<String, Object> behavior = new HashMap<>();
        behavior.put("user_id", userId);
        behavior.put("recent_views", List.of("smartphones", "headphones", "tablets"));
        behavior.put("recent_purchases", List.of("charger"));
        behavior.put("view_count_7d", 25);
        behavior.put("purchase_count_30d", 3);
        behavior.put("avg_order_amount", 299.0);
        return behavior;
    }

    @SuppressWarnings("unchecked")
    private UserProfile parseProfile(String userId, String raw) {
        try {
            String cleaned = raw.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(cleaned.indexOf('\n') + 1);
                cleaned = cleaned.substring(0, cleaned.lastIndexOf("```"));
            }
            Map<String, Object> data = objectMapper.readValue(cleaned, Map.class);

            List<String> segments = (List<String>) data.getOrDefault("segments", List.of("active"));
            List<String> categories = (List<String>) data.getOrDefault("preferred_categories", List.of());
            List<?> priceRaw = (List<?>) data.getOrDefault("price_range", List.of(0, 10000));
            Map<String, Double> rfm = (Map<String, Double>) data.getOrDefault("rfm_score", Map.of());
            Map<String, Object> tags = (Map<String, Object>) data.getOrDefault("real_time_tags", Map.of());

            return UserProfile.builder()
                    .userId(userId)
                    .segments(segments)
                    .preferredCategories(categories)
                    .priceRange(new double[]{
                            ((Number) priceRaw.get(0)).doubleValue(),
                            priceRaw.size() > 1 ? ((Number) priceRaw.get(1)).doubleValue() : 10000
                    })
                    .rfmScore(rfm)
                    .realTimeTags(tags)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse profile for {}: {}", userId, e.getMessage());
            return UserProfile.builder()
                    .userId(userId)
                    .segments(List.of("active"))
                    .build();
        }
    }
}
