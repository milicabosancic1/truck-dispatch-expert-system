import { Component } from '@angular/core';
import { DispatchPanelComponent } from './components/dispatch-panel/dispatch-panel.component';
import { ResultsPanelComponent } from './components/results-panel/results-panel.component';
import { CepPanelComponent } from './components/cep-panel/cep-panel.component';
import { FleetManagementComponent } from './components/fleet-management/fleet-management.component';
import { AlarmsPanelComponent } from './components/alarms-panel/alarms-panel.component';
import { DispatchResult } from './models/models';

@Component({
  selector: 'app-root',
  imports: [DispatchPanelComponent, ResultsPanelComponent, CepPanelComponent, FleetManagementComponent, AlarmsPanelComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  appMode: 'landing' | 'demo' | 'dispatcher' | 'admin' = 'landing';
  tab: 'fc' | 'cep' | 'accumulate' = 'fc';
  demoRole: 'dispatcher' | 'admin' = 'dispatcher';

  fcResult:  DispatchResult | null = null;
  accResult: DispatchResult | null = null;

  get currentRole(): 'dispatcher' | 'admin' {
    if (this.appMode === 'demo') return this.demoRole;
    if (this.appMode === 'admin') return 'admin';
    return 'dispatcher';
  }

  get isDemo(): boolean { return this.appMode === 'demo'; }

  enter(mode: 'demo' | 'dispatcher' | 'admin') {
    this.appMode = mode;
    this.demoRole = 'dispatcher';
    this.tab = 'fc';
    this.fcResult = null;
    this.accResult = null;
  }

  home() {
    this.appMode = 'landing';
    this.demoRole = 'dispatcher';
    this.tab = 'fc';
    this.fcResult = null;
    this.accResult = null;
  }

  onFcResult(r: DispatchResult)  { this.fcResult  = r; }
  onAccResult(r: DispatchResult) { this.accResult = r; }
}
