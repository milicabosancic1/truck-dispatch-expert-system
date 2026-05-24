import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DispatchService } from '../../services/dispatch.service';
import {
  CargoType, DeliveryOrder, DispatchRequest, DispatchResult,
  Driver, OrderPriority, RoadType, Route, Truck, TruckStatus, TruckType
} from '../../models/models';

@Component({
  selector: 'app-dispatch-panel',
  imports: [CommonModule, FormsModule],
  templateUrl: './dispatch-panel.component.html',
  styleUrl: './dispatch-panel.component.css'
})
export class DispatchPanelComponent {
  @Output() resultReady = new EventEmitter<DispatchResult>();

  loading = false;
  error = '';

  cargoTypes: CargoType[] = ['STANDARDNO', 'RASHLADNI', 'OPASNA_ROBA', 'LOMLJIVO'];
  priorities: OrderPriority[] = ['NORMAL', 'HIGH', 'URGENT', 'CRITICAL_DELIVERY'];
  truckTypes: TruckType[] = ['SMALL', 'MEDIUM', 'LARGE'];
  truckStatuses: TruckStatus[] = ['AVAILABLE', 'BUSY', 'BREAKDOWN', 'SERVICE'];
  roadTypes: RoadType[] = ['CITY', 'LOCAL', 'REGIONAL', 'HIGHWAY'];

  request: DispatchRequest = this.emptyRequest();

  constructor(private dispatchService: DispatchService) {}

  private emptyRequest(): DispatchRequest {
    return {
      temperature: 15,
      hour: new Date().getHours(),
      dayOfWeek: new Date().getDay() || 7,
      orders: [], trucks: [], drivers: [], routes: []
    };
  }

  submit() {
    this.loading = true;
    this.error = '';
    this.dispatchService.process(this.request).subscribe({
      next: result => { this.loading = false; this.resultReady.emit(result); },
      error: err => { this.loading = false; this.error = err.message || 'Connection error'; }
    });
  }

  reset() {
    this.request = this.emptyRequest();
    this.error = '';
  }

  loadDemo() {
    this.request = {
      temperature: -3,
      hour: 7,
      dayOfWeek: 3,
      orders: [{
        id: 'K-09', destination: 'Beograd', weightKg: 2000,
        cargoType: 'RASHLADNI', deliveryDeadlineMin: 120,
        priority: 'URGENT', status: 'NEW', routeId: 'R-01'
      }],
      trucks: [{
        id: 'V-05', type: 'MEDIUM', maxCapacityKg: 5000, status: 'AVAILABLE',
        location: 'Novi Sad', fuelPercent: 75, hasRefrigerationUnit: true,
        hasAdrEquipment: false, distanceToOriginKm: 10, daysSinceRefrigerationService: 5
      }],
      drivers: [{
        id: 'D-01', available: true, workingHoursToday: 4, license: 'CE',
        hasAdrLicense: false, fatigueLevel: 2, yearsOfExperience: 5, recentRouteIds: []
      }],
      routes: [{
        id: 'R-01', roadType: 'REGIONAL', distanceKm: 80, estimatedTimeHours: 2,
        maxCapacityKg: 10000, maxSpeedKmh: 90, hasTunnel: false
      }]
    };
  }

  addOrder() {
    this.request.orders.push({
      id: '', destination: '', weightKg: 0, cargoType: 'STANDARDNO',
      deliveryDeadlineMin: 60, priority: 'NORMAL', status: 'NEW', routeId: ''
    });
  }
  removeOrder(i: number) { this.request.orders.splice(i, 1); }

  addTruck() {
    this.request.trucks.push({
      id: '', type: 'MEDIUM', maxCapacityKg: 5000, status: 'AVAILABLE',
      location: '', fuelPercent: 100, hasRefrigerationUnit: false,
      hasAdrEquipment: false, distanceToOriginKm: 0, daysSinceRefrigerationService: 0
    });
  }
  removeTruck(i: number) { this.request.trucks.splice(i, 1); }

  addDriver() {
    this.request.drivers.push({
      id: '', available: true, workingHoursToday: 0, license: 'CE',
      hasAdrLicense: false, fatigueLevel: 0, yearsOfExperience: 1, recentRouteIds: []
    });
  }
  removeDriver(i: number) { this.request.drivers.splice(i, 1); }

  addRoute() {
    this.request.routes.push({
      id: '', roadType: 'REGIONAL', distanceKm: 0, estimatedTimeHours: 0,
      maxCapacityKg: 0, maxSpeedKmh: 80, hasTunnel: false
    });
  }
  removeRoute(i: number) { this.request.routes.splice(i, 1); }
}
