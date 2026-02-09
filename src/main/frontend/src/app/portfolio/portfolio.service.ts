import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PortfolioItem } from './portfolio.models';

@Injectable({ providedIn: 'root' })
export class PortfolioService {
  private readonly baseUrl = '/api/portfolio';

  constructor(private readonly http: HttpClient) {}

  list(): Observable<PortfolioItem[]> {
    return this.http.get<PortfolioItem[]>(this.baseUrl);
  }

  get(id: number): Observable<PortfolioItem> {
    return this.http.get<PortfolioItem>(`${this.baseUrl}/${id}`);
  }

  create(file: File, title: string, description?: string, category?: string): Observable<PortfolioItem> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('title', title);
    if (description) formData.append('description', description);
    if (category) formData.append('category', category);
    return this.http.post<PortfolioItem>(this.baseUrl, formData);
  }

  update(id: number, item: Partial<PortfolioItem>): Observable<PortfolioItem> {
    return this.http.put<PortfolioItem>(`${this.baseUrl}/${id}`, item);
  }

  updateImage(id: number, file: File): Observable<PortfolioItem> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.put<PortfolioItem>(`${this.baseUrl}/${id}/image`, formData);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  reorder(orderedIds: number[]): Observable<PortfolioItem[]> {
    return this.http.put<PortfolioItem[]>(`${this.baseUrl}/reorder`, orderedIds);
  }
}
