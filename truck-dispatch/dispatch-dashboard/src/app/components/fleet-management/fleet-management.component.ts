import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DispatchService } from '../../services/dispatch.service';

@Component({
  selector: 'app-fleet-management',
  imports: [CommonModule, FormsModule],
  templateUrl: './fleet-management.component.html',
  styleUrl: './fleet-management.component.css'
})
export class FleetManagementComponent implements OnInit {

  trucks:  any[] = [];
  drivers: any[] = [];
  routes:  any[] = [];

  loading  = false;
  saving   = false;
  saved    = false;
  error    = '';

  truckTypes   = ['SMALL', 'MEDIUM', 'LARGE'];
  truckStatuses = ['AVAILABLE', 'BUSY', 'BREAKDOWN', 'SERVICE'];
  roadTypes    = ['CITY', 'LOCAL', 'REGIONAL', 'HIGHWAY'];
  licenses     = ['B', 'C', 'CE'];

  private kSeq = 1;
  private vSeq = 1;
  private rSeq = 1;

  constructor(private dispatchService: DispatchService) {}

  ngOnInit() { this.load(); }

  load() {
    this.loading = true;
    this.dispatchService.getFleet().subscribe({
      next: f => {
        this.trucks  = f.trucks  ? JSON.parse(JSON.stringify(f.trucks))  : [];
        this.drivers = f.drivers ? JSON.parse(JSON.stringify(f.drivers)) : [];
        this.routes  = f.routes  ? JSON.parse(JSON.stringify(f.routes))  : [];
        this.kSeq = this.trucks.length  + 1;
        this.vSeq = this.drivers.length + 1;
        this.rSeq = this.routes.length  + 1;
        this.loading = false;
      },
      error: () => { this.error = 'Nije moguće učitati flotu.'; this.loading = false; }
    });
  }

  save() {
    this.saving = true;
    this.saved  = false;
    this.error  = '';
    this.dispatchService.saveFleet({ trucks: this.trucks, drivers: this.drivers, routes: this.routes })
      .subscribe({
        next: () => { this.saving = false; this.saved = true; setTimeout(() => this.saved = false, 3000); },
        error: () => { this.saving = false; this.error = 'Greška pri čuvanju.'; }
      });
  }

  addTruck() {
    this.trucks.push({
      id: `K-${this.kSeq++}`, type: 'MEDIUM', maxCapacityKg: 5000,
      status: 'AVAILABLE', location: 'Novi Sad', fuelPercent: 100,
      hasRefrigerationUnit: false, hasAdrEquipment: false,
      distanceToOriginKm: 0, daysSinceRefrigerationService: 0
    });
  }
  removeTruck(i: number) { this.trucks.splice(i, 1); }

  addDriver() {
    this.drivers.push({
      id: `V-${this.vSeq++}`, available: true, workingHoursToday: 0,
      license: 'CE', hasAdrLicense: false, fatigueLevel: 0,
      yearsOfExperience: 1, recentRouteIds: []
    });
  }
  removeDriver(i: number) { this.drivers.splice(i, 1); }

  addRoute() {
    this.routes.push({
      id: `R-${this.rSeq++}`, roadType: 'REGIONAL', distanceKm: 80,
      estimatedTimeHours: 1, hasTunnel: false, maxCapacityKg: 24000, maxSpeedKmh: 90
    });
  }
  removeRoute(i: number) { this.routes.splice(i, 1); }
}
