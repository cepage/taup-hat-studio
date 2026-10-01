import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CommissionRequest } from './commission.models';

@Injectable({ providedIn: 'root' })
export class CommissionService {
  private readonly baseUrl = '/api/commissions';

  constructor(private readonly http: HttpClient) {}

  list(): Observable<CommissionRequest[]> {
    return this.http.get<CommissionRequest[]>(this.baseUrl);
  }

  retry(id: number): Observable<CommissionRequest> {
    return this.http.post<CommissionRequest>(`${this.baseUrl}/${id}/retry`, null);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
