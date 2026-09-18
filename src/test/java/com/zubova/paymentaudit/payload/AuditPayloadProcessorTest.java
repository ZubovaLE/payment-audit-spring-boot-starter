package com.zubova.paymentaudit.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zubova.paymentaudit.config.PaymentAuditProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditPayloadProcessorTest {

    private static final String[] EXCLUDED_FIELDS = new String[]{"cvv"};
    private static final List<String> SENSITIVE_FIELDS = List.of("cardNumber");
    private final Customer customer = new Customer("1234567812345678", "123", "John");
    private ObjectMapper objectMapper;
    private PaymentAuditProperties properties;
    private AuditPayloadProcessor processor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new PaymentAuditProperties();
        properties.setSensitiveFields(SENSITIVE_FIELDS);
        processor = new AuditPayloadProcessor(objectMapper, properties);
    }

    @Test
    void shouldExcludeAndMaskFields() throws JsonProcessingException {
        String result = processor.process(customer, EXCLUDED_FIELDS);

        JsonNode resultNode = objectMapper.readTree(result);
        assertThat(resultNode.get("cardNumber").asText()).isEqualTo("****");
        assertThat(resultNode.has("cvv")).isFalse();
        assertThat(resultNode.get("customerName").asText()).isEqualTo("John");
    }

    @Test
    void shouldExcludeAndMaskNestedFields() throws JsonProcessingException {
        CustomerRequest request = new CustomerRequest("123", customer);

        String result = processor.process(request, EXCLUDED_FIELDS);

        JsonNode resultNode = objectMapper.readTree(result);
        JsonNode customerNode = resultNode.get("customer");
        assertThat(resultNode.get("id").asText()).isEqualTo("123");
        assertThat(customerNode.get("cardNumber").asText()).isEqualTo("****");
        assertThat(customerNode.has("cvv")).isFalse();
        assertThat(customerNode.get("customerName").asText()).isEqualTo("John");
    }

    @Test
    void shouldNotMaskSensitiveFieldsWhenMaskingIsDisabled() throws JsonProcessingException {
        properties.setMaskSensitiveData(false);
        CustomerRequest request = new CustomerRequest("123", customer);

        String result = processor.process(request, EXCLUDED_FIELDS);

        JsonNode resultNode = objectMapper.readTree(result);
        assertThat(resultNode.get("id").asText()).isEqualTo("123");

        JsonNode customerNode = resultNode.get("customer");
        assertThat(customerNode.get("cardNumber").asText()).isEqualTo("1234567812345678");
        assertThat(customerNode.has("cvv")).isFalse();
        assertThat(customerNode.get("customerName").asText()).isEqualTo("John");
    }

    @Test
    void shouldSanitizeAllElementsWhenPayloadIsArray() throws JsonProcessingException {
        Customer newCustomer = new Customer("8765432187654321", "321", "Bob");
        List<Customer> customers = List.of(customer, newCustomer);

        String result = processor.process(customers, EXCLUDED_FIELDS);

        JsonNode resultNode = objectMapper.readTree(result);

        JsonNode customer1Node = resultNode.get(0);
        assertThat(customer1Node.get("cardNumber").asText()).isEqualTo("****");
        assertThat(customer1Node.has("cvv")).isFalse();
        assertThat(customer1Node.get("customerName").asText()).isEqualTo("John");

        JsonNode customer2Node = resultNode.get(1);
        assertThat(customer2Node.get("cardNumber").asText()).isEqualTo("****");
        assertThat(customer2Node.has("cvv")).isFalse();
        assertThat(customer2Node.get("customerName").asText()).isEqualTo("Bob");
    }

    @Test
    void shouldReturnNullWhenPayloadIsNull() {
        String result = processor.process(null, EXCLUDED_FIELDS);

        assertThat(result).isNull();
    }

    record CustomerRequest(String id, Customer customer) {
    }

    record Customer(String cardNumber, String cvv, String customerName) {
    }

}