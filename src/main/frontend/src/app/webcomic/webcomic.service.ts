import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { WebcomicSeries, WebcomicIssue, WebcomicPage } from './webcomic.models';

@Injectable({ providedIn: 'root' })
export class WebcomicService {
  private readonly baseUrl = '/api/webcomic';

  constructor(private readonly http: HttpClient) {}

  // ── Series ──────────────────────────────────────────────────────────────────

  listSeries(): Observable<WebcomicSeries[]> {
    return this.http.get<WebcomicSeries[]>(`${this.baseUrl}/series`);
  }

  getSeries(id: number): Observable<WebcomicSeries> {
    return this.http.get<WebcomicSeries>(`${this.baseUrl}/series/${id}`);
  }

  createSeries(series: Partial<WebcomicSeries>): Observable<WebcomicSeries> {
    return this.http.post<WebcomicSeries>(`${this.baseUrl}/series`, series);
  }

  updateSeries(id: number, series: Partial<WebcomicSeries>): Observable<WebcomicSeries> {
    return this.http.put<WebcomicSeries>(`${this.baseUrl}/series/${id}`, series);
  }

  deleteSeries(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/series/${id}`);
  }

  reorderSeries(orderedIds: number[]): Observable<WebcomicSeries[]> {
    return this.http.put<WebcomicSeries[]>(`${this.baseUrl}/series/reorder`, orderedIds);
  }

  // ── Issues ──────────────────────────────────────────────────────────────────

  listIssues(seriesId: number): Observable<WebcomicIssue[]> {
    return this.http.get<WebcomicIssue[]>(`${this.baseUrl}/series/${seriesId}/issues`);
  }

  getIssue(seriesId: number, issueId: number): Observable<WebcomicIssue> {
    return this.http.get<WebcomicIssue>(`${this.baseUrl}/series/${seriesId}/issues/${issueId}`);
  }

  createIssue(seriesId: number, issue: Partial<WebcomicIssue>): Observable<WebcomicIssue> {
    return this.http.post<WebcomicIssue>(`${this.baseUrl}/series/${seriesId}/issues`, issue);
  }

  updateIssue(
    seriesId: number,
    issueId: number,
    issue: Partial<WebcomicIssue>,
  ): Observable<WebcomicIssue> {
    return this.http.put<WebcomicIssue>(
      `${this.baseUrl}/series/${seriesId}/issues/${issueId}`,
      issue,
    );
  }

  deleteIssue(seriesId: number, issueId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/series/${seriesId}/issues/${issueId}`);
  }

  // ── Pages ───────────────────────────────────────────────────────────────────

  listPages(seriesId: number, issueId: number): Observable<WebcomicPage[]> {
    return this.http.get<WebcomicPage[]>(
      `${this.baseUrl}/series/${seriesId}/issues/${issueId}/pages`,
    );
  }

  uploadPage(seriesId: number, issueId: number, file: File): Observable<WebcomicPage> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<WebcomicPage>(
      `${this.baseUrl}/series/${seriesId}/issues/${issueId}/pages`,
      formData,
    );
  }

  reorderPages(seriesId: number, issueId: number, orderedPageIds: number[]): Observable<WebcomicPage[]> {
    return this.http.put<WebcomicPage[]>(
      `${this.baseUrl}/series/${seriesId}/issues/${issueId}/pages/reorder`,
      orderedPageIds,
    );
  }

  deletePage(seriesId: number, issueId: number, pageId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.baseUrl}/series/${seriesId}/issues/${issueId}/pages/${pageId}`,
    );
  }
}
