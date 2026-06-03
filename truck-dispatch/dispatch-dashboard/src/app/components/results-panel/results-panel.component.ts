import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DispatchResult, OrderStatus } from '../../models/models';

@Component({
  selector: 'app-results-panel',
  imports: [CommonModule],
  templateUrl: './results-panel.component.html',
  styleUrl: './results-panel.component.css'
})
export class ResultsPanelComponent {
  @Input() result!: DispatchResult;

  count(status: OrderStatus): number {
    return this.result.processedOrders.filter(order => order.status === status).length;
  }

  get assignedCount(): number {
    return this.count('ASSIGNED');
  }

  get waitingCount(): number {
    return this.count('WAITING_RESOURCES') + this.count('WAITING_UNLOADING') + this.count('POSTPONED_UNTIL_MORNING');
  }

  get issueCount(): number {
    return this.count('UNFEASIBLE') + this.result.alarms.length;
  }
}
