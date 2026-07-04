<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Order Confirmed — TechMart</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>
<jsp:include page="/pages/header.jsp"/>

<main class="container">
    <div class="confirmation-box">
        <div class="confirmation-icon">&#10003;</div>
        <h2>Order Placed Successfully!</h2>
        <p>Thank you for shopping with TechMart. A confirmation email has been sent to <strong>${order.customerEmail}</strong>.</p>

        <div class="order-details">
            <h3>Order Summary</h3>
            <table class="order-table">
                <tr><th>Order ID</th><td>#${order.id}</td></tr>
                <tr><th>Status</th><td class="status-badge">${order.status}</td></tr>
                <tr><th>Total Amount</th><td>$<fmt:formatNumber value="${order.totalAmount}" pattern="#,##0.00"/></td></tr>
                <tr><th>Shipping To</th><td>${order.shippingAddress}</td></tr>
                <tr><th>Placed At</th><td>${order.placedAt}</td></tr>
            </table>

            <h4>Items Ordered</h4>
            <table class="order-table">
                <thead>
                    <tr><th>Product</th><th>Qty</th><th>Unit Price</th><th>Subtotal</th></tr>
                </thead>
                <tbody>
                    <c:forEach var="item" items="${order.items}">
                        <tr>
                            <td>${item.productName} <small>(${item.productSku})</small></td>
                            <td>${item.quantity}</td>
                            <td>$<fmt:formatNumber value="${item.unitPrice}" pattern="#,##0.00"/></td>
                            <td>$<fmt:formatNumber value="${item.subtotal}" pattern="#,##0.00"/></td>
                        </tr>
                    </c:forEach>
                </tbody>
            </table>
        </div>

        <div class="confirmation-actions">
            <a href="${pageContext.request.contextPath}/orders" class="btn btn-secondary">View My Orders</a>
            <a href="${pageContext.request.contextPath}/products" class="btn btn-primary">Continue Shopping</a>
        </div>
    </div>
</main>

<jsp:include page="/pages/footer.jsp"/>
</body>
</html>
