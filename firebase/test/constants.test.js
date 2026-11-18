// Design principle 8: one source of truth. contracts/constants.json is the
// contract; this fails if firestore.rules drifts from it. ConstantsParityTest
// in the app does the same for Constants.java.

const fs = require('fs');
const path = require('path');
const h = require('./helpers');

const constants = JSON.parse(
  fs.readFileSync(path.join(h.CONTRACTS_DIR, 'constants.json'), 'utf8'),
);
const rules = fs.readFileSync(h.RULES_PATH, 'utf8');

/** Every `function NAME() { return <number>; }` in the rules file. */
function numericConstantsInRules() {
  const found = {};
  for (const [, name, value] of rules.matchAll(/function ([A-Z][A-Z0-9_]*)\(\) \{ return (-?[0-9.]+); \}/g)) {
    found[name] = Number(value);
  }
  return found;
}

const ENFORCED_BY_RULES = [
  'TOKEN_VALIDITY_S',
  'SESSION_MIN_MIN',
  'SESSION_MAX_MIN',
  'SESSION_SKEW_MIN',
  'GEO_RADIUS_MIN_M',
  'GEO_RADIUS_MAX_M',
  'MAX_ACCURACY_M',
  'QUEUE_GRACE_MIN',
  'THRESHOLD_MIN',
  'THRESHOLD_MAX',
  'NOTE_MAX_LEN',
  'METERS_PER_DEGREE',
  'ALERT_RETENTION_DAYS',
  'CORRECTION_RETENTION_DAYS',
  'HEADCOUNT_MAX',
];

describe('constants parity (rules)', () => {
  const inRules = numericConstantsInRules();

  test('the rules declare exactly the constants they are expected to enforce', () => {
    expect(Object.keys(inRules).sort()).toEqual([...ENFORCED_BY_RULES].sort());
  });

  test.each(ENFORCED_BY_RULES)('%s matches contracts/constants.json', (name) => {
    expect(constants[name]).toBeDefined();
    expect(inRules[name]).toBe(constants[name]);
  });

  test('the join code pattern matches the contract alphabet and length', () => {
    const pattern = `'^[${constants.JOIN_CODE_ALPHABET}]{${constants.JOIN_CODE_LENGTH}}$'`;
    expect(rules).toContain(pattern);
  });

  test('the token pattern matches the contract length', () => {
    expect(rules).toContain(`'^[A-Za-z0-9_-]{${constants.TOKEN_LENGTH}}$'`);
    // 16 random bytes are 22 base64url characters without padding.
    expect(Math.ceil((constants.TOKEN_BYTES * 8) / 6)).toBe(constants.TOKEN_LENGTH);
  });

  test('the student domain matches the contract', () => {
    expect(rules).toContain(`function STUDENT_DOMAIN() { return '${constants.STUDENT_DOMAIN}'; }`);
    expect(constants.STUDENT_DOMAIN).toBe(constants.STUDENT_DOMAIN.toLowerCase());
  });

  test('the roll number and cohort patterns match the contract lengths', () => {
    expect(rules).toContain(`'^[0-9]{${constants.ROLL_NO_LENGTH}}$'`);
    expect(rules).toContain(
      `'^[A-Z0-9]{${constants.COHORT_MIN_LEN},${constants.COHORT_MAX_LEN}}$'`,
    );
  });

  test('the join code alphabet excludes look-alike characters', () => {
    expect(constants.JOIN_CODE_ALPHABET).not.toMatch(/[O0I1]/);
    expect(new Set(constants.JOIN_CODE_ALPHABET).size).toBe(constants.JOIN_CODE_ALPHABET.length);
  });
});
