import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Alarm, DispatchRequest, DispatchResult, FleetEvent } from '../models/models';

@Injectable({ providedIn: 'root' })
export class DispatchService {
  private readonly base = 'http://localhost:8080/api/dispatch';

  constructor(private http: HttpClient) {}

  process(request: DispatchRequest): Observable<DispatchResult> {
    return this.http.post<DispatchResult>(`${this.base}/process`, request);
  }

  sendEvent(event: FleetEvent): Observable<string[]> {
    return this.http.post<string[]>(`${this.base}/event`, event);
  }

  getAlarms(): Observable<Alarm[]> {
    return this.http.get<Alarm[]>(`${this.base}/alarms`);
  }
}
