import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DispatchResult } from '../../models/models';

@Component({
  selector: 'app-results-panel',
  imports: [CommonModule],
  templateUrl: './results-panel.component.html',
  styleUrl: './results-panel.component.css'
})
export class ResultsPanelComponent {
  @Input() result!: DispatchResult;
}
