package com.zubova.paymentaudit.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zubova.paymentaudit.config.PaymentAuditProperties;

import java.util.*;

public class AuditPayloadProcessor {

    private static final String MASK = "****";

    private final ObjectMapper objectMapper;
    private final PaymentAuditProperties properties;

    public AuditPayloadProcessor(ObjectMapper objectMapper, PaymentAuditProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String process(Object value, String[] excludedFields) {
        if (value == null) {
            return null;
        }
        JsonNode root = objectMapper.valueToTree(value);

        Set<String> excluded = new HashSet<>(Arrays.asList(excludedFields));
        Set<String> sensitive = new HashSet<>(properties.getSensitiveFields());

        sanitize(root, excluded, sensitive);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit payload", e);
        }
    }

    private void sanitize(JsonNode node, Set<String> excludedFields, Set<String> sensitiveFields) {
        if (node == null) {
            return;
        }

        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;

            List<String> fieldNames = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(fieldNames::add);

            for (String fieldName : fieldNames) {

                if (excludedFields.contains(fieldName)) {
                    objectNode.remove(fieldName);
                    continue;
                }

                if (properties.isMaskSensitiveData() && sensitiveFields.contains(fieldName)) {
                    objectNode.put(fieldName, MASK);
                    continue;
                }

                sanitize(objectNode.get(fieldName), excludedFields, sensitiveFields);
            }

            return;
        }

        if (node.isArray()) {
            node.forEach(element ->
                    sanitize(element, excludedFields, sensitiveFields));
        }
    }

}