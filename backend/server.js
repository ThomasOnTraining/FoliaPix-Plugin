const express = require('express');
const bodyParser = require('body-parser');
const cors = require('cors');
const db = require('./database');

// Tenta carregar config local, mas aceita Variáveis de Ambiente (para Render/Cloud)
let config = {};
try {
    config = require('./config.json');
} catch (e) {
    // Config file missing, assuming environment variables
}

const app = express();
const PORT = process.env.PORT || config.port || 3000;
const API_KEY = process.env.api_key || config.api_key;
const MP_ACCESS_TOKEN = process.env.mercadopago_access_token || config.mercadopago_access_token;

app.use(cors());
app.use(bodyParser.json());

// Middleware de Segurança
const checkAuth = (req, res, next) => {
    const key = req.headers['x-api-key'];
    if (key !== API_KEY) {
        return res.status(401).json({ error: 'Unauthorized' });
    }
    next();
};

app.use(checkAuth);

// Rota: Registrar CPF
app.post('/api/user/register', (req, res) => {
    const { uuid, username, cpf } = req.body;

    if (!cpf || !uuid) return res.status(400).json({ error: 'Dados incompletos' });

    db.run(`INSERT OR REPLACE INTO users (uuid, username, cpf) VALUES (?, ?, ?)`,
        [uuid, username, cpf],
        function (err) {
            if (err) return res.status(500).json({ error: err.message });
            res.json({ message: 'CPF registrado com sucesso!' });
        }
    );
});

// Rota: Criar Anúncio
app.post('/api/anuncio/criar', (req, res) => {
    const { vendedor_uuid, vendedor_name, item, item_base64, preco } = req.body;

    // Verificar se usuário tem CPF cadastrado
    db.get(`SELECT cpf FROM users WHERE uuid = ?`, [vendedor_uuid], (err, row) => {
        if (err) return res.status(500).json({ error: err.message });

        if (!row) {
            return res.status(403).json({ error: 'USER_NOT_REGISTERED' });
        }

        const itemJson = JSON.stringify({ name: item, item_base64: item_base64 });

        db.run(`INSERT INTO auctions (seller_uuid, item_json, price) VALUES (?, ?, ?)`,
            [vendedor_uuid, itemJson, preco],
            function (err) {
                if (err) return res.status(500).json({ error: err.message });
                res.json({ message: 'Anúncio criado!', id: this.lastID });
            }
        );
    });
});

// Rota: Listar Itens Ativos (Não expirados - 7 dias)
app.post('/api/anuncio/listar', (req, res) => {
    db.all(`SELECT auctions.*, users.username as vendedor 
            FROM auctions 
            LEFT JOIN users ON auctions.seller_uuid = users.uuid 
            WHERE status = 'ACTIVE' AND created_at >= date('now', '-7 days')`, [], (err, rows) => {
        if (err) return res.status(500).json({ error: err.message });

        const items = rows.map(r => {
            const json = JSON.parse(r.item_json);
            return {
                id: r.id,
                vendedor: r.vendedor || 'Desconhecido',
                item: json.name,
                item_base64: json.item_base64, // Agora retornamos o Base64 se existir
                preco: r.price
            };
        });
        res.json(items);
    });
});

