package com.terp.erp.commerce.sales;

/** Product-wise invoices have a warehouse and quantities; service-wise ones neither (ERP manual 6.2). */
public enum EInvoiceType {
    PRODUCT, SERVICE
}
