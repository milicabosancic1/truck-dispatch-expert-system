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
  tab: 'fc' | 'bc' | 'cep' | 'accumulate' | 'fleet' = 'fc';

  fcResult:  DispatchResult | null = null;
  bcResult:  DispatchResult | null = null;
  accResult: DispatchResult | null = null;

  onFcResult(r: DispatchResult)  { this.fcResult  = r; }
  onBcResult(r: DispatchResult)  { this.bcResult  = r; }
  onAccResult(r: DispatchResult) { this.accResult = r; }
}
