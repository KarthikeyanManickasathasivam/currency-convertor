export interface ConversionRequest {
  fromCurrency: string;
  toCurrency: string;
  amount: number;
}

export interface TransactionResponse {
  transactionId: string;
  userId: string;
  fromCurrency: string;
  toCurrency: string;
  amount: number;
  convertedAmount: number;
  rate: number;
  transactionDate: string;
  status: 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';
  approvedBy?: string;
  approvalDate?: string;
}

export interface RateResponse {
  id?: number;
  fromCurrency: string;
  toCurrency: string;
  rate: number;
  lastUpdated?: string;
  source?: string;
  isActive?: boolean;
}

export interface LogEntry {
  logId: number;
  event: string;
  eventType: string;
  timestamp: string;
  userId?: string;
  ipAddress: string;
  details?: Record<string, unknown>;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
