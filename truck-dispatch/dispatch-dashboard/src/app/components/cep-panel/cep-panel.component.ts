import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { DispatchService } from '../../services/dispatch.service';
import { Alarm, FleetEvent, FleetEventType } from '../../models/models';

interface EventLogEntry {
  event: FleetEvent & { label?: string };
  messages: string[];
  time: string;
}

@Component({
  selector: 'app-cep-panel',
  imports: [CommonModule, FormsModule],
  templateUrl: './cep-panel.component.html',
  styleUrl: './cep-panel.component.css'
})
export class CepPanelComponent implements OnInit {
  eventTypes: FleetEventType[] = [
    'TRIP_STARTED', 'UNLOADING_STARTED', 'DELIVERY_CONFIRMED',
    'DELAY', 'POSITION', 'BREAKDOWN', 'FUEL_LEVEL', 'NEW_ORDER'
  ];

  event: FleetEvent = { type: 'TRIP_STARTED', entityId: '', value: 0, location: '' };

  eventLog: EventLogEntry[] = [];
  activeAlarms: Alarm[] = [];
  fleetTrucks: any[] = [];
  fleetOrders: any[] = [];

  loading = false;
  fleetLoading = false;
  error = '';

  constructor(private dispatchService: DispatchService) {}

  ngOnInit() {
    this.refreshFleet();
  }

  refreshFleet() {
    this.fleetLoading = true;
    forkJoin({
      fleet: this.dispatchService.getFleet(),
      alarms: this.dispatchService.getAlarms()
    }).subscribe({
      next: ({ fleet, alarms }) => {
        this.fleetTrucks = fleet.trucks ?? [];
        this.fleetOrders = (fleet.orders ?? []).filter((o: any) =>
          ['ASSIGNED', 'IN_PROGRESS', 'WAITING_UNLOADING', 'REPLANNED'].includes(o.status)
        );
        this.activeAlarms = alarms;
        this.fleetLoading = false;
      },
      error: () => { this.fleetLoading = false; }
    });
  }

  send() {
    this.loading = true;
    this.error = '';
    const snapshot = { ...this.event };
    this.dispatchService.sendEvent(this.event).subscribe({
      next: msgs => {
        this.addToLog(snapshot, msgs);
        this.loading = false;
        this.refreshFleet();
      },
      error: err => { this.error = err.message || 'Greška pri slanju'; this.loading = false; }
    });
  }

  /** Sends 5 NEW_ORDER events in parallel — triggers CEP_OrderSpike (5 orders in 15min window). */
  runOrderSpike() {
    this.loading = true;
    this.error = '';
    const requests = Array.from({ length: 5 }, (_, i) =>
      this.dispatchService.sendEvent({ type: 'NEW_ORDER', entityId: `O-SP-${i + 1}`, value: 0, location: '' })
    );
    forkJoin(requests).subscribe({
      next: results => {
        const allMsgs = results.flat();
        this.addToLog(
          { type: 'NEW_ORDER', entityId: '5 × NEW_ORDER (order spike)', value: 0, location: '' },
          allMsgs
        );
        this.loading = false;
        this.refreshFleet();
      },
      error: err => { this.error = err.message || 'Greška'; this.loading = false; }
    });
  }

  /** Sends FUEL_LEVEL 80% then 62% sequentially — triggers CEP_FuelDrop (>15% drop in 10min). */
  runFuelDrop() {
    this.loading = true;
    this.error = '';
    const truckId = this.event.entityId || 'K-01';
    this.dispatchService.sendEvent({ type: 'FUEL_LEVEL', entityId: truckId, value: 80, location: '' })
      .subscribe({
        next: msgs1 => {
          this.dispatchService.sendEvent({ type: 'FUEL_LEVEL', entityId: truckId, value: 62, location: '' })
            .subscribe({
              next: msgs2 => {
                this.addToLog(
                  { type: 'FUEL_LEVEL', entityId: truckId + ' (80%→62%, pad >15%)', value: 0, location: '' },
                  [...msgs1, ...msgs2]
                );
                this.loading = false;
                this.refreshFleet();
              },
              error: err => { this.error = err.message; this.loading = false; }
            });
        },
        error: err => { this.error = err.message; this.loading = false; }
      });
  }

  loadScenario(scenario: 'breakdown' | 'lowfuel' | 'delay' | 'trip_started' | 'delivery_confirmed') {
    if (scenario === 'breakdown') {
      this.event = { type: 'BREAKDOWN', entityId: 'K-01', value: 1, location: 'Autoput E75' };
    } else if (scenario === 'lowfuel') {
      this.event = { type: 'FUEL_LEVEL', entityId: 'K-01', value: 12, location: 'Novi Sad' };
    } else if (scenario === 'trip_started') {
      this.event = { type: 'TRIP_STARTED', entityId: 'K-01', value: 0, location: '' };
    } else if (scenario === 'delivery_confirmed') {
      this.event = { type: 'DELIVERY_CONFIRMED', entityId: 'K-01', value: 0, location: '' };
    } else {
      this.event = { type: 'DELAY', entityId: 'K-01', value: 50, location: 'Beograd' };
    }
  }

  clearLog() { this.eventLog = []; }

  alarmIcon(type: string): string {
    const icons: Record<string, string> = {
      ESCALATION: '🚨', VEHICLE_STOPPED: '🛑', EMERGENCY_REPLACEMENT: '🔄',
      FUEL_LEAK: '⛽', DOMINO_ESCALATION: '🌊', ORDER_SPIKE: '📦',
      OVERLOADED_DRIVER: '😴', OVERLOADED_TRUCK: '⚖️', FLEET_SHORTAGE: '🚛'
    };
    return icons[type] ?? '⚠️';
  }

  eventIcon(type: FleetEventType): string {
    const icons: Record<FleetEventType, string> = {
      TRIP_STARTED: '🚛', UNLOADING_STARTED: '📦', DELIVERY_CONFIRMED: '✅',
      DELAY: '⏱', POSITION: '📍', BREAKDOWN: '🔧', FUEL_LEVEL: '⛽', NEW_ORDER: '📋'
    };
    return icons[type] ?? '•';
  }

  truckStatusClass(status: string): string {
    if (status === 'AVAILABLE') return 'status-available';
    if (status === 'BUSY') return 'status-busy';
    if (status === 'BREAKDOWN') return 'status-breakdown';
    return 'status-service';
  }

  private addToLog(event: FleetEvent & { label?: string }, messages: string[]) {
    this.eventLog.unshift({
      event,
      messages,
      time: new Date().toLocaleTimeString('sr-Latn', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
    });
  }
}
