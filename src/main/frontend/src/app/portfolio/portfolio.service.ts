import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PortfolioItem, PortfolioSet } from './portfolio.models';

@Injectable({ providedIn: 'root' })
export class PortfolioService {
  private readonly baseUrl = '/api/portfolio';
  private readonly setsUrl = '/api/portfolio-sets';

  constructor(private readonly http: HttpClient) {}

  // ── Items ──

  list(): Observable<PortfolioItem[]> {
    return this.http.get<PortfolioItem[]>(this.baseUrl);
  }

  get(id: number): Observable<PortfolioItem> {
    return this.http.get<PortfolioItem>(`${this.baseUrl}/${id}`);
  }

  create(
    file: File,
    title: string,
    description?: string,
    category?: string,
    setId?: number,
  ): Observable<PortfolioItem> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('title', title);
    if (description) formData.append('description', description);
    if (category) formData.append('category', category);
    if (setId != null) formData.append('setId', String(setId));
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

  // ── Sets ──

  listSets(): Observable<PortfolioSet[]> {
    return this.http.get<PortfolioSet[]>(this.setsUrl);
  }

  getSet(id: number): Observable<PortfolioSet> {
    return this.http.get<PortfolioSet>(`${this.setsUrl}/${id}`);
  }

  createSet(file: File, title: string, description?: string): Observable<PortfolioSet> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('title', title);
    if (description) formData.append('description', description);
    return this.http.post<PortfolioSet>(this.setsUrl, formData);
  }

  updateSet(id: number, set: Partial<PortfolioSet>): Observable<PortfolioSet> {
    return this.http.put<PortfolioSet>(`${this.setsUrl}/${id}`, set);
  }

  updateSetIcon(id: number, file: File): Observable<PortfolioSet> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.put<PortfolioSet>(`${this.setsUrl}/${id}/icon`, formData);
  }

  deleteSet(id: number): Observable<void> {
    return this.http.delete<void>(`${this.setsUrl}/${id}`);
  }

  setSetItems(setId: number, orderedItemIds: number[]): Observable<PortfolioItem[]> {
    return this.http.put<PortfolioItem[]>(`${this.setsUrl}/${setId}/items`, orderedItemIds);
  }

  reorderSets(orderedIds: number[]): Observable<PortfolioSet[]> {
    return this.http.put<PortfolioSet[]>(`${this.setsUrl}/reorder`, orderedIds);
  }
}
