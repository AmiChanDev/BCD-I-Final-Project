<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Shopping Cart — TechMart</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>
<jsp:include page="/pages/header.jsp"/>

<main class="container">
    <h2>Shopping Cart</h2>

    <c:if test="${not empty error}">
        <div class="alert alert-error">${error}</div>
    </c:if>

    <c:choose>
        <c:when test="${not empty cartItems}">
            <div class="cart-layout">
                <!-- Cart Items -->
                <div class="cart-items">
                    <table class="cart-table">
                        <thead>
                            <tr>
                                <th>Product</th>
                                <th>Unit Price</th>
                                <th>Quantity</th>
                                <th>Subtotal</th>
                                <th>Action</th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:forEach var="item" items="${cartItems}">
                                <tr>
                                    <td>
                                        <strong>${item.name}</strong><br/>
                                        <small class="sku">${item.sku}</small>
                                    </td>
                                    <td>$<fmt:formatNumber value="${item.unitPrice}" pattern="#,##0.00"/></td>
                                    <td>
                                        <form action="${pageContext.request.contextPath}/cart" method="post" class="inline-form">
                                            <input type="hidden" name="action" value="update"/>
                                            <input type="hidden" name="productId" value="${item.productId}"/>
                                            <input type="number" name="quantity" value="${item.quantity}" min="0" max="99" class="qty-input-sm"/>
                                            <button type="submit" class="btn-link">Update</button>
                                        </form>
                                    </td>
                                    <td>$<fmt:formatNumber value="${item.subtotal}" pattern="#,##0.00"/></td>
                                    <td>
                                        <form action="${pageContext.request.contextPath}/cart" method="post" class="inline-form">
                                            <input type="hidden" name="action" value="remove"/>
                                            <input type="hidden" name="productId" value="${item.productId}"/>
                                            <button type="submit" class="btn-danger-sm">Remove</button>
                                        </form>
                                    </td>
                                </tr>
                            </c:forEach>
                        </tbody>
                        <tfoot>
                            <tr>
                                <td colspan="3" class="total-label">Order Total</td>
                                <td class="total-value">$<fmt:formatNumber value="${cartTotal}" pattern="#,##0.00"/></td>
                                <td></td>
                            </tr>
                        </tfoot>
                    </table>
                </div>

                <!-- Checkout Panel -->
                <div class="checkout-panel">
                    <h3>Checkout</h3>
                    <form action="${pageContext.request.contextPath}/cart" method="post">
                        <input type="hidden" name="action" value="checkout"/>

                        <div class="form-group">
                            <label for="email">Email Address</label>
                            <input type="email" id="email" name="email" required
                                   placeholder="you@example.com"/>
                        </div>

                        <div class="form-group">
                            <label for="address">Shipping Address</label>
                            <textarea id="address" name="shippingAddress" rows="3"
                                      required placeholder="Enter your full delivery address"></textarea>
                        </div>

                        <div class="order-summary">
                            <p><strong>${itemCount} item(s)</strong></p>
                            <p class="summary-total">Total: $<fmt:formatNumber value="${cartTotal}" pattern="#,##0.00"/></p>
                        </div>

                        <button type="submit" class="btn btn-primary btn-full">Place Order</button>
                    </form>
                </div>
            </div>
        </c:when>
        <c:otherwise>
            <div class="empty-state">
                <p>Your cart is empty.</p>
                <a href="${pageContext.request.contextPath}/products" class="btn btn-primary">Browse Products</a>
            </div>
        </c:otherwise>
    </c:choose>

</main>

<jsp:include page="/pages/footer.jsp"/>
</body>
</html>
