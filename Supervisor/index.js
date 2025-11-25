import dotenv from "dotenv";
dotenv.config({ path: process.env.NODE_ENV === "production" ? "/etc/msu/.env" : "./.env" });

import express from "express";
import crypto from "crypto";
import axios from "axios";

import { turnOff } from "./Util/shutdown.js";
import { invokeFirewallLambda } from "./Util/ip-whitelister.js";

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 5001;
const AUTH_SECRET = process.env.AUTH_SECRET || "dev-super-secret";
const AUTH_WINDOW_MS = process.env.AUTH_WINDOW_MS || 5 * 60 * 1000; // 5 minutes
const STATUS_INTERVAL_MS = process.env.STATUS_INTERVAL_MS || 3 * 60 * 1000; // 3 minutes
const MINE_PLUGIN_URL = process.env.MINE_PLUGIN_URL || "http://127.0.0.1:27111"; // plugin endpoint

// --- state tracking ---
const authorized = new Map(); // username -> { ip, logged, timer, graceTimer }

// --- Middleware: shared signature auth ---
function verifyAuth(req, res, next) {
  const signature = req.headers["x-signature"];
  const timestamp = req.headers["x-timestamp"];
  if (!signature || !timestamp)
    return res.status(400).json({ ok: false, error: "missing signature" });

  const body = JSON.stringify(req.body);
  const expected = crypto
    .createHmac("sha256", AUTH_SECRET)
    .update(timestamp + ":" + body)
    .digest("hex");

  if (signature !== expected)
    return res.status(403).json({ ok: false, error: "invalid signature" });

  if (Math.abs(Date.now() - Number(timestamp)) > 5 * 60 * 1000)
    return res.status(403).json({ ok: false, error: "stale signature" });

  next();
}

// --- Middleware: localhost restriction ---
function requireLocalhost(req, res, next) {
  const ip = req.ip || req.connection.remoteAddress;
  console.log("Incoming IP:", ip);

  // normalize IPv6-mapped IPv4 addresses
  const normalized = ip.replace(/^::ffff:/, "");

  if (normalized === "127.0.0.1" || normalized === "::1") {
    return next();
  }

  res.status(403).json({ ok: false, error: "forbidden" });
}

// --- /status (checks MineServer plugin) ---
app.get("/status", async (req, res) => {
  try {
    const response = await axios.get(MINE_PLUGIN_URL + "/online", { timeout: 1500 });
    const players = response.data.players.map((p) => p.name);
    const minePort = response.data.port || "24111";

    return res.json({
      ok: true,
      port: minePort,
      players,
    });
  } catch (err) {
    console.error("Status check failed:", err.message);
    return res.json({ ok: false, message: "server offline" });
  }
});

// --- /authorize (called by Auth Server) ---
app.post("/authorize", verifyAuth, async (req, res) => {
  const { ip, username } = req.body || {};
  if (!ip || !username)
    return res.status(400).json({ ok: false, error: "missing fields" });

  // clean any previous entry
  if (authorized.has(username)) {
    const old = authorized.get(username);
    clearTimeout(old.timer);
    clearTimeout(old.graceTimer);
  }

  // create authorization window
  const timer = setTimeout(() => {
    const entry = authorized.get(username);
    if (entry && !entry.logged) {
      console.log(`⏳ Authorization for ${username} expired (no login after ${AUTH_WINDOW_MS/60000} min)`);
      authorized.delete(username);
      invokeFirewallLambda("revoke", ip, username);
    }
  }, AUTH_WINDOW_MS);

  authorized.set(username, { ip, logged: false, timer, graceTimer: null });
  console.log(`✅ Authorized ${username} (${ip}) for ${AUTH_WINDOW_MS/60000} minutes`);

  const result = await invokeFirewallLambda("authorize", ip, username);
  if(result.ok)
    return res.json({ ok: true, message: `Player ${username} authorized for ${AUTH_WINDOW_MS/60000} minutes` });
  else
    return res.json({ ok: false, message: result.error });
});

