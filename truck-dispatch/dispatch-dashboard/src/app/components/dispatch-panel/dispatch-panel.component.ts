import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DispatchService } from '../../services/dispatch.service';
import {
  CargoType, DispatchRequest, DispatchResult,
  OrderPriority, RoadType, TruckStatus, TruckType
} from '../../models/models';

@Component({
  selector: 'app-dispatch-panel',
  imports: [CommonModule, FormsModule],
  templateUrl: './dispatch-panel.component.html',
  styleUrl: './dispatch-panel.component.css'
})
export class DispatchPanelComponent implements OnInit {
  @Input() mode: 'fc' | 'accumulate' = 'fc';
  @Input() showDemo = false;
  @Output() resultReady = new EventEmitter<DispatchResult>();

  loading = false;
  error = '';
  fleetLoading = false;

  cargoTypes: CargoType[]   = ['STANDARD', 'REFRIGERATED', 'HAZARDOUS', 'FRAGILE'];
  priorities: OrderPriority[] = ['NORMAL', 'HIGH', 'URGENT', 'CRITICAL_DELIVERY'];
  truckTypes: TruckType[]   = ['SMALL', 'MEDIUM', 'LARGE'];
  truckStatuses: TruckStatus[] = ['AVAILABLE', 'BUSY', 'BREAKDOWN', 'SERVICE'];
  roadTypes: RoadType[]     = ['CITY', 'LOCAL', 'REGIONAL', 'HIGHWAY'];
  orderStatuses = ['NEW', 'VALID', 'ASSIGNED', 'IN_PROGRESS', 'WAITING_RESOURCES'];

  private oSeq = 1;
  private kSeq = 1;
  private vSeq = 1;
  private rSeq = 1;

  collapsed = { routes: false, orders: false, trucks: false, drivers: false };
  toggle(s: keyof typeof this.collapsed) { this.collapsed[s] = !this.collapsed[s]; }

  // Live fleet from backend — shown as read-only in normal mode
  fleet: { trucks: any[], drivers: any[], routes: any[], orders: any[] } | null = null;

  demoMode = false;
  demoLabel = '';
  currentScenario: 'cold' | 'adr' | 'domino' | 'acc' | 'routeCap' | 'deadline' | 'fuel' | 'service' | 'hours' | 'overloaded' | 'delay_route' | null = null;
  showAdvanced = false;

  request: DispatchRequest = this.emptyRequest();

  newOrder = this.emptyNewOrder();

  constructor(private dispatchService: DispatchService) {}

  ngOnInit() {
    if (this.mode === 'fc')         this.loadFleet();
    if (this.mode === 'accumulate') this.loadFleetForAcc();
  }

  // ---- fleet ----

  loadFleet() {
    this.fleetLoading = true;
    this.dispatchService.getFleet().subscribe({
      next: r => { this.fleet = r; this.fleetLoading = false; },
      error: () => { this.fleetLoading = false; }
    });
  }

  loadFleetForAcc() {
    this.fleetLoading = true;
    this.dispatchService.getFleet().subscribe({
      next: r => {
        this.fleet = r;
        this.request.trucks  = [...(r.trucks  ?? [])];
        this.request.drivers = [...(r.drivers ?? [])];
        this.request.routes  = [...(r.routes  ?? [])];
        this.request.orders  = (r.orders ?? []).filter((o: any) => o.status !== 'COMPLETED');
        this.oSeq = 100; this.kSeq = 100; this.vSeq = 100; this.rSeq = 100;
        this.fleetLoading = false;
      },
      error: () => { this.fleetLoading = false; }
    });
  }

  resetAndLoadFleet() {
    this.fleetLoading = true;
    this.dispatchService.resetFleet().subscribe({
      next: r => { this.fleet = r; this.fleetLoading = false; },
      error: () => { this.fleetLoading = false; this.loadFleet(); }
    });
  }

  /** Routes visible in the order form — fleet routes in normal mode, request routes in demo mode. */
  get currentRoutes() { return this.demoMode ? this.request.routes : (this.fleet?.routes ?? []); }
  get currentTrucks() { return this.demoMode ? this.request.trucks : (this.fleet?.trucks ?? []); }
  get currentDrivers() { return this.demoMode ? this.request.drivers : (this.fleet?.drivers ?? []); }

