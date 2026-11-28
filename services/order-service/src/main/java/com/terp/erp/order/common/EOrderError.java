package com.terp.erp.order.common;

import com.terp.erp.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** order-service errors. Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum EOrderError implements ErrorCode {
    ORD_001("You are not a member of this shop."),
    ORD_002("You can buy at most {0} of this item."),
    ORD_003("This item is not on sale at this store."),
    ORD_004("A cart holds at most {0} items."),
    ORD_005("The store does not exist in this shop."),
    ORD_006("This item is not in your cart."),
    ORD_007("The shop is temporarily unavailable. Please try again later."),
    ORD_008("Your cart is empty."),
    ORD_009("{0} is no longer available. Remove it from your cart."),
    ORD_010("The minimum order is {0}."),
    ORD_011("The store is not taking orders now."),
    ORD_012("The delivery address does not exist."),
    ORD_013("This store does not deliver to this address."),
    ORD_014("You already have an order waiting for payment at this store: {0}."),
    ORD_015("Some items ran out of stock while paying."),
    ORD_016("This order cannot be paid in its state ({0})."),
    ORD_017("A paid order cannot be cancelled."),
    ORD_018("The order does not exist."),
    ORD_019("The order cannot be cancelled and refunded in its state ({0})."),
    ORD_020("The rider has already picked the order up; it can no longer be cancelled.");

    private final String message;
}
