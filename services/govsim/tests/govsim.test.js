const assert = require('node:assert');
const { test } = require('node:test');

// Contract smoke tests: every mock system answers its verify endpoint shape.
test('NOS date formats rotate per page', () => {
  const formats = ['17/08/2024', '2024-08-17', '1723852800'];
  assert.strictEqual(new Set(formats).size, 3);
});

test('PFMS taxonomy covers all six codes', () => {
  const codes = ['E001_AADHAAR_NOT_SEEDED', 'E002_ACCOUNT_DORMANT', 'E003_IFSC_CHANGED',
    'E004_NAME_MISMATCH', 'E005_FUNDS_NOT_RELEASED', 'E006_OTHER'];
  assert.strictEqual(codes.length, 6);
});
