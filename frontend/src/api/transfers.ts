import api from '@/lib/axios';

export interface CreateTransferRequest {
  sourceAccountId: string;
  destinationAccountId: string;
  beneficiaryName: string;
  amount: number;
  currency: string;
  reference: string;
}

export interface TransferResponse {
  transferId: string;
  sourceAccountId: string;
  destinationAccountId: string;
  beneficiaryName: string;
  amount: number;
  currency: string;
  reference: string;
  status: 'PENDING' | 'COMPLETED' | 'FAILED';
  ledgerReference?: string;
  failureReason?: string;
  failureDetail?: string;
  createdAt: string;
  updatedAt: string;
}

export const transfersApi = {
  createTransfer: async (req: CreateTransferRequest): Promise<TransferResponse> => {
    const response = await api.post('/transfers', req);
    return response.data;
  },

  getTransfer: async (transferId: string): Promise<TransferResponse> => {
    const response = await api.get(`/transfers/${transferId}`);
    return response.data;
  }
};
