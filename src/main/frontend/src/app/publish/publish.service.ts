import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface PreviewResponse {
  status: string;
  previewUrl?: string;
  fileCount: number;
  timestamp: string;
  message?: string;
}

export interface DeployResponse {
  status: string;
  fileCount?: number;
  timestamp: string;
  message?: string;
}

export interface PreviewSummaryResponse {
  status: string;
  fileCount: number;
  files: string[];
  timestamp: string;
  message?: string;
}

@Injectable({ providedIn: 'root' })
export class PublishService {
  private readonly baseUrl = '/api/publish';

  constructor(private readonly http: HttpClient) {}

  generatePreview(): Observable<PreviewResponse> {
    return this.http.post<PreviewResponse>(`${this.baseUrl}/preview`, null);
  }

  deployToProduction(): Observable<DeployResponse> {
    return this.http.post<DeployResponse>(`${this.baseUrl}/deploy`, null);
  }

  getPreviewSummary(): Observable<PreviewSummaryResponse> {
    return this.http.get<PreviewSummaryResponse>(`${this.baseUrl}/preview-summary`);
  }
}