  // ---- computed ----

  get totalWeightKg(): number {
    return this.request.orders.reduce((s, o) => s + Number(o.weightKg || 0), 0);
  }
  get availableTrucks(): number {
    return this.currentTrucks.filter((t: any) => t.status === 'AVAILABLE').length;
  }
  get availableDrivers(): number {
    return this.currentDrivers.filter((d: any) => d.available).length;
  }
  get specialOrders(): number {
    return this.request.orders.filter(o => o.cargoType !== 'STANDARD').length;
  }

  // ---- lifecycle ----

  private emptyRequest(): DispatchRequest {
    return {
      temperature: 15, hour: new Date().getHours(),
      dayOfWeek: new Date().getDay() || 7,
      orders: [], trucks: [], drivers: [], routes: []
    };
  }

  private emptyNewOrder() {
    return {
      destination: '', weightKg: 1000,
      cargoType: 'STANDARD' as CargoType,
      priority: 'NORMAL' as OrderPriority,
      routeId: ''
    };
  }

  addNewOrder() {
    if (!this.newOrder.destination) return;
    this.request.orders.push({
      id: `O-${this.oSeq++}`,
      destination: this.newOrder.destination,
      weightKg: this.newOrder.weightKg,
      cargoType: this.newOrder.cargoType,
      priority: this.newOrder.priority,
      routeId: this.newOrder.routeId || (this.currentRoutes[0]?.id ?? ''),
      status: 'NEW',
      deliveryDeadlineMin: 180,
      delayMin: 0
    });
    this.newOrder = this.emptyNewOrder();
  }

  addOrderAcc() {
    this.request.orders.push({
      id: `O-${this.oSeq++}`, destination: 'Beograd', weightKg: 1000,
      cargoType: 'STANDARD', deliveryDeadlineMin: 300,
      priority: 'NORMAL', status: 'WAITING_RESOURCES' as any,
      routeId: this.currentRoutes[0]?.id ?? 'R-1', delayMin: 0
    });
  }

  reset() {
    this.request = this.emptyRequest();
    this.newOrder = this.emptyNewOrder();
    this.oSeq = 1; this.kSeq = 1; this.vSeq = 1; this.rSeq = 1;
    this.collapsed = { routes: false, orders: false, trucks: false, drivers: false };
    this.demoMode = false;
    this.demoLabel = '';
    this.currentScenario = null;
    this.showAdvanced = false;
    this.error = '';
    if (this.mode === 'fc')         this.loadFleet();
    if (this.mode === 'accumulate') this.loadFleetForAcc();
  }

  submit() {
    this.loading = true;
    this.error = '';
    this.dispatchService.process(this.request).subscribe({
      next: r => {
        this.loading = false;
        this.resultReady.emit(r);
        if (this.mode === 'fc') this.loadFleet();
      },
      error: e => { this.loading = false; this.error = e.message || 'Connection error'; }
    });
  }

  // ---- demo scenarios ----

  loadDemo() {
    this.demoMode = true;
    this.demoLabel = 'Winter Conditions';
    this.currentScenario = 'cold';
    this.oSeq = 2; this.kSeq = 2; this.vSeq = 2; this.rSeq = 2;
    this.request = {
      temperature: -3, hour: 7, dayOfWeek: 3,   // WINTER_CONDITIONS + MORNING_PEAK
      orders: [{
        id: 'O-1', destination: 'Beograd', weightKg: 2000,
        cargoType: 'REFRIGERATED', deliveryDeadlineMin: 120,
        priority: 'URGENT', status: 'NEW', routeId: 'R-1'
      }],
      trucks: [{
        id: 'K-1', type: 'MEDIUM', maxCapacityKg: 5000, status: 'AVAILABLE',
        location: 'Novi Sad', fuelPercent: 75, hasRefrigerationUnit: true,
        hasAdrEquipment: false, distanceToOriginKm: 5, daysSinceRefrigerationService: 10
      }],
      drivers: [{
        id: 'V-1', available: true, workingHoursToday: 2, license: 'CE',
        hasAdrLicense: false, fatigueLevel: 1, yearsOfExperience: 5, recentRouteIds: []
      }],
      routes: [{
        id: 'R-1', roadType: 'REGIONAL', distanceKm: 80, estimatedTimeHours: 2.0,
        maxCapacityKg: 24000, maxSpeedKmh: 90, hasTunnel: false
      }]
    };
  }

