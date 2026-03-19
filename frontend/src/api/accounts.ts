import api from '@/lib/axios';

export interface Account {
  accountId: string;
  customerId: string;
  type: 'CHECKING' | 'SAVINGS';
  currency: string;
  productCode: string;
  status: 'ACTIVE' | 'CLOSED' | 'FROZEN';
  openedAt: string;
  updatedAt: string;
}

export interface Balance {
  accountId: string;
  availableBalance: number;
  currency: string;
  updatedAt: string;
}

export interface OpenAccountRequest {
  type: string;
  currency: string;
  productCode: string;
}

export const accountsApi = {
  // Get all accounts for a customer
  getAccountsByCustomer: async (customerId: string): Promise<Account[]> => {
    const response = await api.get(`/customers/${customerId}/accounts`);
    // Assuming AccountListResponse has an `accounts` array
    return response.data.accounts;
  },

  // Get a single account by ID
  getAccount: async (accountId: string): Promise<Account> => {
    const response = await api.get(`/accounts/${accountId}`);
    return response.data;
  },

  // Get balance for an account
  getBalance: async (accountId: string): Promise<Balance> => {
    const response = await api.get(`/accounts/${accountId}/balance`);
    return response.data;
  },

  // Open a new account
  openAccount: async (req: OpenAccountRequest & { customerId: string }): Promise<{ accountId: string }> => {
    const response = await api.post('/accounts', req);
    return response.data;
  },

  // Freeze account
  freezeAccount: async (accountId: string, reason: string): Promise<void> => {
    await api.post(`/accounts/${accountId}/freeze`, { reason });
  },

  // Unfreeze account
  unfreezeAccount: async (accountId: string, reason: string): Promise<void> => {
    await api.post(`/accounts/${accountId}/unfreeze`, { reason });
  },

  // Close account
  closeAccount: async (accountId: string, reason: string): Promise<void> => {
    await api.post(`/accounts/${accountId}/close`, { reason });
  }
};
