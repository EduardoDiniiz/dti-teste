package co.inter.piggies.shared.domain.port.out;

import java.util.function.Supplier;

/** Executa um bloco numa transação nova: commit ao final, rollback em exceção. */
public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> work);
}