  loadIncidentDemo() {
    this.demoMode = true;
    this.demoLabel = 'ADR / Incident';
    this.currentScenario = 'adr';
    this.oSeq = 3; this.kSeq = 3; this.vSeq = 3; this.rSeq = 3;
    this.request = {
      temperature: 8, hour: 18, dayOfWeek: 4,
      orders: [
        {
          id: 'O-1', destination: 'Subotica', weightKg: 1800,
          cargoType: 'HAZARDOUS', deliveryDeadlineMin: 90,
          priority: 'URGENT', status: 'NEW', routeId: 'R-1'
        },
        {
          id: 'O-2', destination: 'Zrenjanin', weightKg: 2200,
          cargoType: 'STANDARD', deliveryDeadlineMin: 300,
          priority: 'HIGH', status: 'NEW', routeId: 'R-2'
        }
      ],
      trucks: [
        {
          id: 'K-1', type: 'MEDIUM', maxCapacityKg: 6000, status: 'AVAILABLE',
          location: 'Novi Sad', fuelPercent: 35, hasRefrigerationUnit: false,
          hasAdrEquipment: true, distanceToOriginKm: 6, daysSinceRefrigerationService: 0
        },
        {
          id: 'K-2', type: 'LARGE', maxCapacityKg: 18000, status: 'AVAILABLE',
          location: 'Novi Sad', fuelPercent: 82, hasRefrigerationUnit: false,
          hasAdrEquipment: false, distanceToOriginKm: 14, daysSinceRefrigerationService: 0
        }
      ],
      drivers: [
        {
          id: 'V-1', available: true, workingHoursToday: 3, license: 'CE',
          hasAdrLicense: true, fatigueLevel: 2, yearsOfExperience: 8, recentRouteIds: ['R-1']
        },
        {
          id: 'V-2', available: true, workingHoursToday: 5, license: 'CE',
          hasAdrLicense: true, fatigueLevel: 5, yearsOfExperience: 1, recentRouteIds: []
        }
      ],
      routes: [
        {
          id: 'R-1', roadType: 'REGIONAL', distanceKm: 105, estimatedTimeHours: 1.5,
          maxCapacityKg: 24000, maxSpeedKmh: 80, hasTunnel: true
        },
        {
          id: 'R-2', roadType: 'REGIONAL', distanceKm: 55, estimatedTimeHours: 1,
          maxCapacityKg: 24000, maxSpeedKmh: 90, hasTunnel: false
        }
      ]
    };
  }

