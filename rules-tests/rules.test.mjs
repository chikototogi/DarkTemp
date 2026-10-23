import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { ref, set, get, update, remove, query, orderByChild, limitToLast, runTransaction } from 'firebase/database';

// This project ID is emulator-only. No credentials or live project are used.
const deviceUid = 'test-device-uid';
let environment;
const db = (uid, email = `${uid}@example.test`) => environment.authenticatedContext(uid, { email }).database();
const profile = (email = 'alice@example.test') => ({ firstName: 'Test', lastName: 'User', email, createdAt: Date.now(), preferences: {
  temperatureUnit: 'C', temperatureAlertsEnabled: true, lightAlertsEnabled: true, minimumTemperatureC: 18, maximumTemperatureC: 35,
} });
const sample = (timestamp = Date.now()) => ({ deviceId: 'device001', lightValue: 1800, lightStatus: 'LIGHT', temperature: 36, lastUpdated: timestamp });
const history = timestamp => { const { lastUpdated, ...reading } = sample(timestamp); return { ...reading, timestamp: lastUpdated }; };

before(async () => {
  const rules = (await readFile(new URL('../database.rules.json', import.meta.url), 'utf8')).replaceAll('REPLACE_WITH_DEVICE_AUTH_UID', deviceUid);
  environment = await initializeTestEnvironment({ projectId: 'demo-darktemp', database: { host: '127.0.0.1', port: 9000, rules } });
});
beforeEach(async () => environment.clearDatabase());
after(async () => environment?.cleanup());

test('anonymous clients cannot read or write users or sensors', async () => {
  const anonymous = environment.unauthenticatedContext().database();
  await assertFails(get(ref(anonymous, 'devices/device001/latest')));
  await assertFails(set(ref(anonymous, 'devices/device001/latest'), sample()));
  await assertFails(get(ref(anonymous, 'users/alice')));
  await assertFails(set(ref(anonymous, 'users/alice'), profile()));
});

test('users create/read only their own profile and cannot delete required fields', async () => {
  const alice = db('alice');
  await assertSucceeds(set(ref(alice, 'users/alice'), profile()));
  await assertSucceeds(get(ref(alice, 'users/alice')));
  await assertFails(get(ref(db('bob'), 'users/alice')));
  await assertFails(set(ref(db('bob'), 'users/alice'), profile()));
  await assertFails(remove(ref(alice, 'users/alice/preferences')));
  await assertFails(remove(ref(alice, 'users/alice/firstName')));
  await assertFails(remove(ref(alice, 'users/alice')));
  await assertFails(update(ref(alice, 'users/alice'), { email: 'someone@example.test' }));
});

test('device can write a real atomic sampling pair; app users only read sensors', async () => {
  const device = db(deviceUid);
  const timestamp = Date.now();
  await assertSucceeds(update(ref(device, 'devices/device001'), { latest: sample(timestamp), 'history/sample-a': history(timestamp) }));
  await assertSucceeds(get(ref(db('alice'), 'devices/device001/latest')));
  await assertSucceeds(get(query(ref(db('alice'), 'devices/device001/history'), orderByChild('timestamp'), limitToLast(100))));
  await assertFails(set(ref(db('alice'), 'devices/device001/latest'), sample()));
  await assertFails(set(ref(device, `users/${deviceUid}`), profile(`${deviceUid}@example.test`)));
  await assertFails(get(ref(device, `users/${deviceUid}`)));
  await assertFails(set(ref(device, 'devices/device002/latest'), sample()));
  await assertFails(remove(ref(device, 'devices/device001/latest')));
});

test('history permits only identical retries and malformed, stale, future, and regressing samples fail', async () => {
  const device = db(deviceUid);
  const timestamp = Date.now();
  const path = ref(device, 'devices/device001/latest');
  await assertSucceeds(set(path, sample(timestamp)));
  for (const invalid of [
    { ...sample(), temperature: '36' }, { ...sample(), temperature: 100 },
    { ...sample(), lightStatus: 'POWER_OUTAGE' }, { ...sample(), lightValue: 1.5 },
    { ...sample(), extra: true }, sample(timestamp - 1), sample(Date.now() + 60_000), sample(Date.now() - 180_000),
  ]) await assertFails(set(path, invalid));
  await assertSucceeds(set(ref(device, 'devices/device001/history/sample-a'), history(timestamp)));
  await assertSucceeds(set(ref(device, 'devices/device001/history/sample-a'), history(timestamp)));
  await assertFails(set(ref(device, 'devices/device001/history/sample-a'), { ...history(timestamp), temperature: 37 }));
  await assertFails(remove(ref(device, 'devices/device001/history/sample-a')));
});

