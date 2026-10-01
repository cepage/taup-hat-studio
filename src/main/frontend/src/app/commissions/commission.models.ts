export type CommissionStatus = 'PENDING' | 'SENT' | 'FAILED';

export interface CommissionRequest {
  id: number;
  firestoreId: string;
  replyTo: string | null;
  subject: string | null;
  body: string | null;
  status: CommissionStatus;
  attempts: number;
  lastError: string | null;
  receivedAt: string;
  sentAt: string | null;
}