  loadDominoDemo() {
    this.demoMode = true;
    this.demoLabel = 'Domino Effect';
    this.currentScenario = 'domino';
    this.oSeq = 6; this.kSeq = 3; this.vSeq = 2; this.rSeq = 2;
    this.request = {
      temperature: 15, hour: 10, dayOfWeek: 3,
      orders: [
        // O-1: primarno kasnjenje — blokira sve ostale naloge K-1
        { id: 'O-1', destination: 'Beograd', weightKg: 1000, cargoType: 'STANDARD', deliveryDeadlineMin: 300, priority: 'NORMAL', status: 'IN_PROGRESS', routeId: 'R-1', assignedTruckId: 'K-1', delayMin: 35 },
        // O-2/O-3/O-4: tri naloga koja čekaju K-1 → 3× DelayPropagation → Escalation alarm
        { id: 'O-2', destination: 'Zrenjanin',  weightKg: 1000, cargoType: 'STANDARD', deliveryDeadlineMin: 300, priority: 'NORMAL', status: 'ASSIGNED', routeId: 'R-1', assignedTruckId: 'K-1', delayMin: 0 },
        { id: 'O-3', destination: 'Subotica',   weightKg: 1000, cargoType: 'STANDARD', deliveryDeadlineMin: 300, priority: 'NORMAL', status: 'ASSIGNED', routeId: 'R-1', assignedTruckId: 'K-1', delayMin: 0 },
        { id: 'O-4', destination: 'Kikinda',    weightKg: 1000, cargoType: 'STANDARD', deliveryDeadlineMin: 300, priority: 'NORMAL', status: 'ASSIGNED', routeId: 'R-1', assignedTruckId: 'K-1', delayMin: 0 },
        // O-5: URGENT nalog zahvaćen dominom → replanira se na slobodni K-2
        { id: 'O-5', destination: 'Sombor', weightKg: 1000, cargoType: 'STANDARD', deliveryDeadlineMin: 90, priority: 'URGENT', status: 'ASSIGNED', routeId: 'R-1', assignedTruckId: 'K-1', delayMin: 0 }
      ],
      trucks: [
        { id: 'K-1', type: 'MEDIUM', maxCapacityKg: 5000, status: 'BUSY',      location: 'Novi Sad', fuelPercent: 80, hasRefrigerationUnit: false, hasAdrEquipment: false, distanceToOriginKm: 0, daysSinceRefrigerationService: 0 },
        { id: 'K-2', type: 'MEDIUM', maxCapacityKg: 5000, status: 'AVAILABLE', location: 'Novi Sad', fuelPercent: 80, hasRefrigerationUnit: false, hasAdrEquipment: false, distanceToOriginKm: 8, daysSinceRefrigerationService: 0 }
      ],
      drivers: [{
        id: 'V-1', available: true, workingHoursToday: 3, license: 'CE',
        hasAdrLicense: false, fatigueLevel: 1, yearsOfExperience: 5, recentRouteIds: []
      }],
      routes: [{
        id: 'R-1', roadType: 'REGIONAL', distanceKm: 80, estimatedTimeHours: 1,
        maxCapacityKg: 24000, maxSpeedKmh: 90, hasTunnel: false
      }]
    };
  }

  loadDriverHoursDemo() {
    this.demoMode = true;
    this.demoLabel = 'Driver Hours Limit';
    this.currentScenario = 'hours';
    this.oSeq = 2; this.kSeq = 2; this.vSeq = 2; this.rSeq = 2;
    this.request = {
      temperature: 15, hour: 16, dayOfWeek: 3,
      orders: [{
        id: 'O-1', destination: 'Beograd', weightKg: 2000,
        cargoType: 'STANDARD', deliveryDeadlineMin: 300,
        priority: 'NORMAL', status: 'NEW', routeId: 'R-1'
      }],
      trucks: [{
        id: 'K-1', type: 'MEDIUM', maxCapacityKg: 5000, status: 'AVAILABLE',
        location: 'Novi Sad', fuelPercent: 80,
        hasRefrigerationUnit: false, hasAdrEquipment: false,
        distanceToOriginKm: 5, daysSinceRefrigerationService: 0
      }],
      drivers: [{
        id: 'V-1', available: true, workingHoursToday: 6.5, license: 'CE',
        hasAdrLicense: false, fatigueLevel: 3, yearsOfExperience: 5, recentRouteIds: []
      }],
      routes: [{
        id: 'R-1', roadType: 'REGIONAL', distanceKm: 80, estimatedTimeHours: 2.0,
        maxCapacityKg: 24000, maxSpeedKmh: 90, hasTunnel: false
      }]
    };
  }

  loadLowFuelDemo() {
    this.demoMode = true;
    this.demoLabel = 'Low Fuel';
    this.currentScenario = 'fuel';
    this.oSeq = 2; this.kSeq = 2; this.vSeq = 2; this.rSeq = 2;
    this.request = {
      temperature: 15, hour: 10, dayOfWeek: 3,
      orders: [{
        id: 'O-1', destination: 'Beograd', weightKg: 2000,
        cargoType: 'STANDARD', deliveryDeadlineMin: 300,
        priority: 'NORMAL', status: 'NEW', routeId: 'R-1'
      }],
      trucks: [{
        id: 'K-1', type: 'MEDIUM', maxCapacityKg: 5000, status: 'AVAILABLE',
        location: 'Novi Sad', fuelPercent: 5,
        hasRefrigerationUnit: false, hasAdrEquipment: false,
        distanceToOriginKm: 5, daysSinceRefrigerationService: 0
      }],
      drivers: [{
        id: 'V-1', available: true, workingHoursToday: 3, license: 'CE',
        hasAdrLicense: false, fatigueLevel: 1, yearsOfExperience: 5, recentRouteIds: []
      }],
      routes: [{
        id: 'R-1', roadType: 'REGIONAL', distanceKm: 80, estimatedTimeHours: 2.0,
        maxCapacityKg: 24000, maxSpeedKmh: 90, hasTunnel: false
      }]
    };
  }

