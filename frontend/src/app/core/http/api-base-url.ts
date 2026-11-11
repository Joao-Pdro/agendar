import { InjectionToken } from '@angular/core';

/** URL base da API, injetada a partir de `environment.apiUrl`. */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL');
