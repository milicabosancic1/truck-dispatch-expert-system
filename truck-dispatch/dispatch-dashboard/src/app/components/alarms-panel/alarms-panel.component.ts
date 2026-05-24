import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DispatchService } from '../../services/dispatch.service';
import { Alarm } from '../../models/models';

@Component({
  selector: 'app-alarms-panel',
  imports: [CommonModule],
  templateUrl: './alarms-panel.component.html',
  styleUrl: './alarms-panel.component.css'
})
export class AlarmsPanelComponent implements OnInit {
  alarms: Alarm[] = [];
  loading = false;
  error = '';

  constructor(private dispatchService: DispatchService) {}

  ngOnInit() { this.refresh(); }

  refresh() {
    this.loading = true;
    this.error = '';
    this.dispatchService.getAlarms().subscribe({
      next: alarms => { this.alarms = alarms; this.loading = false; },
      error: err => { this.error = err.message || 'Error loading alarms'; this.loading = false; }
    });
  }
}