  loadServiceOverdueDemo() {
    this.demoMode = true;
    this.demoLabel = 'Service Overdue';
    this.currentScenario = 'service';
    this.oSeq = 2; this.kSeq = 2; this.vSeq = 2; this.rSeq = 2;
    this.request = {
      temperature: 4, hour: 8, dayOfWeek: 2,
      orders: [{
        id: 'O-1', destination: 'Beograd', weightKg: 2000,
        cargoType: 'REFRIGERATED', deliveryDeadlineMin: 180,
        priority: 'NORMAL', status: 'NEW', routeId: 'R-1'
      }],
      trucks: [{
        id: 'K-1', type: 'MEDIUM', maxCapacityKg: 5000, status: 'AVAILABLE',
        location: 'Novi Sad', fuelPercent: 80,
        hasRefrigerationUnit: true, hasAdrEquipment: false,
        distanceToOriginKm: 5, daysSinceRefrigerationService: 45
      }],
      drivers: [{
        id: 'V-1', available: true, workingHoursToday: 3, license: 'CE',
        hasAdrLicense: false, fatigueLevel: 1, yearsOfExperience: 5, recentRouteIds: []
      }],
      routes: [{
        id: 'R-1', roadType: 'REGIONAL', distanceKm: 80, estimatedTimeHours: 2.0,
        maxCapacityKg: 24000, maxSpeedKmh: 90, hasTunnel: false
      }]
    };
  }

  loadRouteCapacityDemo() {
    this.demoMode = true;
    this.demoLabel = 'Route Capacity';
    this.currentScenario = 'routeCap';
    this.oSeq = 2; this.kSeq = 2; this.vSeq = 2; this.rSeq = 2;
    this.request = {
      temperature: 15, hour: 10, dayOfWeek: 3,
      orders: [{
        id: 'O-1', destination: 'Prijepolje', weightKg: 8000,
        cargoType: 'STANDARD', deliveryDeadlineMin: 300,
        priority: 'NORMAL', status: 'NEW', routeId: 'R-1'
      }],
      trucks: [{
        id: 'K-1', type: 'LARGE', maxCapacityKg: 18000, status: 'AVAILABLE',
        location: 'Novi Sad', fuelPercent: 90, hasRefrigerationUnit: false,
        hasAdrEquipment: false, distanceToOriginKm: 5, daysSinceRefrigerationService: 0
      }],
      drivers: [{
        id: 'V-1', available: true, workingHoursToday: 3, license: 'CE',
        hasAdrLicense: false, fatigueLevel: 1, yearsOfExperience: 5, recentRouteIds: []
      }],
      routes: [{
        id: 'R-1', roadType: 'LOCAL', distanceKm: 110, estimatedTimeHours: 2.5,
        maxCapacityKg: 5000, maxSpeedKmh: 60, hasTunnel: false
      }]
    };
  }

  loadDeadlineMissDemo() {
    this.demoMode = true;
    this.demoLabel = 'Deadline Miss';
    this.currentScenario = 'deadline';
    this.oSeq = 2; this.kSeq = 2; this.vSeq = 2; this.rSeq = 2;
    this.request = {
      temperature: 15, hour: 10, dayOfWeek: 3,
      orders: [{
        id: 'O-1', destination: 'Beograd', weightKg: 2000,
        cargoType: 'STANDARD', deliveryDeadlineMin: 45,
        priority: 'HIGH', status: 'NEW', routeId: 'R-1'
      }],
      trucks: [{
        id: 'K-1', type: 'MEDIUM', maxCapacityKg: 5000, status: 'AVAILABLE',
        location: 'Novi Sad', fuelPercent: 90, hasRefrigerationUnit: false,
        hasAdrEquipment: false, distanceToOriginKm: 5, daysSinceRefrigerationService: 0
      }],
      drivers: [{
        id: 'V-1', available: true, workingHoursToday: 3, license: 'CE',
        hasAdrLicense: false, fatigueLevel: 1, yearsOfExperience: 5, recentRouteIds: []
      }],
      routes: [{
        id: 'R-1', roadType: 'REGIONAL', distanceKm: 80, estimatedTimeHours: 2.0,
        maxCapacityKg: 24000, maxSpeedKmh: 90, hasTunnel: false
      }]
    };
  }

