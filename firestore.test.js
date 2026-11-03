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

test("Unauthenticated user: cannot read loans", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("loans").get());
});

test("Authenticated user: cannot read another user's loan", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("loans").doc("loan_1").set({
      userId: BOB_UID,
      clientName: "John Doe",
      clientAddress: "123 Main St",
      clientPhone: "555-1234",
      principal: 1000,
      loanTerm: "30 days",
      dueDate: "2026-12-01",
      status: "active",
      totalPaid: 0,
      remainingBalance: 1000,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("loans").doc("loan_1").get());
});

test("Authenticated user: can create and read their own loan", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("loans").doc("loan_1").set({
      userId: ALICE_UID,
      clientName: "Jane Smith",
      clientAddress: "456 Market St",
      clientPhone: "555-5678",
      principal: 500,
      loanTerm: "2 months",
      dueDate: "2026-12-15",
      notes: "First time client",
      status: "active",
      totalPaid: 0,
      remainingBalance: 500,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("loans").doc("loan_1").get()
  );
});

test("Authenticated user: can add payment to their own loan", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("loans").doc("loan_1").set({
      userId: ALICE_UID,
      clientName: "Jane Smith",
      clientAddress: "456 Market St",
      clientPhone: "555-5678",
      principal: 500,
      loanTerm: "2 months",
      dueDate: "2026-12-15",
      status: "active",
      totalPaid: 0,
      remainingBalance: 500,
      createdAt: now,
      updatedAt: now,
    });
  });

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("loans").doc("loan_1").collection("payments").doc("payment_1").set({
      userId: ALICE_UID,
      loanId: "loan_1",
      amount: 150,
      paymentDate: "2026-11-05",
      notes: "Cash installment",
      createdAt: now,
    })
  );
});
