package com.bank.account.service.api;

import com.bank.account.domain.AccountType;
import com.bank.sharedkernel.domain.Currency;
import com.bank.account.service.MockRepositoryConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@Import(MockRepositoryConfig.class)
public class AccountControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void shouldOpenAccountSuccessfully() throws Exception {
        // Given
        AccountRequests.OpenAccountRequest request = new AccountRequests.OpenAccountRequest(
                "CUST-" + UUID.randomUUID().toString().substring(0, 8),
                AccountType.CHECKING,
                Currency.USD,
                "PROD-001"
        );

        // When & Then
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").exists());
    }

    @Test
    public void shouldReturnBadRequestWhenInputIsInvalid() throws Exception {
        // Given
        AccountRequests.OpenAccountRequest request = new AccountRequests.OpenAccountRequest(
                "", // Blank customerId
                AccountType.CHECKING,
                Currency.USD,
                "PROD-001"
        );

        // When & Then
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Autowired
    private com.bank.sharedkernel.domain.EventSourcedRepository<com.bank.account.domain.AccountAggregate> repository;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.bank.account.application.AccountReadModelRepository readModelRepository;

    @Test
    public void shouldFreezeAccountSuccessfully() throws Exception {
        String accountId = UUID.randomUUID().toString();
        com.bank.account.domain.AccountAggregate aggregate = com.bank.account.domain.AccountAggregate.open(
                new com.bank.account.domain.AccountId(accountId), new com.bank.account.domain.CustomerId("cust-1"), AccountType.CHECKING, Currency.USD, "PROD", "corr"
        );
        org.mockito.Mockito.when(repository.load(accountId)).thenReturn(aggregate);

        AccountRequests.FreezeAccountRequest request = new AccountRequests.FreezeAccountRequest("Suspicious activity");

        mockMvc.perform(post("/api/v1/accounts/{accountId}/freeze", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }

    @Test
    public void shouldUnfreezeAccountSuccessfully() throws Exception {
        String accountId = UUID.randomUUID().toString();
        com.bank.account.domain.AccountAggregate aggregate = com.bank.account.domain.AccountAggregate.open(
                new com.bank.account.domain.AccountId(accountId), new com.bank.account.domain.CustomerId("cust-1"), AccountType.CHECKING, Currency.USD, "PROD", "corr"
        );
        aggregate.freeze("suspicious", "corr", "causation");
        org.mockito.Mockito.when(repository.load(accountId)).thenReturn(aggregate);

        AccountRequests.UnfreezeAccountRequest request = new AccountRequests.UnfreezeAccountRequest("Identity verified");

        mockMvc.perform(post("/api/v1/accounts/{accountId}/unfreeze", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }

    @Test
    public void shouldCloseAccountSuccessfully() throws Exception {
        String accountId = UUID.randomUUID().toString();
        com.bank.account.domain.AccountAggregate aggregate = com.bank.account.domain.AccountAggregate.open(
                new com.bank.account.domain.AccountId(accountId), new com.bank.account.domain.CustomerId("cust-1"), AccountType.CHECKING, Currency.USD, "PROD", "corr"
        );
        org.mockito.Mockito.when(repository.load(accountId)).thenReturn(aggregate);

        AccountRequests.CloseAccountRequest request = new AccountRequests.CloseAccountRequest("Customer request");

        mockMvc.perform(post("/api/v1/accounts/{accountId}/close", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }

    @Test
    public void shouldGetAccountSuccessfully() throws Exception {
        String accountId = UUID.randomUUID().toString();
        com.bank.account.application.AccountReadModel readModel = new com.bank.account.application.AccountReadModel(
                accountId, "cust-1", AccountType.SAVINGS, Currency.USD, "SAV", com.bank.account.domain.AccountStatus.ACTIVE, java.time.Instant.now(), java.time.Instant.now()
        );
        org.mockito.Mockito.when(readModelRepository.findByAccountId(accountId)).thenReturn(java.util.Optional.of(readModel));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/accounts/{accountId}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    public void shouldGetAccountsForCustomerSuccessfully() throws Exception {
        String customerId = "cust-1";
        com.bank.account.application.AccountReadModel readModel = new com.bank.account.application.AccountReadModel(
                UUID.randomUUID().toString(), customerId, AccountType.SAVINGS, Currency.USD, "SAV", com.bank.account.domain.AccountStatus.ACTIVE, java.time.Instant.now(), java.time.Instant.now()
        );
        org.mockito.Mockito.when(readModelRepository.findByCustomerId(customerId)).thenReturn(java.util.List.of(readModel));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/customers/{customerId}/accounts", customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts[0].customerId").value(customerId));
    }
}
