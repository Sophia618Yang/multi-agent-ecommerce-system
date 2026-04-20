package com.ecommerce.agent;

import com.ecommerce.model.AgentResult;
import com.ecommerce.model.Product;
import com.ecommerce.model.UserProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Marketing copy agent with template selection, personalized generation, and compliance filtering.
 */
@Component
public class MarketingCopyAgent extends BaseAgent {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, String> TEMPLATES = Map.of(
            "new_user", "Write welcoming copy for a new shopper. Use a warm tone and highlight a first-purchase benefit.",
            "high_value", "Write premium copy for a high-value shopper. Emphasize quality, brand value, and exclusivity.",
            "price_sensitive", "Write value-focused copy for a price-sensitive shopper. Emphasize savings and practical value.",
            "active", "Write engaging copy for an active shopper. Highlight product benefits and use cases.",
            "churn_risk", "Write win-back copy for a shopper at risk of churn. Highlight a relevant offer and urgency."
    );

    private static final List<String> FORBIDDEN_WORDS = List.of(
            "best ever", "number one", "guaranteed", "100%", "permanent", "miracle", "ultimate"
    );

    public MarketingCopyAgent(ChatClient.Builder chatClientBuilder) {
        super("marketing_copy", 10.0, 2);
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        UserProfile profile = (UserProfile) params.get("userProfile");
        List<Product> products = (List<Product>) params.getOrDefault("products", List.of());

        if (products.isEmpty()) {
            return AgentResult.builder().agentName(name).success(true)
                    .data(Map.of("copies", List.of())).confidence(1.0).build();
        }

        String templateKey = selectTemplate(profile);
        String systemPrompt = TEMPLATES.getOrDefault(templateKey, TEMPLATES.get("active"))
                + "\nGenerate one short line of copy for each product, 15-30 words each. Return a JSON array: [{\"product_id\":\"xxx\",\"copy\":\"copy text\"}]";

        String productInfo = products.stream()
                .map(p -> "ID:" + p.getProductId() + " " + p.getName() + " $" + p.getPrice() + " " + p.getTags())
                .collect(Collectors.joining("\n"));

        String response = chatClient.prompt()
                .system(systemPrompt)
                .user("Product list:\n" + productInfo)
                .call()
                .content();

        List<Map<String, String>> copies = parseCopies(response);
        copies = copies.stream().map(this::complianceCheck).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("copies", copies);
        data.put("template_used", templateKey);

        return AgentResult.builder()
                .agentName(name)
                .success(true)
                .data(data)
                .confidence(0.9)
                .build();
    }

    private String selectTemplate(UserProfile profile) {
        if (profile == null || profile.getSegments() == null) return "active";
        List<String> priority = List.of("new_user", "high_value", "churn_risk", "price_sensitive", "active");
        for (String seg : priority) {
            if (profile.getSegments().contains(seg)) return seg;
        }
        return "active";
    }

    private List<Map<String, String>> parseCopies(String raw) {
        try {
            String cleaned = raw.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(cleaned.indexOf('\n') + 1);
                cleaned = cleaned.substring(0, cleaned.lastIndexOf("```"));
            }
            return objectMapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse copies: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, String> complianceCheck(Map<String, String> copyItem) {
        String text = copyItem.getOrDefault("copy", "");
        for (String word : FORBIDDEN_WORDS) {
            text = text.replace(word, "***");
        }
        Map<String, String> result = new HashMap<>(copyItem);
        result.put("copy", text);
        return result;
    }
}