  loadAccumulateDemo() {
    this.demoMode = true;
    this.demoLabel = 'Accumulate';
    this.currentScenario = 'acc';
    const w = (id: string) => ({
      id, destination: 'Beograd', weightKg: 1000,
      cargoType: 'STANDARD' as CargoType, deliveryDeadlineMin: 300,
      priority: 'NORMAL' as OrderPriority, status: 'WAITING_RESOURCES' as any,
      routeId: 'R-1'
    });
    this.request = {
      temperature: 15, hour: 10, dayOfWeek: 3,
      orders: [w('O-1'), w('O-2'), w('O-3'), w('O-4'), w('O-5'), w('O-6')],
      trucks: [{
        id: 'K-1', type: 'MEDIUM', maxCapacityKg: 10000, status: 'AVAILABLE',
        location: 'Novi Sad', fuelPercent: 80, hasRefrigerationUnit: false,
        hasAdrEquipment: false, distanceToOriginKm: 5, daysSinceRefrigerationService: 0
      }],
      drivers: [
        { id: 'V-1', available: true, workingHoursToday: 10.0, license: 'CE', hasAdrLicense: false, fatigueLevel: 3, yearsOfExperience: 5, recentRouteIds: [] },
        { id: 'V-2', available: true, workingHoursToday: 10.5, license: 'CE', hasAdrLicense: false, fatigueLevel: 4, yearsOfExperience: 3, recentRouteIds: [] },
        { id: 'V-3', available: true, workingHoursToday: 9.5,  license: 'CE', hasAdrLicense: false, fatigueLevel: 2, yearsOfExperience: 7, recentRouteIds: [] }
      ],
      routes: [{
        id: 'R-1', roadType: 'REGIONAL', distanceKm: 80, estimatedTimeHours: 1,
        maxCapacityKg: 24000, maxSpeedKmh: 90, hasTunnel: false
      }]
    };
    this.oSeq = 7; this.kSeq = 2; this.vSeq = 4; this.rSeq = 2;
  }

  loadOverloadedTruckDemo() {
    this.demoMode = true;
    this.demoLabel = 'Overloaded Truck';
    this.currentScenario = 'overloaded';
    this.oSeq = 4; this.kSeq = 2; this.vSeq = 2; this.rSeq = 2;
    this.request = {
      temperature: 15, hour: 10, dayOfWeek: 3,
      orders: [
        { id: 'O-1', destination: 'Beograd',   weightKg: 1500, cargoType: 'STANDARD', deliveryDeadlineMin: 300, priority: 'NORMAL', status: 'ASSIGNED' as any, routeId: 'R-1', assignedTruckId: 'K-1', delayMin: 0 },
        { id: 'O-2', destination: 'Novi Sad',   weightKg: 1500, cargoType: 'STANDARD', deliveryDeadlineMin: 300, priority: 'NORMAL', status: 'ASSIGNED' as any, routeId: 'R-1', assignedTruckId: 'K-1', delayMin: 0 },
        { id: 'O-3', destination: 'Zrenjanin',  weightKg: 1500, cargoType: 'STANDARD', deliveryDeadlineMin: 300, priority: 'NORMAL', status: 'ASSIGNED' as any, routeId: 'R-1', assignedTruckId: 'K-1', delayMin: 0 },
      ],
      trucks: [{
        id: 'K-1', type: 'MEDIUM', maxCapacityKg: 4000, status: 'BUSY',
        location: 'Novi Sad', fuelPercent: 80, hasRefrigerationUnit: false,
        hasAdrEquipment: false, distanceToOriginKm: 0, daysSinceRefrigerationService: 0
      }],
      drivers: [{
        id: 'V-1', available: true, workingHoursToday: 5, license: 'CE',
        hasAdrLicense: false, fatigueLevel: 2, yearsOfExperience: 5, recentRouteIds: []
      }],
      routes: [{
        id: 'R-1', roadType: 'REGIONAL', distanceKm: 80, estimatedTimeHours: 1,
        maxCapacityKg: 24000, maxSpeedKmh: 90, hasTunnel: false
      }]
    };
  }

