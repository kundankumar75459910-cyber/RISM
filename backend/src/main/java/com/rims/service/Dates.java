package com.rims.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Inclusive date filters ("from" and "to" are whole calendar days). */
final class Dates {
    private Dates() {}

    static LocalDateTime start(LocalDate from) {
        return from == null ? null : from.atStartOfDay();
    }

    /** exclusive upper bound = start of the day after "to" */
    static LocalDateTime endExclusive(LocalDate to) {
        return to == null ? null : to.plusDays(1).atStartOfDay();
    }
}
