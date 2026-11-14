const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function tsNow() {
  return new Date(Date.now() - 1000);
}

function validProfile(uid) {
  return {
    userId: uid,
    displayName: "Trader Alice",
    voiceAlertsEnabled: true,
    autoMonitorEnabled: true,
    preferredAsset: "EUR/USD",
    createdAt: tsNow(),
    updatedAt: tsNow(),
  };
}

function validSignal(uid) {
  return {
    userId: uid,
    symbol: "EUR/USD",
    direction: "UP",
    confidence: 78,
    entryPrice: 1.0842,
    rsi1m: 34.5,
    macdSignal: "Bullish Crossover",
    emaTrend: "EMA9 > EMA21 > EMA50",
    patternSummary: "Bullish Engulfing at Support",
    urduSummary: "EUR/USD par support se bullish reversal nazar aa raha hai.",
    evidenceSummary: "1m RSI oversold bounce + MACD positive + 5m EMA alignment.",
    dataSource: "Yahoo Finance Live Feed",
    candleExpiryEpochMs: Date.now() + 60000,
    createdAt: tsNow(),
  };
}

function validTrade(uid) {
  return {
    userId: uid,
    signalId: "sig_001",
    symbol: "XAU/USD",
    direction: "UP",
    stakeUsd: 50.0,
    brokerName: "OANDA v20 (Authorized)",
    executionMode: "MANUAL_CONFIRMED",
    status: "CONFIRMED",
    notes: "Manual user authorization confirmed",
    createdAt: tsNow(),
    updatedAt: tsNow(),
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

test("Unauthenticated user: cannot read or write signals or trades", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("signals").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("signals").doc("s1").set(validSignal(ALICE_UID)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("trade_confirmations").get());
});

test("Authenticated owner: can create and read own profile, signals, and trades", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_1").set(validSignal(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("signals").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("trade_confirmations").doc("tr_1").set(validTrade(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("trade_confirmations").get());
});

test("Cross-user isolation: Bob cannot read or write Alice's signals or trades", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_1").set(validSignal(ALICE_UID)));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("signals").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_2").set(validSignal(ALICE_UID)));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("trade_confirmations").doc("tr_2").set(validTrade(BOB_UID)));
});

test("Shadow update & schema validation: rejects ghost fields, bad enum, or signal mutation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const badSignal = { ...validSignal(ALICE_UID), isAdmin: true };
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_bad").set(badSignal));

  const badDirection = { ...validSignal(ALICE_UID), direction: "GUARANTEED_WIN" };
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_dir").set(badDirection));

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_immut").set(validSignal(ALICE_UID)));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_immut").update({ direction: "DOWN" }));
});

test("Trade confirmation terminal lock: can cancel once, cannot re-open after cancelled", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const tradeRef = aliceDb.collection("users").doc(ALICE_UID).collection("trade_confirmations").doc("tr_lock");
  await assertSucceeds(tradeRef.set(validTrade(ALICE_UID)));
  await assertSucceeds(tradeRef.update({ status: "CANCELLED", updatedAt: tsNow() }));
  await assertFails(tradeRef.update({ status: "CONFIRMED", updatedAt: tsNow() }));
});
