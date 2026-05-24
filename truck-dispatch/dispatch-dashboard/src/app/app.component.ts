import { Component } from '@angular/core';
import { DispatchPanelComponent } from './components/dispatch-panel/dispatch-panel.component';
import { ResultsPanelComponent } from './components/results-panel/results-panel.component';
import { CepPanelComponent } from './components/cep-panel/cep-panel.component';
import { AlarmsPanelComponent } from './components/alarms-panel/alarms-panel.component';
import { DispatchResult } from './models/models';

@Component({
  selector: 'app-root',
  imports: [DispatchPanelComponent, ResultsPanelComponent, CepPanelComponent, AlarmsPanelComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  tab: 'dispatch' | 'events' | 'alarms' = 'dispatch';
  result: DispatchResult | null = null;

  onResult(result: DispatchResult) {
    this.result = result;
  }
}
