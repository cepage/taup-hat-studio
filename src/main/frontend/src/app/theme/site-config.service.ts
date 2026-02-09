import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SiteConfig } from './site-config.models';

@Injectable({ providedIn: 'root' })
export class SiteConfigService {
  private readonly baseUrl = '/api/site-config';

  constructor(private readonly http: HttpClient) {}

  get(): Observable<SiteConfig> {
    return this.http.get<SiteConfig>(this.baseUrl);
  }

  update(config: SiteConfig): Observable<SiteConfig> {
    return this.http.put<SiteConfig>(this.baseUrl, config);
  }

  uploadHeroImage(file: File): Observable<SiteConfig> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.put<SiteConfig>(`${this.baseUrl}/hero-image`, formData);
  }

  deleteHeroImage(): Observable<SiteConfig> {
    return this.http.delete<SiteConfig>(`${this.baseUrl}/hero-image`);
  }
}
