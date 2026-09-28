package co.inter.piggies.shared.adapter.out.persistence;

import co.inter.piggies.shared.domain.port.out.TransactionRunner;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Singleton;
import org.hibernate.Session;

import java.util.function.Supplier;

@Singleton
class HibernateTransactionRunner implements TransactionRunner {

    private final TransactionOperations<Session> transactions;

    HibernateTransactionRunner(TransactionOperations<Session> transactions) {
        this.transactions = transactions;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactions.executeWrite(status -> work.get());
    }
}
