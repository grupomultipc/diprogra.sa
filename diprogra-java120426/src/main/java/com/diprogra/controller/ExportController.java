package com.diprogra.controller;

import com.diprogra.service.AuthService;
import com.lowagie.text.Document;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;

@RestController
public class ExportController {

    @Autowired private JdbcTemplate db;
    @Autowired private AuthService  auth;

    private Map<String, String> getConfig() {
        List<Map<String, Object>> rows = db.queryForList("SELECT clave, valor FROM config");
        Map<String, String> cfg = new HashMap<>();
        for (Map<String, Object> r : rows) cfg.put(r.get("clave").toString(), String.valueOf(r.get("valor")));
        return cfg;
    }

    // ── EXCEL ────────────────────────────────────────────────────
    @GetMapping("/api/exportar/excel/{tabla}")
    public void exportarExcel(@PathVariable String tabla,
                               HttpSession session, HttpServletResponse response) throws Exception {
        if (!auth.isLoggedIn(session)) { response.sendError(401); return; }

        Map<String, String> cfg = getConfig();
        String empresa = cfg.getOrDefault("empresa_nombre", "DIPROGRA S.A.");
        Color verde    = new Color(0x1B, 0x43, 0x32);

        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet ws;
        String[] headers;
        java.util.List<Object[]> data = new ArrayList<>();

        switch (tabla) {
            case "articulos" -> {
                ws = wb.createSheet("Inventario");
                headers = new String[]{"Código","Artículo","Descripción","Precio","Entradas","Salidas","Stock","Actualizado"};
                db.queryForList("SELECT codigo,articulo,descripcion,precio,entradas,salidas,stocks,updated_at FROM articulos ORDER BY codigo")
                  .forEach(r -> data.add(new Object[]{r.get("codigo"),r.get("articulo"),r.get("descripcion"),r.get("precio"),r.get("entradas"),r.get("salidas"),r.get("stocks"),r.get("updated_at")}));
            }
            case "entradas" -> {
                ws = wb.createSheet("Entradas");
                headers = new String[]{"ID","Código","Artículo","Fecha","Cantidad","Precio","Notas","Usuario"};
                db.queryForList("SELECT id,codigo,articulo,fecha,cantidad,precio,notas,usuario FROM movimientos WHERE tipo='ENTRADA' ORDER BY fecha DESC,id DESC")
                  .forEach(r -> data.add(new Object[]{r.get("id"),r.get("codigo"),r.get("articulo"),r.get("fecha"),r.get("cantidad"),r.get("precio"),r.get("notas"),r.get("usuario")}));
            }
            case "salidas" -> {
                ws = wb.createSheet("Salidas");
                headers = new String[]{"ID","Código","Artículo","Fecha","Cantidad","Precio","Notas","Usuario"};
                db.queryForList("SELECT id,codigo,articulo,fecha,cantidad,precio,notas,usuario FROM movimientos WHERE tipo='SALIDA' ORDER BY fecha DESC,id DESC")
                  .forEach(r -> data.add(new Object[]{r.get("id"),r.get("codigo"),r.get("articulo"),r.get("fecha"),r.get("cantidad"),r.get("precio"),r.get("notas"),r.get("usuario")}));
            }
            case "movimientos" -> {
                ws = wb.createSheet("Movimientos");
                headers = new String[]{"ID","Tipo","Código","Artículo","Fecha","Cantidad","Precio","Notas","Usuario"};
                db.queryForList("SELECT id,tipo,codigo,articulo,fecha,cantidad,precio,notas,usuario FROM movimientos ORDER BY fecha DESC,id DESC")
                  .forEach(r -> data.add(new Object[]{r.get("id"),r.get("tipo"),r.get("codigo"),r.get("articulo"),r.get("fecha"),r.get("cantidad"),r.get("precio"),r.get("notas"),r.get("usuario")}));
            }
            default -> { response.sendError(400, "Tabla inválida"); wb.close(); return; }
        }

        // Estilos
        XSSFCellStyle titleStyle = wb.createCellStyle();
        XSSFFont titleFont = wb.createFont();
        titleFont.setBold(true); titleFont.setFontHeightInPoints((short)12);
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);

