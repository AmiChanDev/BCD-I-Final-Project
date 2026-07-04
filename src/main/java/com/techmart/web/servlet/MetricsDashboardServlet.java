package com.techmart.web.servlet;

import com.techmart.util.PerformanceMonitor;
import com.techmart.model.PerformanceMetric;

import jakarta.ejb.EJB;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

@WebServlet(name = "MetricsDashboardServlet", urlPatterns = "/metrics")
public class MetricsDashboardServlet extends HttpServlet {

    @EJB
    private PerformanceMonitor perfMonitor;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        if (req.getParameter("json") != null) {
            serveJson(req, resp);
        } else {
            serveHtml(req, resp);
        }
    }

    private void serveJson(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        List<Map<String, Object>> summary = perfMonitor.getSummary();

        out.println("{");
        out.println("  \"startTime\": \"" + perfMonitor.getStartTime() + "\",");
        out.println("  \"metrics\": [");

        for (int i = 0; i < summary.size(); i++) {
            Map<String, Object> row = summary.get(i);
            out.print("    {\"key\":\"" + row.get("key") + "\",");
            out.print("\"invocations\":" + row.get("invocations") + ",");
            out.print("\"avgLatencyMs\":" + row.get("avgLatencyMs") + "}");
            if (i < summary.size() - 1) out.println(",");
            else out.println();
        }

        out.println("  ]");
        out.println("}");
    }

    private void serveHtml(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        List<Map<String, Object>> summary = perfMonitor.getSummary();
        List<PerformanceMetric> recent    = perfMonitor.getRecentMetrics();

        req.setAttribute("summary", summary);
        req.setAttribute("recent", recent);
        req.setAttribute("startTime", perfMonitor.getStartTime());
        req.getRequestDispatcher("/pages/metrics.jsp").forward(req, resp);
    }
}