test('preferences reject reversed limits, invalid units, and extra fields', async () => {
  const alice = db('alice');
  const path = ref(alice, 'users/alice');
  await assertSucceeds(set(path, profile()));
  await assertFails(update(ref(alice, 'users/alice/preferences'), { minimumTemperatureC: 40 }));
  await assertFails(update(ref(alice, 'users/alice/preferences'), { temperatureUnit: 'K' }));
  await assertFails(update(ref(alice, 'users/alice/preferences'), { unknown: true }));
  await assertSucceeds(update(ref(alice, 'users/alice/preferences'), { temperatureUnit: 'F', minimumTemperatureC: 20 }));
});

test('light alerts are valid account alerts and retain optional preference migration', async () => {
  const alice = db('alice');
  const user = ref(alice, 'users/alice');
  const timestamp = Date.now();
  const lightAlert = {
    type: 'LIGHT_CHANGED_TO_DARK', temperatureC: 24.5, timestamp, isRead: false,
  };
  await assertSucceeds(set(user, profile()));
  await assertSucceeds(update(ref(alice, 'users/alice'), {
    alertState: {
      lastProcessedReadingTimestamp: timestamp,
      lastTemperatureZone: 'NORMAL',
      lastLightStatus: 'DARK',
    },
    [`alerts/device001_${timestamp}_light`]: lightAlert,
  }));
  await assertSucceeds(update(ref(alice, 'users/alice/preferences'), { lightAlertsEnabled: false }));
  await assertFails(set(ref(alice, `users/alice/alerts/device001_${timestamp + 1}_light`), {
    ...lightAlert, timestamp: timestamp + 1, thresholdC: 18,
  }));
  await assertFails(set(ref(alice, `users/alice/alerts/device001_${timestamp + 2}`), {
    ...lightAlert, type: 'LIGHT_CHANGED_TO_DARK',
  }));

  await assertSucceeds(update(ref(alice, 'users/alice/preferences'), { lightAlertsEnabled: null }));
  await assertSucceeds(update(ref(alice, 'users/alice/alertState'), {
    lastProcessedReadingTimestamp: timestamp,
    lastTemperatureZone: 'NORMAL',
  }));
});

test('an atomic alert decision survives inbox clearing and cannot cross user boundaries', async () => {
  const alice = db('alice');
  const timestamp = Date.now();
  const id = `device001_${timestamp}`;
  await assertSucceeds(set(ref(alice, 'users/alice'), profile()));
  const alert = { type: 'TEMPERATURE_HIGH', temperatureC: 36, thresholdC: 35, timestamp, isRead: false };
  await assertSucceeds(update(ref(alice, 'users/alice'), {
    alertState: { lastProcessedReadingTimestamp: timestamp, lastTemperatureZone: 'ABOVE' }, [`alerts/${id}`]: alert,
  }));
  await assertFails(update(ref(db('bob'), 'users/alice/alerts'), { [id]: null }));
  await assertFails(get(ref(db('bob'), 'users/alice/alerts')));
  await assertSucceeds(update(ref(alice, `users/alice/alerts/${id}`), { isRead: true }));
  await assertSucceeds(remove(ref(alice, 'users/alice/alerts')));
  assert.equal((await get(ref(alice, 'users/alice/alertState/lastProcessedReadingTimestamp'))).val(), timestamp);
  await assertFails(remove(ref(alice, 'users/alice/alertState')));
  await assertFails(update(ref(alice, 'users/alice/alertState'), { lastProcessedReadingTimestamp: timestamp - 1 }));
});

test('two sessions committing one reading create one atomic alert and state', async () => {
  const first = db('alice');
  const second = db('alice');
  const timestamp = Date.now();
  await set(ref(first, 'users/alice'), profile());
  const process = database => runTransaction(ref(database, 'users/alice'), current => {
    if (!current) return current;
    if ((current.alertState?.lastProcessedReadingTimestamp ?? 0) >= timestamp) return;
    current.alertState = { lastProcessedReadingTimestamp: timestamp, lastTemperatureZone: 'ABOVE' };
    current.alerts ??= {};
    current.alerts[`device001_${timestamp}`] = { type: 'TEMPERATURE_HIGH', temperatureC: 36, thresholdC: 35, timestamp, isRead: false };
    return current;
  }, { applyLocally: false });
  await Promise.all([get(ref(first, 'users/alice')), get(ref(second, 'users/alice'))]);
  await Promise.all([assertSucceeds(process(first)), assertSucceeds(process(second))]);
  const result = (await get(ref(first, 'users/alice'))).val();
  assert.equal(Object.keys(result.alerts).length, 1);
  assert.equal(result.alertState.lastProcessedReadingTimestamp, timestamp);
});
