import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin, concat } from 'rxjs';
import { toArray } from 'rxjs/operators';
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
      error: err => { this.error = err.message || 'Error sending event'; this.loading = false; }
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
      error: err => { this.error = err.message || 'Error'; this.loading = false; }
    });
  }

  /** Sends FUEL_LEVEL 80% then 62% sequentially — triggers CEP_FuelDrop (>15% drop in 10min). */
  runFuelDrop() {
    this.loading = true;
    this.error = '';
    const truckId = this.event.entityId || 'K-1';
    this.dispatchService.sendEvent({ type: 'FUEL_LEVEL', entityId: truckId, value: 80, location: '' })
      .subscribe({
        next: msgs1 => {
          this.dispatchService.sendEvent({ type: 'FUEL_LEVEL', entityId: truckId, value: 62, location: '' })
            .subscribe({
              next: msgs2 => {
                this.addToLog(
                  { type: 'FUEL_LEVEL', entityId: truckId + ' (80%→62%, drop >15%)', value: 0, location: '' },
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

  loadScenario(scenario: 'breakdown' | 'lowfuel' | 'delay' | 'trip_started' | 'unloading_started' | 'delivery_confirmed') {
    if (scenario === 'breakdown') {
      this.event = { type: 'BREAKDOWN', entityId: 'K-1', value: 1, location: 'Autoput E75' };
    } else if (scenario === 'lowfuel') {
      this.event = { type: 'FUEL_LEVEL', entityId: 'K-1', value: 12, location: 'Novi Sad' };
    } else if (scenario === 'trip_started') {
      this.event = { type: 'TRIP_STARTED', entityId: 'K-1', value: 0, location: '' };
    } else if (scenario === 'unloading_started') {
      this.event = { type: 'UNLOADING_STARTED', entityId: 'K-1', value: 0, location: '' };
    } else if (scenario === 'delivery_confirmed') {
      this.event = { type: 'DELIVERY_CONFIRMED', entityId: 'K-1', value: 0, location: '' };
    } else {
      this.event = { type: 'DELAY', entityId: 'K-1', value: 50, location: 'Beograd' };
    }
  }

  /** Sends 3 DELAY events sequentially — triggers CEP_DelayEscalation (3 delays in 30min window). */
  runDelayEscalation() {
    this.loading = true;
    this.error = '';
    const truckId = this.event.entityId || 'K-1';
    const evt = { type: 'DELAY' as FleetEventType, entityId: truckId, value: 30, location: 'Beograd' };
    concat(
      this.dispatchService.sendEvent({ ...evt }),
      this.dispatchService.sendEvent({ ...evt }),
      this.dispatchService.sendEvent({ ...evt })
    ).pipe(toArray()).subscribe({
      next: results => {
        this.addToLog(
          { type: 'DELAY', entityId: truckId + ' (3× delay → escalation)', value: 0, location: '' },
          results.flat()
        );
        this.loading = false;
        this.refreshFleet();
      },
      error: err => { this.error = err.message || 'Error'; this.loading = false; }
    });
  }

  /**
   * Sends 2 POSITION events at the same location with timestamps 21 min apart (simulated).
   * Triggers CEP_VehicleStopped (ista lokacija >20min).
   * Requires: FC dispatch + TRIP_STARTED for this truck must be done first
   * so that an IN_PROGRESS order exists in the CEP session.
   */
  runVehicleStopped() {
    this.loading = true;
    this.error = '';
    const truckId = this.event.entityId || 'K-1';
    const now = Date.now();
    this.dispatchService.sendEvent({
      type: 'POSITION', entityId: truckId, value: 0, location: 'Autoput E75', timestamp: now
    }).subscribe({
      next: m1 => {
        this.dispatchService.sendEvent({
          type: 'POSITION', entityId: truckId, value: 0, location: 'Autoput E75',
          timestamp: now + 21 * 60 * 1000
        }).subscribe({
          next: m2 => {
            this.addToLog(
              { type: 'POSITION', entityId: truckId + ' (2× same loc., Δt=21min → VEHICLE_STOPPED)', value: 0, location: '' },
              [...m1, ...m2]
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
      time: new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
    });
  }
}
