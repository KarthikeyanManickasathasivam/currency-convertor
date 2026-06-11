CREATE TABLE transactions (
    transaction_id  UUID            NOT NULL DEFAULT RANDOM_UUID(),
    user_id         UUID            NOT NULL,
    from_currency   VARCHAR(3)      NOT NULL,
    to_currency     VARCHAR(3)      NOT NULL,
    amount          DECIMAL(18, 2)  NOT NULL,
    converted_amount DECIMAL(18, 2) NOT NULL,
    rate            DECIMAL(18, 8)  NOT NULL,
    transaction_date TIMESTAMP      NOT NULL DEFAULT NOW(),
    status          VARCHAR(20)     NOT NULL DEFAULT 'APPROVED',
    approved_by     UUID,
    approval_date   TIMESTAMP,

    CONSTRAINT pk_transactions PRIMARY KEY (transaction_id),
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_transactions_approver FOREIGN KEY (approved_by) REFERENCES users (user_id),
    CONSTRAINT chk_transactions_status CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_transactions_user_id ON transactions (user_id);
CREATE INDEX idx_transactions_status ON transactions (status);
CREATE INDEX idx_transactions_date ON transactions (transaction_date DESC);