// --- /logged (called by the MineServer when player logs in) ---
app.post("/logged", requireLocalhost, (req, res) => {
  const { username } = req.body || {};
  if (!username)
    return res.status(400).json({ ok: false, error: "missing username" });

  const parsed = getRoot(username);
  if (!parsed)
    return res.status(400).json({ ok: false, error: "invalid alias format" });

  const { root, aliasNum } = parsed;

  const entry = authorized.get(root);
  if (!entry)
    return res.status(404).json({ ok: false, error: "not authorized" });

  // Initialize alias tracking
  if (!entry.aliases) entry.aliases = { 0: false, 1: false, 2: false };

  // Already logged?
  if (entry.aliases[aliasNum]) {
    return res.json({ ok: true, message: "alias already logged" });
  }

  // Enforce max 3 total (root, -1, -2)
  const activeCount = Object.values(entry.aliases).filter(Boolean).length;
  if (activeCount >= 3)
    return res.status(403).json({ ok: false, error: "alias limit reached" });

  // Mark alias active
  entry.aliases[aliasNum] = true;

  // Clear root-wide timers
  clearTimeout(entry.timer);
  clearTimeout(entry.graceTimer);

  authorized.set(root, entry);

  console.log(`🎮 Player alias ${username} logged in as ${root}`);
  return res.json({
    ok: true,
    message: `Alias ${username} confirmed for root ${root}`
  });
});

function getRoot(username) {
  const match = username.match(/^([A-Za-z0-9_]+)(?:-(\d))?$/);
  if (!match) return null;

  const root = match[1];
  const aliasNum = match[2] ? parseInt(match[2], 10) : 0;

  if (aliasNum > 2) return null; // only allow -1 and -2
  return { root, aliasNum };
}

// --- /deauthorize (called locally when player disconnects) ---
app.post("/deauthorize", requireLocalhost, async (req, res) => {
  const { username } = req.body || {};
  if (!username) return res.status(400).json({ ok: false, error: "missing username" });

  const entry = authorized.get(username);
  if (!entry)
    return res.json({ ok: true, message: `Player ${username} was not authorized` });

  entry.logged = false;

  if (entry.graceTimer) clearTimeout(entry.graceTimer);
  entry.graceTimer = setTimeout(() => {
    const e = authorized.get(username);
    if (e && !e.logged) {
      console.log(`⌛ Player ${username} grace period expired (not relogged)`);
      authorized.delete(username);
      invokeFirewallLambda("revoke", e.ip, username);
    }
  }, AUTH_WINDOW_MS);

  authorized.set(username, entry);
  console.log(`🚪 Player ${username} disconnected — ${AUTH_WINDOW_MS/60000} min grace timer started`);
  return res.json({ ok: true, message: `Player ${username} grace timer started` });
});

// --- Periodic server check ---
let emptyCount = 0;
async function checkServerStatus() {
  try {
    const res = await axios.get("http://127.0.0.1:"+PORT+"/status", { timeout: 3000 });
    if (!res.data.ok) {
      console.warn("⚠️ MineServer appears offline");
      emptyCount++;
    } else {
      const playerCount = res.data.players.length;
      console.log(`📊 MineServer online — ${playerCount} player(s) connected`);

      if (playerCount === 0) emptyCount++;
      else emptyCount = 0;
    }

    // e.g., 3 consecutive empty checks (≈9 min)
    if (emptyCount >= 3) {
      emptyCount = 0;

      if (hasActivePlayersOrTimers()) {
        console.log("⛔ Shutdown skipped — players still authorized or grace timers active");
        return;
      }

      console.log("🕒 No players & no timers — initiating shutdown");
      turnOff();
    }
  } catch (err) {
    console.error("❌ Error checking MineServer status:", err.message);
    emptyCount++;
  }
}

function hasActivePlayersOrTimers() {
  for (const [user, entry] of authorized.entries()) {

    // Any alias logged in?
    if (entry.aliases && Object.values(entry.aliases).some(v => v)) {
      return true;
    }

    // Authorization window still running?
    if (entry.timer) return true;

    // Grace window still running?
    if (entry.graceTimer) return true;
  }

  return false;
}


setInterval(checkServerStatus, STATUS_INTERVAL_MS);

// --- Debug helper ---
app.get("/authorized", (req, res) => {
  const list = Array.from(authorized.entries()).map(([user, { ip, logged }]) => ({
    user,
    ip,
    logged,
  }));
  res.json({ ok: true, list });
});

// --- Start server ---
app.listen(PORT, () => console.log(`🧩 MineSupervisor listening on port ${PORT}`));