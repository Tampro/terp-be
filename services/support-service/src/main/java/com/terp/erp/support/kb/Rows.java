package com.terp.erp.support.kb;

import com.terp.erp.exception.ApiException;
import com.terp.erp.support.common.ESupportError;
import com.terp.erp.support.kb.CsvImport.Row;

/** Cell checks of a CSV import row; each failure names the row and the column. */
final class Rows {
    private Rows() {
    }

    static String required(Row r, int i, String column, int max) {
        String s = optional(r, i, column, max);
        if (s == null) {
            throw ApiException.badRequest(ESupportError.SUP_022, r.number(), column);
        }
        return s;
    }

    static String optional(Row r, int i, String column, int max) {
        String s = r.cell(i);
        if (s != null && s.length() > max) {
            throw ApiException.badRequest(ESupportError.SUP_023, r.number(), column, max);
        }
        return s;
    }
}
