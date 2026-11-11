import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { ShellComponent } from './shell.component';
import { provideApi } from '../http/provide-api';

@Component({ selector: 'app-stub', standalone: true, template: '' })
class StubComponent {}

describe('ShellComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [
        provideRouter([{ path: '**', component: StubComponent }]),
        provideApi(''),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
  });

  it('should create the shell', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the navigation with the agenda link', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    const links = Array.from(
      fixture.nativeElement.querySelectorAll('a.shell__link'),
    ) as HTMLAnchorElement[];

    expect(links.map((link) => link.textContent?.trim())).toEqual([
      'Início',
      'Agenda',
    ]);
    expect(links[1].getAttribute('href')).toBe('/agenda');
  });

  it('should render a router outlet for the lazy features', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('main router-outlet'),
    ).not.toBeNull();
  });
});