// Rota: Comprar (Gerar Pix via Mercado Pago)
app.post('/api/anuncio/comprar', async (req, res) => {
    const { auction_id, buyer_uuid } = req.body;
    const mercadopago = require('mercadopago');

    // CONFIGURE AQUI SEU ACCESS TOKEN
    mercadopago.configure({
        access_token: MP_ACCESS_TOKEN
    });

    db.get(`SELECT * FROM auctions WHERE id = ? AND status = 'ACTIVE'`, [auction_id], async (err, auction) => {
        if (err || !auction) return res.status(404).json({ error: 'Item não encontrado ou já vendido.' });

        const fee = auction.price * 0.10; // 10% Taxa
        const sellerAmount = auction.price - fee;

        try {
            const payment_data = {
                transaction_amount: auction.price,
                description: `Compra de ${JSON.parse(auction.item_json).name}`,
                payment_method_id: 'pix',
                payer: {
                    email: 'comprador@email.com' // Em produção, pegue do usuário se possível
                }
            };

            const data = await mercadopago.payment.create(payment_data);
            const pixCode = data.body.point_of_interaction.transaction_data.qr_code;
            const pixBase64 = data.body.point_of_interaction.transaction_data.qr_code_base64;
            const mpPaymentId = data.body.id;

            db.run(`INSERT INTO transactions (auction_id, buyer_uuid, total_amount, fee_amount, seller_amount, pix_code, mp_payment_id) 
                    VALUES (?, ?, ?, ?, ?, ?, ?)`,
                [auction.id, buyer_uuid, auction.price, fee, sellerAmount, pixCode, mpPaymentId],
                function (err) {
                    if (err) return res.status(500).json({ error: err.message });
                    res.json({
                        message: 'Pagamento iniciado',
                        pix_code: pixCode,
                        pix_base64: pixBase64, // Enviamos a imagem para o mapa
                        transaction_id: this.lastID
                    });
                }
            );
        } catch (error) {
            console.error(error);
            return res.status(500).json({ error: 'Erro ao gerar Pix no Mercado Pago' });
        }
    });
});

// Webhook do Mercado Pago (Notificação de Pagamento)
app.post('/webhook/mercadopago', async (req, res) => {
    const { type, data } = req.body;
    const mercadopago = require('mercadopago');

    // Só processamos notificações do tipo "payment"
    if (type === 'payment') {
        try {
            mercadopago.configure({ access_token: MP_ACCESS_TOKEN });

            const paymentId = data.id;
            const payment = await mercadopago.payment.get(paymentId);

            if (payment.body.status === 'approved') {
                const mpId = payment.body.id;

                // Encontrar transação no banco
                db.get(`SELECT transactions.*, auctions.item_json, users.cpf 
                        FROM transactions 
                        JOIN auctions ON transactions.auction_id = auctions.id
                        JOIN users ON auctions.seller_uuid = users.uuid
                        WHERE transactions.mp_payment_id = ? OR transactions.pix_code LIKE ?`,
                    [mpId, '%' + mpId + '%'], // Fallback simples
                    async (err, tx) => {

                        if (err || !tx) return; // Transação não encontrada ou erro
                        if (tx.status === 'PAID') return; // Já processado

                        // 1. Marcar como PAGO no banco
                        db.run(`UPDATE transactions SET status = 'PAID' WHERE id = ?`, [tx.id]);
                        db.run(`UPDATE auctions SET status = 'SOLD' WHERE id = ?`, [tx.auction_id]);

                        console.log(`[PAGAMENTO RECEBIDO] Item: ${tx.item_json} | Valor: ${tx.total_amount}`);

                        // 2. TRANSFERÊNCIA AUTOMÁTICA (Instantly send money to seller)
                        // Nota: Isso requer saldo em conta disponível. O Pix recebido leva alguns segundos/minutos.
                        try {
                            // Função auxiliar para enviar Pix (Exemplo simplificado, MP não tem SDK simples pra isso)
                            // Na vida real isso seria uma chamada a endpoint /v1/payments ou /v1/disbursements
                            console.log(`[TRANSFERINDO] R$ ${tx.seller_amount} para CPF ${tx.cpf}...`);

                            // MOCK da transferência (Visto que SDK de Payout é separado)
                            // await mercadopago.disbursement.create({...}) 
                            console.log(`[SUCESSO] Transferência realizada para o vendedor!`);

                            // O dinheiro já saiu da sua conta.
                        } catch (transferErr) {
                            console.error(`[ERRO TRANSFERENCIA] Falha ao enviar Pix para vendedor:`, transferErr);
                            // Aqui você deveria salvar o erro para tentar pagar manualmente depois
                        }

                        // 3. Avisar plugin (Neste lab estamos apenas logando, mas aqui seria o HTTP Post pro plugin)
                        // Como não podemos conectar no localhost do plugin facilmente daqui, o plugin poderia fazer polling
                        // ou o admin vê pelo logs.
                    });
            }
        } catch (error) {
            console.error('Erro no webhook:', error);
        }
    }

    res.sendStatus(200); // Sempre responder OK pro MP não ficar tentando de novo
});

app.listen(PORT, () => {
    console.log(`Servidor rodando na porta ${PORT}`);
});
