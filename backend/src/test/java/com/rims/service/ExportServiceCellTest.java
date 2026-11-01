package com.rims.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Plain unit test (no Spring): CSV cells must never be read as spreadsheet formulas. */
class ExportServiceCellTest {

    @Test
    void textThatLooksLikeAFormulaIsDefused() {
        // defused with a leading apostrophe, then quoted because it contains quotes
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\"", ExportService.cell("=HYPERLINK(\"x\")"));
        assertEquals("'+1+1", ExportService.cell("+1+1"));
        assertEquals("'@SUM(A1)", ExportService.cell("@SUM(A1)"));
        assertEquals("'-cmd", ExportService.cell("-cmd"));
    }

    @Test
    void realNumbersKeepTheirSignAndMoneyIsNotInScientificNotation() {
        assertEquals("-5", ExportService.cell(-5));
        assertEquals("12.50", ExportService.cell(new BigDecimal("12.50")));
        assertEquals("0.00", ExportService.cell(new BigDecimal("0.00")));
    }

    @Test
    void commasQuotesAndNewlinesAreQuoted() {
        assertEquals("\"a,b\"", ExportService.cell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", ExportService.cell("say \"hi\""));
        assertEquals("\"l1\nl2\"", ExportService.cell("l1\nl2"));
        assertEquals("", ExportService.cell(null));
    }
}