  loadAvgDelayDemo() {
    this.demoMode = true;
    this.demoLabel = 'Route Avg Delay';
    this.currentScenario = 'delay_route';
    this.oSeq = 3; this.kSeq = 3; this.vSeq = 2; this.rSeq = 2;
    this.request = {
      temperature: 15, hour: 10, dayOfWeek: 3,
      orders: [
        { id: 'O-1', destination: 'Beograd', weightKg: 1000, cargoType: 'STANDARD', deliveryDeadlineMin: 300, priority: 'NORMAL', status: 'IN_PROGRESS' as any, routeId: 'R-1', assignedTruckId: 'K-1', delayMin: 35 },
        { id: 'O-2', destination: 'Beograd', weightKg: 1000, cargoType: 'STANDARD', deliveryDeadlineMin: 300, priority: 'NORMAL', status: 'IN_PROGRESS' as any, routeId: 'R-1', assignedTruckId: 'K-2', delayMin: 45 },
      ],
      trucks: [
        { id: 'K-1', type: 'MEDIUM', maxCapacityKg: 5000, status: 'BUSY', location: 'Beograd', fuelPercent: 80, hasRefrigerationUnit: false, hasAdrEquipment: false, distanceToOriginKm: 0, daysSinceRefrigerationService: 0 },
        { id: 'K-2', type: 'MEDIUM', maxCapacityKg: 5000, status: 'BUSY', location: 'Beograd', fuelPercent: 75, hasRefrigerationUnit: false, hasAdrEquipment: false, distanceToOriginKm: 0, daysSinceRefrigerationService: 0 },
      ],
      drivers: [{
        id: 'V-1', available: true, workingHoursToday: 5, license: 'CE',
        hasAdrLicense: false, fatigueLevel: 2, yearsOfExperience: 5, recentRouteIds: []
      }],
      routes: [{
        id: 'R-1', roadType: 'REGIONAL', distanceKm: 80, estimatedTimeHours: 1,
        maxCapacityKg: 24000, maxSpeedKmh: 90, hasTunnel: false
      }]
    };
  }

  // ---- CRUD ----

  addOrder() {
    this.request.orders.push({
      id: `O-${this.oSeq++}`, destination: '', weightKg: 1000, cargoType: 'STANDARD',
      deliveryDeadlineMin: 180, priority: 'NORMAL', status: 'NEW',
      routeId: this.currentRoutes[0]?.id ?? '', assignedTruckId: '', delayMin: 0
    });
  }
  removeOrder(i: number) { this.request.orders.splice(i, 1); }

  addTruck() {
    this.request.trucks.push({
      id: `K-${this.kSeq++}`, type: 'MEDIUM', maxCapacityKg: 5000, status: 'AVAILABLE',
      location: 'Novi Sad', fuelPercent: 100, hasRefrigerationUnit: false,
      hasAdrEquipment: false, distanceToOriginKm: 10, daysSinceRefrigerationService: 0
    });
  }
  removeTruck(i: number) { this.request.trucks.splice(i, 1); }

  addDriver() {
    this.request.drivers.push({
      id: `V-${this.vSeq++}`, available: true, workingHoursToday: 0, license: 'CE',
      hasAdrLicense: false, fatigueLevel: 0, yearsOfExperience: 1, recentRouteIds: []
    });
  }
  removeDriver(i: number) { this.request.drivers.splice(i, 1); }

  addRoute() {
    this.request.routes.push({
      id: `R-${this.rSeq++}`, roadType: 'REGIONAL', distanceKm: 80, estimatedTimeHours: 1,
      maxCapacityKg: 24000, maxSpeedKmh: 90, hasTunnel: false
    });
  }
  removeRoute(i: number) { this.request.routes.splice(i, 1); }
}
