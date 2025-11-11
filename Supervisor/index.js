import dotenv from "dotenv";
dotenv.config({ path: process.env.NODE_ENV === "production" ? "/etc/msu/.env" : "./.env" });

import express from "express";
import crypto from "crypto";
import axios from "axios";

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
  if (ip === "127.0.0.1" || ip === "::1") return next();
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
      console.log(`⏳ Authorization for ${username} expired (no login after 5 min)`);
      authorized.delete(username);
      // 👇 Insert whitelist/firewall REMOVE logic here
    }
  }, AUTH_WINDOW_MS);

  authorized.set(username, { ip, logged: false, timer, graceTimer: null });
  console.log(`✅ Authorized ${username} (${ip}) for 5 minutes`);

  // 👇 Insert whitelist/firewall ADD logic here
  return res.json({ ok: true, message: `Player ${username} authorized for 5 minutes` });
});

// --- /logged (called by the MineServer when player logs in) ---
app.post("/logged", requireLocalhost, (req, res) => {
  const { username } = req.body || {};
  if (!username)
    return res.status(400).json({ ok: false, error: "missing username" });

  const entry = authorized.get(username);
  if (!entry)
    return res.status(404).json({ ok: false, error: "not authorized" });

  if (entry.logged)
    return res.json({ ok: true, message: "already logged" });

  entry.logged = true;
  clearTimeout(entry.timer);
  clearTimeout(entry.graceTimer);
  authorized.set(username, entry);

  console.log(`🎮 Player ${username} logged in — authorization timer cleared`);
  return res.json({ ok: true, message: `Player ${username} login confirmed` });
});

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
      // 👇 Insert whitelist/firewall REMOVE logic here
    }
  }, AUTH_WINDOW_MS);

  authorized.set(username, entry);
  console.log(`🚪 Player ${username} disconnected — 5 min grace timer started`);
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
      console.log("🕒 Server empty for >" + (STATUS_INTERVAL_MS*3/(60*1000)).toString() + " min — initiating shutdown sequence...");
      // 👇 Insert graceful shutdown / EC2 stop logic here
      emptyCount = 0;
    }
  } catch (err) {
    console.error("❌ Error checking MineServer status:", err.message);
    emptyCount++;
  }
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