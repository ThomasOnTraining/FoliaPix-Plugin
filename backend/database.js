const sqlite3 = require('sqlite3').verbose();
const path = require('path');

// Cria ou abre o banco de dados
const db = new sqlite3.Database(path.join(__dirname, 'foliapix.db'), (err) => {
    if (err) {
        console.error('Erro ao conectar no banco de dados', err.message);
    } else {
        console.log('Conectado ao banco de dados SQLite.');
    }
});

// Inicializa tabelas
db.serialize(() => {
    // Tabela de Usuários (Vendedores)
    db.run(`CREATE TABLE IF NOT EXISTS users (
        uuid TEXT PRIMARY KEY,
        username TEXT,
        cpf TEXT UNIQUE
    )`);

    // Tabela de Anúncios
    db.run(`CREATE TABLE IF NOT EXISTS auctions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        seller_uuid TEXT,
        item_json TEXT,
        price REAL,
        status TEXT DEFAULT 'ACTIVE',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )`);

    // Tabela de Transações (Vendas)
    db.run(`CREATE TABLE IF NOT EXISTS transactions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        auction_id INTEGER,
        buyer_uuid TEXT,
        total_amount REAL,
        fee_amount REAL,
        seller_amount REAL,
        pix_code TEXT,
        status TEXT DEFAULT 'PENDING',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )`);
});

module.exports = db;
