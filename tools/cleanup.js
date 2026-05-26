/**
 * SwiftNote — Firebase Realtime Database One-Time Cleanup Script
 *
 * WHAT IT DOES
 * ────────────
 * 1. Deletes the legacy top-level /notes/ node (old schema, never read by current app).
 * 2. Identifies UUID-shaped account keys under /users/ that haven't synced in 90 days.
 * 3. By default runs as a DRY RUN — prints what would be deleted, deletes nothing.
 *    Set the DELETE environment variable to "true" to actually delete.
 *
 * SETUP
 * ─────
 * 1. Download a service account key JSON:
 *      Firebase Console → Project Settings → Service Accounts → Generate New Private Key
 *    Save it next to this file as:  tools/service-account-key.json
 *    ⚠️  Treat this file like a password — do NOT commit it to git.
 *
 * 2. Install dependencies (run once from the tools/ directory):
 *      npm install firebase-admin
 *
 * 3. Dry-run (safe, shows what would be deleted):
 *      node cleanup.js
 *
 * 4. Actually delete:
 *      $env:DELETE="true"; node cleanup.js          # PowerShell
 *      DELETE=true node cleanup.js                  # bash / macOS
 *
 * SAFETY RULES
 * ────────────
 * - 16-hex-char keys (e.g. "2ae571a095343c67")  →  KEPT — these are real device IDs.
 * - UUID-shaped keys (e.g. "8b4535cb-0782-4a67") →  CANDIDATE for deletion if stale.
 * - Only deletes UUID accounts whose lastSyncAt (or createdAt) is older than CUTOFF_DAYS.
 * - Legacy /notes/ top-level node is always deleted (zero current code reads/writes it).
 *
 * Run the dry-run FIRST, review the console output,
 * then re-run with DELETE=true only if you're satisfied.
 */

'use strict';

const path = require('path');
const admin = require('firebase-admin');

// ── Configuration ──────────────────────────────────────────────────────────────

const SERVICE_ACCOUNT_PATH = path.join(__dirname, 'service-account-key.json');
const DATABASE_URL = 'https://self-note-636a2-default-rtdb.firebaseio.com';

// Accounts last active more than this many days ago are deletion candidates.
const CUTOFF_DAYS = 90;

// ── Derived constants ──────────────────────────────────────────────────────────

// UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (36 chars, 5 hyphen-separated groups)
const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// 16-char hex device ID (no hyphens) — these are always KEPT
const DEVICE_ID_REGEX = /^[0-9a-f]{16}$/i;

const CUTOFF_MS = Date.now() - CUTOFF_DAYS * 24 * 60 * 60 * 1000;
const IS_DRY_RUN = process.env.DELETE !== 'true';

// ── Helpers ────────────────────────────────────────────────────────────────────

function formatDate(ms) {
    if (!ms || ms === 0) return 'unknown';
    return new Date(ms).toISOString().replace('T', ' ').substring(0, 19) + ' UTC';
}

function accountType(key) {
    if (UUID_REGEX.test(key)) return 'UUID (orphan candidate)';
    if (DEVICE_ID_REGEX.test(key)) return 'device-ID (keep)';
    return 'unknown format';
}

// ── Main ───────────────────────────────────────────────────────────────────────

