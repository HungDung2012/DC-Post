package com.dcpost.customer.common.exception;

import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;

public final class PersistenceConflicts {
    private PersistenceConflicts() {}

    public static RuntimeException accountConflict(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1062) {
                return new AccountConflictException();
            }
        }
        return exception;
    }
}
