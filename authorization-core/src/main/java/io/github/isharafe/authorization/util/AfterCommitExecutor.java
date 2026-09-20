package io.github.isharafe.authorization.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class AfterCommitExecutor {
  public void execute(Runnable callback) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      callback.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            callback.run();
          }
        });
  }
}