        XSSFCellStyle headerStyle = wb.createCellStyle();
        XSSFFont headerFont = wb.createFont();
        headerFont.setColor(IndexedColors.WHITE.getIndex()); headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(new XSSFColor(verde, null));
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        // Fila título
        Row titleRow = ws.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(empresa + " — " + ws.getSheetName() + " — " + LocalDate.now());
        titleCell.setCellStyle(titleStyle);
        ws.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));

        // Cabeceras
        Row headerRow = ws.createRow(1);
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        // Datos
        int rowNum = 2;
        for (Object[] rowData : data) {
            Row row = ws.createRow(rowNum++);
            for (int i = 0; i < rowData.length; i++) {
                Cell c = row.createCell(i);
                if (rowData[i] instanceof Number n) c.setCellValue(n.doubleValue());
                else c.setCellValue(rowData[i] != null ? rowData[i].toString() : "");
            }
        }

        // Auto-ancho
        for (int i = 0; i < headers.length; i++) ws.autoSizeColumn(i);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        wb.write(bos); wb.close();

        String fname = "DIPROGRA_" + tabla + "_" + LocalDate.now() + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fname + "\"");
        response.getOutputStream().write(bos.toByteArray());
    }

    // ── PDF ──────────────────────────────────────────────────────
    @GetMapping("/api/exportar/pdf/{tabla}")
    public void exportarPdf(@PathVariable String tabla,
                             HttpSession session, HttpServletResponse response) throws Exception {
        if (!auth.isLoggedIn(session)) { response.sendError(401); return; }

        Map<String, String> cfg = getConfig();
        String empresa = cfg.getOrDefault("empresa_nombre", "DIPROGRA");
        String slogan  = cfg.getOrDefault("empresa_slogan", "");

        String[] headers;
        java.util.List<Object[]> data = new ArrayList<>();

        switch (tabla) {
            case "articulos" -> {
                headers = new String[]{"Código","Artículo","Precio","Entradas","Salidas","Stock"};
                db.queryForList("SELECT codigo,articulo,precio,entradas,salidas,stocks FROM articulos ORDER BY codigo")
                  .forEach(r -> data.add(new Object[]{r.get("codigo"),r.get("articulo"),r.get("precio"),r.get("entradas"),r.get("salidas"),r.get("stocks")}));
            }
            case "entradas" -> {
                headers = new String[]{"Código","Artículo","Fecha","Cantidad","Notas"};
                db.queryForList("SELECT codigo,articulo,fecha,cantidad,notas FROM movimientos WHERE tipo='ENTRADA' ORDER BY fecha DESC,id DESC LIMIT 500")
                  .forEach(r -> data.add(new Object[]{r.get("codigo"),r.get("articulo"),r.get("fecha"),r.get("cantidad"),r.get("notas")}));
            }
            case "salidas" -> {
                headers = new String[]{"Código","Artículo","Fecha","Cantidad","Notas"};
                db.queryForList("SELECT codigo,articulo,fecha,cantidad,notas FROM movimientos WHERE tipo='SALIDA' ORDER BY fecha DESC,id DESC LIMIT 500")
                  .forEach(r -> data.add(new Object[]{r.get("codigo"),r.get("articulo"),r.get("fecha"),r.get("cantidad"),r.get("notas")}));
            }
            case "movimientos" -> {
                headers = new String[]{"Tipo","Código","Artículo","Fecha","Cantidad","Usuario"};
                db.queryForList("SELECT tipo,codigo,articulo,fecha,cantidad,usuario FROM movimientos ORDER BY fecha DESC,id DESC LIMIT 500")
                  .forEach(r -> data.add(new Object[]{r.get("tipo"),r.get("codigo"),r.get("articulo"),r.get("fecha"),r.get("cantidad"),r.get("usuario")}));
            }
            default -> { response.sendError(400, "Tabla inválida"); return; }
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(doc, bos);
        doc.open();

        Color verde  = new Color(0x1B, 0x43, 0x32);
        com.lowagie.text.Font titleF  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new java.awt.Color(0x1B,0x43,0x32));
        com.lowagie.text.Font sloganF = FontFactory.getFont(FontFactory.HELVETICA, 9, java.awt.Color.GRAY);
        com.lowagie.text.Font infoF   = FontFactory.getFont(FontFactory.HELVETICA, 9);

        doc.add(new Paragraph(empresa, titleF));
        doc.add(new Paragraph(slogan, sloganF));
        doc.add(new Paragraph("Reporte: " + tabla.toUpperCase() + "   Fecha: " + LocalDate.now(), infoF));
        doc.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);

        // Cabeceras
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, java.awt.Color.WHITE)));
            cell.setBackgroundColor(verde);
            cell.setPadding(4);
            table.addCell(cell);
        }

        // Datos
        boolean alt = false;
        Color lVerde = new Color(0xD8, 0xF3, 0xDC);
        for (Object[] row : data) {
            for (Object val : row) {
                PdfPCell cell = new PdfPCell(new Phrase(val != null ? val.toString() : "", FontFactory.getFont(FontFactory.HELVETICA, 7.5f)));
                cell.setBackgroundColor(alt ? lVerde : java.awt.Color.WHITE);
                cell.setPadding(4);
                table.addCell(cell);
            }
            alt = !alt;
        }

        doc.add(table);
        doc.close();

        String fname = "DIPROGRA_" + tabla + "_" + LocalDate.now() + ".pdf";
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fname + "\"");
        response.getOutputStream().write(bos.toByteArray());
    }
}
