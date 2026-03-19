package com.bank.account.service.api;

import com.bank.account.domain.AccountType;
import com.bank.sharedkernel.domain.Currency;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class AccountControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void shouldOpenAccountSuccessfully() throws Exception {
        AccountRequests.OpenAccountRequest request = new AccountRequests.OpenAccountRequest(
                "CUST-" + UUID.randomUUID().toString().substring(0, 8),
                AccountType.CHECKING,
                Currency.USD,
                "PROD-001"
        );

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").exists());
    }

    @Test
    public void shouldReturnBadRequestWhenInputIsInvalid() throws Exception {
        AccountRequests.OpenAccountRequest request = new AccountRequests.OpenAccountRequest(
                "", // blank customerId — must fail validation
                AccountType.CHECKING,
                Currency.USD,
                "PROD-001"
        );

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void shouldFreezeAccountSuccessfully() throws Exception {
        // Step 1: create account
        AccountRequests.OpenAccountRequest openRequest = new AccountRequests.OpenAccountRequest(
                "cust-" + UUID.randomUUID().toString().substring(0, 8),
                AccountType.CHECKING, Currency.USD, "PROD-001"
        );
        String response = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openRequest)))
                .andReturn().getResponse().getContentAsString();
        String accountId = objectMapper.readTree(response).get("accountId").asText();

        // Step 2: freeze it
        AccountRequests.FreezeAccountRequest request = new AccountRequests.FreezeAccountRequest("Suspicious activity");
        mockMvc.perform(post("/api/v1/accounts/{accountId}/freeze", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }

    @Test
    public void shouldGetAccountSuccessfully() throws Exception {
        // Step 1: create account
        AccountRequests.OpenAccountRequest openRequest = new AccountRequests.OpenAccountRequest(
                "cust-" + UUID.randomUUID().toString().substring(0, 8),
                AccountType.CHECKING, Currency.USD, "PROD-001"
        );
        String response = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openRequest)))
                .andReturn().getResponse().getContentAsString();
        String accountId = objectMapper.readTree(response).get("accountId").asText();

        // Step 2: wait for async Kafka projection to write to PostgreSQL read model
        Thread.sleep(1500);

        // Step 3: read it back
        mockMvc.perform(get("/api/v1/accounts/{accountId}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId));
    }

    @Test
    public void shouldReturnNotFoundWhenAccountDoesNotExist() throws Exception {
        String accountId = "non-existent-" + UUID.randomUUID();

        mockMvc.perform(get("/api/v1/accounts/{accountId}", accountId))
                .andExpect(status().isNotFound());
    }

    @Test
    public void shouldReturnBadRequestForInvalidCurrency() throws Exception {
        String json = "{\"customerId\":\"cust-1\",\"type\":\"CHECKING\",\"currency\":\"INVALID\",\"productCode\":\"PROD-001\"}";

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}
