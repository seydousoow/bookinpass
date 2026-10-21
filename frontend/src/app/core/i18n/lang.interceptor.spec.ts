import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslocoService } from '@jsverse/transloco';
import { APP_CONFIG } from '@core/config/app-config.token';
import { langInterceptor } from './lang.interceptor';

describe('langInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let lang: string;

  beforeEach(() => {
    lang = 'en';
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([langInterceptor])),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { production: false, apiBaseUrl: 'https://api.test/api' } },
        { provide: TranslocoService, useValue: { getActiveLang: () => lang } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('sends the active language to the API', () => {
    http.get('https://api.test/api/flights/offers/off_1').subscribe();
    lang = 'fr';
    http.post('https://api.test/api/flights/orders', {}).subscribe();

    const [offer, order] = backend.match(() => true);
    expect(offer.request.headers.get('Accept-Language')).toBe('en');
    expect(order.request.headers.get('Accept-Language')).toBe('fr');
    offer.flush({});
    order.flush({});
  });

  it('leaves other requests untouched', () => {
    http.get('/i18n/fr.json').subscribe();

    const request = backend.expectOne('/i18n/fr.json');
    expect(request.request.headers.has('Accept-Language')).toBe(false);
    request.flush({});
  });
});
