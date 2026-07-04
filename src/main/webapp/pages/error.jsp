<%@ page contentType="text/html;charset=UTF-8" language="java" isErrorPage="true" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Error — TechMart</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>
<jsp:include page="/pages/header.jsp"/>

<main class="container">
    <div class="empty-state">
        <%
            Integer statusCode = (Integer) request.getAttribute("jakarta.servlet.error.status_code");
            String message;
            if (statusCode != null && statusCode == 404) {
                message = "The page you're looking for doesn't exist.";
            } else {
                message = "Something went wrong on our end. Please try again shortly.";
            }
        %>
        <h2>Oops!</h2>
        <p><%= message %></p>
        <a href="${pageContext.request.contextPath}/" class="btn btn-primary">Back to Home</a>
    </div>
</main>

<jsp:include page="/pages/footer.jsp"/>
</body>
</html>
