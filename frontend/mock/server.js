/**
 * Mock backend for local frontend development — no Spring Boot needed.
 * Uses plain Express (pure JS, no native deps). Runs on port 8080 to
 * match proxy.conf.json without any config changes.
 *
 * Start:  npm run mock
 * Both:   npm run start:mock
 *
 * Test accounts (any password, OTP always 123456):
 *   user@example.com   → role USER
 *   admin@example.com  → role ADMIN
 */

const express = require('express');
const app = express();

app.use(express.json());
app.use((req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Headers', 'Authorization, Content-Type');
  res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});

// ─── In-memory data store ─────────────────────────────────────────────────────

const db = {
  users: [
    { userId: 'user-001', username: 'johndoe', email: 'john@example.com', role: 'USER', isActive: true, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-04-30T00:00:00Z' },
    { userId: 'admin-001', username: 'adminuser', email: 'admin@example.com', role: 'ADMIN', isActive: true, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-04-30T00:00:00Z' },
    { userId: 'user-002', username: 'janedoe', email: 'jane@example.com', role: 'USER', isActive: true, createdAt: '2026-02-15T00:00:00Z', updatedAt: '2026-04-30T00:00:00Z' }
  ],
  transactions: [
    { transactionId: 'tx-001', userId: 'user-001', fromCurrency: 'USD', toCurrency: 'EUR', amount: 50.00, convertedAmount: 46.00, rate: 0.92, transactionDate: '2026-04-30T08:00:00Z', status: 'APPROVED' },
    { transactionId: 'tx-002', userId: 'user-001', fromCurrency: 'USD', toCurrency: 'GBP', amount: 200.00, convertedAmount: 157.00, rate: 0.785, transactionDate: '2026-04-29T14:30:00Z', status: 'PENDING_APPROVAL' },
    { transactionId: 'tx-003', userId: 'user-001', fromCurrency: 'EUR', toCurrency: 'INR', amount: 30.00, convertedAmount: 2742.00, rate: 91.4, transactionDate: '2026-04-28T10:15:00Z', status: 'APPROVED' }
  ],
  rates: [
    { id: 1, fromCurrency: 'USD', toCurrency: 'EUR', rate: 0.92, lastUpdated: '2026-04-30T09:00:00Z', source: 'API', isActive: true },
    { id: 2, fromCurrency: 'USD', toCurrency: 'GBP', rate: 0.785, lastUpdated: '2026-04-30T09:00:00Z', source: 'API', isActive: true },
    { id: 3, fromCurrency: 'USD', toCurrency: 'INR', rate: 83.50, lastUpdated: '2026-04-30T09:00:00Z', source: 'API', isActive: true },
    { id: 4, fromCurrency: 'USD', toCurrency: 'JPY', rate: 154.20, lastUpdated: '2026-04-30T09:00:00Z', source: 'API', isActive: true },
    { id: 5, fromCurrency: 'EUR', toCurrency: 'USD', rate: 1.087, lastUpdated: '2026-04-30T09:00:00Z', source: 'API', isActive: true },
    { id: 6, fromCurrency: 'EUR', toCurrency: 'INR', rate: 91.40, lastUpdated: '2026-04-30T09:00:00Z', source: 'API', isActive: true },
    { id: 7, fromCurrency: 'GBP', toCurrency: 'USD', rate: 1.274, lastUpdated: '2026-04-30T09:00:00Z', source: 'API', isActive: true }
  ],
  logs: [
    { logId: 1, event: 'User login successful', eventType: 'LOGIN', timestamp: '2026-04-30T08:00:00', userId: 'user-001', ipAddress: '127.0.0.1', details: { email: 'john@example.com' } },
    { logId: 2, event: 'Currency conversion processed', eventType: 'CONVERSION', timestamp: '2026-04-30T08:01:00', userId: 'user-001', ipAddress: '127.0.0.1', details: { from: 'USD', to: 'EUR', amount: 50 } }
  ]
};

// ─── Helpers ──────────────────────────────────────────────────────────────────

function makeToken(email, role) {
  const header = Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT' })).toString('base64');
  const payload = Buffer.from(JSON.stringify({
    sub: email, role, iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 900
  })).toString('base64');
  return `${header}.${payload}.mock_signature`;
}

function page(arr, req) {
  const size = parseInt(req.query.size) || 20;
  const num = parseInt(req.query.page) || 0;
  const content = arr.slice(num * size, num * size + size);
  return { content, totalElements: arr.length, totalPages: Math.ceil(arr.length / size), size, number: num };
}

// ─── Auth ─────────────────────────────────────────────────────────────────────

app.post('/api/auth/register', (req, res) => {
  const { username, email } = req.body;
  const user = { userId: `user-${Date.now()}`, username, email, role: 'USER', isActive: true, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() };
  db.users.push(user);
  res.status(201).json(user);
});

app.post('/api/auth/login', (req, res) => {
  const { email } = req.body;
  console.log(`\n  [MOCK OTP] ${email} → 123456\n`);
  res.status(200).json({ message: 'OTP sent to registered email', mfaRequired: true });
});

app.post('/api/auth/mfa/verify', (req, res) => {
  const { email, otp } = req.body;
  if (otp !== '123456') {
    return res.status(401).json({ status: 401, error: 'AUTHENTICATION_FAILED', message: 'Invalid OTP. Use 123456 in mock mode.' });
  }
  const role = (email || '').includes('admin') ? 'ADMIN' : 'USER';
  res.status(200).json({ accessToken: makeToken(email, role), tokenType: 'Bearer', expiresIn: 900, role });
});

app.post('/api/auth/logout', (req, res) => res.status(204).end());

app.post('/api/auth/refresh', (req, res) => {
  res.status(200).json({ accessToken: makeToken('user@example.com', 'USER'), tokenType: 'Bearer', expiresIn: 900 });
});

// ─── Exchange Rates ───────────────────────────────────────────────────────────

app.get('/api/rates', (req, res) => {
  res.json(db.rates.filter(r => r.isActive));
});

app.get('/api/rates/:from/:to', (req, res) => {
  const r = db.rates.find(r => r.fromCurrency === req.params.from && r.toCurrency === req.params.to);
  if (!r) return res.status(404).json({ message: 'Rate not found' });
  res.json(r);
});

app.post('/api/rates/admin', (req, res) => {
  const { fromCurrency, toCurrency, rate } = req.body;
  const newRate = { id: db.rates.length + 1, fromCurrency, toCurrency, rate, lastUpdated: new Date().toISOString(), source: 'MANUAL', isActive: true };
  db.rates.push(newRate);
  res.status(201).json(newRate);
});

app.put('/api/rates/admin/:id', (req, res) => {
  const r = db.rates.find(r => r.id === parseInt(req.params.id));
  if (!r) return res.status(404).json({ message: 'Not found' });
  Object.assign(r, { rate: req.body.rate, source: 'MANUAL', lastUpdated: new Date().toISOString() });
  res.json(r);
});

app.delete('/api/rates/admin/:id', (req, res) => {
  const r = db.rates.find(r => r.id === parseInt(req.params.id));
  if (r) r.isActive = false;
  res.status(204).end();
});

// ─── Transactions ─────────────────────────────────────────────────────────────

app.post('/api/transactions', (req, res) => {
  const { fromCurrency, toCurrency, amount } = req.body;
  const rateObj = db.rates.find(r => r.fromCurrency === fromCurrency && r.toCurrency === toCurrency);
  const rate = rateObj ? rateObj.rate : 1.0;
  const convertedAmount = parseFloat((amount * rate).toFixed(4));
  const status = parseFloat(amount) >= 100 ? 'PENDING_APPROVAL' : 'APPROVED';
  const tx = { transactionId: `tx-${Date.now()}`, userId: 'user-001', fromCurrency, toCurrency, amount: parseFloat(amount), convertedAmount, rate, transactionDate: new Date().toISOString(), status };
  db.transactions.unshift(tx);
  console.log(`  [MOCK TX] ${fromCurrency}→${toCurrency} ${amount} = ${convertedAmount} [${status}]`);
  res.status(201).json(tx);
});

app.get('/api/transactions', (req, res) => {
  res.json(page(db.transactions, req));
});

// ─── Admin ────────────────────────────────────────────────────────────────────

app.get('/api/admin/dashboard', (req, res) => {
  res.json({
    totalUsers: db.users.length,
    activeUsers: db.users.filter(u => u.isActive).length,
    totalTransactions: db.transactions.length,
    pendingApprovals: db.transactions.filter(t => t.status === 'PENDING_APPROVAL').length,
    topFromCurrencies: ['USD', 'EUR', 'GBP']
  });
});

app.get('/api/admin/transactions/pending', (req, res) => {
  res.json(db.transactions.filter(t => t.status === 'PENDING_APPROVAL'));
});

app.get('/api/admin/transactions', (req, res) => {
  res.json(page(db.transactions, req));
});

app.post('/api/admin/transactions/:id/approve', (req, res) => {
  const tx = db.transactions.find(t => t.transactionId === req.params.id);
  if (!tx) return res.status(404).json({ message: 'Not found' });
  Object.assign(tx, { status: 'APPROVED', approvedBy: 'admin@example.com', approvalDate: new Date().toISOString() });
  res.json(tx);
});

app.post('/api/admin/transactions/:id/reject', (req, res) => {
  const tx = db.transactions.find(t => t.transactionId === req.params.id);
  if (!tx) return res.status(404).json({ message: 'Not found' });
  Object.assign(tx, { status: 'REJECTED', approvedBy: 'admin@example.com', approvalDate: new Date().toISOString() });
  res.json(tx);
});

app.get('/api/admin/users', (req, res) => {
  res.json(page(db.users, req));
});

app.delete('/api/admin/users/:id', (req, res) => {
  const u = db.users.find(u => u.userId === req.params.id);
  if (u) u.isActive = false;
  res.status(204).end();
});

app.get('/api/admin/logs', (req, res) => {
  res.json(page(db.logs, req));
});

// ─── User self ────────────────────────────────────────────────────────────────

app.get('/api/users/me', (req, res) => {
  res.json(db.users[0]);
});

app.put('/api/users/me', (req, res) => {
  const { username, password } = req.body;
  if (username) db.users[0].username = username;
  db.users[0].updatedAt = new Date().toISOString();
  res.json(db.users[0]);
});

// ─── Start ────────────────────────────────────────────────────────────────────

const PORT = process.env.MOCK_PORT || 8080;
app.listen(PORT, () => {
  console.log(`\n Mock backend → http://localhost:${PORT}`);
  console.log('  Accounts (any password, OTP = 123456):');
  console.log('    USER  → user@example.com');
  console.log('    ADMIN → admin@example.com\n');
});
