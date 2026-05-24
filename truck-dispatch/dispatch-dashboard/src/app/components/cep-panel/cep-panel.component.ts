import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DispatchService } from '../../services/dispatch.service';
import { FleetEvent, FleetEventType } from '../../models/models';

@Component({
  selector: 'app-cep-panel',
  imports: [CommonModule, FormsModule],
  templateUrl: './cep-panel.component.html',
  styleUrl: './cep-panel.component.css'
})
export class CepPanelComponent {
  eventTypes: FleetEventType[] = ['DELAY', 'POSITION', 'BREAKDOWN', 'FUEL_LEVEL', 'NEW_ORDER'];

  event: FleetEvent = { type: 'DELAY', entityId: '', value: 0, location: '' };

  messages: string[] = [];
  loading = false;
  error = '';

  constructor(private dispatchService: DispatchService) {}

  send() {
    this.loading = true;
    this.error = '';
    this.messages = [];
    this.dispatchService.sendEvent(this.event).subscribe({
      next: msgs => { this.messages = msgs; this.loading = false; },
      error: err => { this.error = err.message || 'Error sending event'; this.loading = false; }
    });
  }
}
