const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_student_123";
const BOB_UID = "bob_student_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validSettings(overrides = {}) {
  return {
    appThemeStyle: "STANDARD",
    darkModePreference: "SYSTEM",
    agendaStyle: "COMPACT",
    uiScale: 1.0,
    eloUrl: "https://elo.example.nl",
    savedBusLine: "300",
    uitvalNotificationsEnabled: true,
    gradeNotificationsEnabled: true,
    testNotificationsEnabled: true,
    quietHoursEnabled: true,
    quietHoursStartMinutes: 1320,
    quietHoursEndMinutes: 420,
    mutedSubjects: ["gym"],
    smartBusAlertsEnabled: true,
    busWalkMinutes: 5,
    busArrivalBufferMinutes: 10,
    gradeUnboxingEnabled: true,
    gradeUnboxingMode: "CASE_OPENING",
    ...overrides,
  };
}

function validProfile(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    uid,
    email: `${uid}@example.com`,
    displayName: "Alice Student",
    studentName: "Alice van Dijk",
    studentId: "S1042",
    studentClass: "5V2",
    personId: 98765,
    schoolId: "1",
    schoolName: "St. Ignatius Gymnasium",
    schoolCity: "Amsterdam",
    schoolSubdomain: "ignatius",
    schoolProvider: "MAGISTER",
    magisterAccessToken: "eyJ_mock_access_token_payload",
    magisterRefreshToken: "refresh_token_payload",
    magisterAuthenticated: true,
    settings: validSettings(),
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write /users/{uid}", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID))
  );
});

test("Owner can create and update their own UserCloudProfile and read it back", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(docRef.set(validProfile(ALICE_UID)));
  await assertSucceeds(docRef.get());

  await assertSucceeds(
    docRef.update({
      magisterAccessToken: "eyJ_refreshed_token",
      settings: validSettings({
        appThemeStyle: "ONE_UI",
        darkModePreference: "DARK",
        agendaStyle: "SPACIOUS",
        gradeUnboxingMode: "PLINKO_DROP",
      }),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user read, create, update, and delete are strictly denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).set(validProfile(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).set(validProfile(ALICE_UID)));
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(aliceDb.collection("users").doc(BOB_UID).delete());
});

test("Rejects identity spoofing, ghost fields, @DocumentId 'id', immutable createdAt mutation, and invalid enums/bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Identity spoofing
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(BOB_UID))
  );

  // Ghost field in create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validProfile(ALICE_UID),
      isAdmin: true,
    })
  );

  // Forbidden @DocumentId 'id' inside document payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validProfile(ALICE_UID),
      id: ALICE_UID,
    })
  );

  // Invalid theme enum (e.g. unsupported value)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(
      validProfile(ALICE_UID, {
        settings: validSettings({ appThemeStyle: "INVALID_THEME" }),
      })
    )
  );

  // Invalid agendaStyle enum (must reject 'DETAILED')
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(
      validProfile(ALICE_UID, {
        settings: validSettings({ agendaStyle: "DETAILED" }),
      })
    )
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(
      validProfile(ALICE_UID, {
        updatedAt: new Date(Date.now() + 60000),
      })
    )
  );

  // Valid create, then reject mutating createdAt or uid
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID))
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 50),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      uid: BOB_UID,
      updatedAt: new Date(Date.now() - 50),
    })
  );
});