async function main() {
    // Guard: make sure the service account file exists before trying to init
    let serviceAccount;
    try {
        serviceAccount = require(SERVICE_ACCOUNT_PATH);
    } catch (e) {
        console.error(`\n❌  Cannot find service-account-key.json at:\n    ${SERVICE_ACCOUNT_PATH}`);
        console.error('\nDownload it from:');
        console.error('  Firebase Console → Project Settings → Service Accounts → Generate New Private Key\n');
        process.exit(1);
    }

    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount),
        databaseURL: DATABASE_URL,
    });

    const db = admin.database();

    console.log('═══════════════════════════════════════════════════════════════');
    console.log(' SwiftNote Firebase Cleanup');
    console.log(`  Mode    : ${IS_DRY_RUN ? '🔍 DRY RUN (nothing will be deleted)' : '🗑️  LIVE DELETE'}`);
    console.log(`  Cutoff  : ${CUTOFF_DAYS} days  (${formatDate(CUTOFF_MS)})`);
    console.log(`  DB      : ${DATABASE_URL}`);
    console.log('═══════════════════════════════════════════════════════════════\n');

    // ── Step 1: Legacy top-level /notes/ node ──────────────────────────────────
    console.log('── Step 1: Legacy top-level /notes/ ────────────────────────────');
    const legacyNotesSnap = await db.ref('notes').once('value');
    if (legacyNotesSnap.exists()) {
        const childCount = legacyNotesSnap.numChildren();
        console.log(`  Found ${childCount} children under /notes/ (old schema, never read by current app)`);
        if (!IS_DRY_RUN) {
            await db.ref('notes').remove();
            console.log('  ✅ Deleted /notes/');
        } else {
            console.log('  [DRY RUN] Would delete /notes/');
        }
    } else {
        console.log('  ✅ /notes/ does not exist — nothing to do');
    }
    console.log();

    // ── Step 2: Inventory /users/ ──────────────────────────────────────────────
    console.log('── Step 2: Inventory /users/ ───────────────────────────────────');
    const usersSnap = await db.ref('users').once('value');

    if (!usersSnap.exists()) {
        console.log('  /users/ is empty — nothing to check');
        await shutdown(db);
        return;
    }

    const users = usersSnap.val();
    const allKeys = Object.keys(users);

    console.log(`  Total accounts: ${allKeys.length}\n`);

    const toDelete = [];       // UUID accounts that are stale
    const toKeep = [];         // device-ID accounts (always kept)
    const uuidActive = [];     // UUID accounts that are recent (warn but don't delete)
    const unknownFormat = [];  // keys that aren't UUID or device-ID (just report)

    for (const [accountId, data] of Object.entries(users)) {
        const lastSync = (data && (data.lastSyncAt || data.createdAt)) || 0;
        const entry = { accountId, lastSync, type: accountType(accountId) };

        if (DEVICE_ID_REGEX.test(accountId)) {
            toKeep.push(entry);
        } else if (UUID_REGEX.test(accountId)) {
            if (lastSync < CUTOFF_MS) {
                toDelete.push(entry);
            } else {
                uuidActive.push(entry);
            }
        } else {
            unknownFormat.push(entry);
        }
    }

    // Print kept accounts
    if (toKeep.length > 0) {
        console.log(`  ✅ Keeping ${toKeep.length} device-ID account(s):`);
        toKeep.forEach(({ accountId, lastSync }) => {
            console.log(`     ${accountId}  (last sync: ${formatDate(lastSync)})`);
        });
        console.log();
    }

    // Print active UUID accounts (warn — not deleting)
    if (uuidActive.length > 0) {
        console.log(`  ⚠️  ${uuidActive.length} UUID account(s) are RECENT (< ${CUTOFF_DAYS} days) — SKIPPING:`);
        uuidActive.forEach(({ accountId, lastSync }) => {
            console.log(`     ${accountId}  (last sync: ${formatDate(lastSync)})`);
        });
        console.log(`     → These could belong to active users. Increase CUTOFF_DAYS or delete manually.\n`);
    }

    // Print unknown-format accounts (just informational)
    if (unknownFormat.length > 0) {
        console.log(`  ℹ️  ${unknownFormat.length} account(s) with unrecognised key format (manual review recommended):`);
        unknownFormat.forEach(({ accountId, lastSync }) => {
            console.log(`     ${accountId}  (last sync: ${formatDate(lastSync)})`);
        });
        console.log();
    }

    // ── Step 3: Delete (or dry-run) stale UUID accounts ───────────────────────
    console.log('── Step 3: Stale UUID accounts ─────────────────────────────────');
    if (toDelete.length === 0) {
        console.log('  ✅ No stale UUID accounts found\n');
    } else {
        console.log(`  Found ${toDelete.length} stale UUID account(s) (last activity > ${CUTOFF_DAYS} days ago):\n`);
        toDelete.forEach(({ accountId, lastSync }) => {
            console.log(`     ${accountId}  (last sync: ${formatDate(lastSync)})`);
        });
        console.log();

        if (!IS_DRY_RUN) {
            for (const { accountId } of toDelete) {
                await db.ref('users').child(accountId).remove();
                console.log(`  🗑️  Deleted: ${accountId}`);
            }
            console.log(`\n  ✅ Removed ${toDelete.length} orphan UUID account(s)`);
        } else {
            console.log(`  [DRY RUN] Would delete ${toDelete.length} account(s) listed above`);
        }
    }

    // ── Summary ────────────────────────────────────────────────────────────────
    console.log();
    console.log('═══════════════════════════════════════════════════════════════');
    if (IS_DRY_RUN) {
        console.log(' DRY RUN complete — nothing was changed.');
        console.log(' Review the output above, then re-run with:');
        console.log('   PowerShell:  $env:DELETE="true"; node cleanup.js');
        console.log('   bash/macOS:  DELETE=true node cleanup.js');
    } else {
        console.log(' ✅ Live cleanup complete.');
        console.log(` Kept    : ${toKeep.length} device-ID account(s)`);
        console.log(` Deleted : ${toDelete.length} stale UUID account(s)`);
        console.log(` Skipped : ${uuidActive.length} recent UUID account(s) (manual review)`);
    }
    console.log('═══════════════════════════════════════════════════════════════');

    await shutdown(db);
}

async function shutdown(db) {
    await db.app.delete();
    process.exit(0);
}

main().catch(err => {
    console.error('\n❌ Fatal error:', err.message || err);
    process.exit(1);
});

