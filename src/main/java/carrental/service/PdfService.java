package carrental.service;

import carrental.model.Rental;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class PdfService {

    private static final BaseColor DARK_BG    = new BaseColor(19, 25, 41);
    private static final BaseColor CARD_BG    = new BaseColor(26, 34, 53);
    private static final BaseColor BLUE       = new BaseColor(59, 130, 246);
    private static final BaseColor GREEN      = new BaseColor(34, 197, 94);
    private static final BaseColor TEXT       = new BaseColor(226, 232, 248);
    private static final BaseColor MUTED      = new BaseColor(122, 143, 176);
    private static final BaseColor BORDER     = new BaseColor(30, 45, 69);
    private static final BaseColor GOLD       = new BaseColor(245, 158, 11);

    public byte[] generateRentalReceipt(Rental rental) throws DocumentException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        doc.open();

        // ── Background ────────────────────────────────────────────────────────
        PdfContentByte canvas = writer.getDirectContentUnder();
        canvas.setColorFill(DARK_BG);
        canvas.rectangle(0, 0, PageSize.A4.getWidth(), PageSize.A4.getHeight());
        canvas.fill();

        // ── Header bar ────────────────────────────────────────────────────────
        canvas.setColorFill(BLUE);
        canvas.rectangle(0, PageSize.A4.getHeight() - 90, PageSize.A4.getWidth(), 90);
        canvas.fill();

        Font logoFont   = new Font(Font.FontFamily.HELVETICA, 28, Font.BOLD, BaseColor.WHITE);
        Font subFont    = new Font(Font.FontFamily.HELVETICA, 12, Font.NORMAL, new BaseColor(200, 220, 255));
        Font titleFont  = new Font(Font.FontFamily.HELVETICA, 20, Font.BOLD, TEXT);
        Font labelFont  = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, MUTED);
        Font valueFont  = new Font(Font.FontFamily.HELVETICA, 12, Font.NORMAL, TEXT);
        Font totalFont  = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD, GREEN);
        Font footerFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, MUTED);

        // Logo in header
        Paragraph logo = new Paragraph("🚗  Rentify", logoFont);
        logo.setAlignment(Element.ALIGN_CENTER);
        logo.setSpacingBefore(16);
        doc.add(logo);

        Paragraph tagline = new Paragraph("Car Rental Receipt", subFont);
        tagline.setAlignment(Element.ALIGN_CENTER);
        doc.add(tagline);

        doc.add(Chunk.NEWLINE);

        // ── Receipt Title ──────────────────────────────────────────────────────
        Paragraph title = new Paragraph("Booking Confirmation", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(20);
        title.setSpacingAfter(6);
        doc.add(title);

        // Booking ID line
        Font idFont = new Font(Font.FontFamily.COURIER, 11, Font.NORMAL, MUTED);
        Paragraph bookingId = new Paragraph("Receipt #" + String.format("%06d", rental.getId()), idFont);
        bookingId.setAlignment(Element.ALIGN_CENTER);
        bookingId.setSpacingAfter(20);
        doc.add(bookingId);

        // ── Receipt Table ──────────────────────────────────────────────────────
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(90);
        table.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.setSpacingBefore(10);
        table.setSpacingAfter(20);
        table.setWidths(new float[]{40f, 60f});

        addRow(table, "Customer ID",   rental.getCustomer().getCustomerId(), labelFont, valueFont);
        addRow(table, "Customer Name", rental.getCustomer().getName(),       labelFont, valueFont);
        addRow(table, "Phone",         rental.getCustomer().getPhone(),      labelFont, valueFont);
        addDivider(table);
        addRow(table, "Car ID",        rental.getCar().getCarId(),           labelFont, valueFont);
        addRow(table, "Car",           rental.getCar().getBrand() + " " + rental.getCar().getModel(), labelFont, valueFont);
        addRow(table, "Category",      rental.getCar().getCategory(),        labelFont, valueFont);
        addRow(table, "Rate",          "$" + rental.getCar().getBasePricePerDay() + " / day", labelFont, valueFont);
        addDivider(table);
        addRow(table, "Start Date",    rental.getStartDateStr(),             labelFont, valueFont);
        addRow(table, "End Date",      rental.getEndDateStr(),               labelFont, valueFont);
        addRow(table, "Rental Days",   String.valueOf(rental.getDays()),     labelFont, valueFont);
        addRow(table, "Booked At",     rental.getRentedAtStr(),              labelFont, valueFont);

        // Total row
        PdfPCell labelCell = styledCell("TOTAL AMOUNT", labelFont);
        labelCell.setBackgroundColor(CARD_BG);
        labelCell.setPaddingTop(14);
        labelCell.setPaddingBottom(14);

        Font totalLabelFont = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD, MUTED);
        PdfPCell valueCell = new PdfPCell(new Phrase(
                "$" + String.format("%.2f", rental.getTotalPrice()), totalFont));
        valueCell.setBackgroundColor(CARD_BG);
        valueCell.setBorderColor(BORDER);
        valueCell.setBorderWidth(0.5f);
        valueCell.setPadding(12);

        table.addCell(labelCell);
        table.addCell(valueCell);
        doc.add(table);

        // ── Status Badge ───────────────────────────────────────────────────────
        String statusText = rental.getStatus() == Rental.Status.ACTIVE ? "● ACTIVE RENTAL" : "✓ RETURNED";
        Font statusFont = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD,
                rental.getStatus() == Rental.Status.ACTIVE ? GOLD : GREEN);
        Paragraph status = new Paragraph(statusText, statusFont);
        status.setAlignment(Element.ALIGN_CENTER);
        status.setSpacingAfter(30);
        doc.add(status);

        // ── Footer ─────────────────────────────────────────────────────────────
        LineSeparator line = new LineSeparator(0.5f, 80f, BORDER, Element.ALIGN_CENTER, -5);
        doc.add(new Chunk(line));

        Paragraph footer = new Paragraph("Built by Bikash Talukder  ·  Rentify v2.0  ·  Thank you for choosing us!", footerFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(14);
        doc.add(footer);

        doc.close();
        return out.toByteArray();
    }

    private void addRow(PdfPTable table, String label, String value, Font lf, Font vf) {
        table.addCell(styledCell(label, lf));
        PdfPCell vc = new PdfPCell(new Phrase(value != null ? value : "-", vf));
        vc.setBackgroundColor(CARD_BG);
        vc.setBorderColor(BORDER);
        vc.setBorderWidth(0.5f);
        vc.setPadding(10);
        table.addCell(vc);
    }

    private void addDivider(PdfPTable table) {
        for (int i = 0; i < 2; i++) {
            PdfPCell cell = new PdfPCell();
            cell.setBackgroundColor(BORDER);
            cell.setBorderWidth(0);
            cell.setFixedHeight(1f);
            table.addCell(cell);
        }
    }

    private PdfPCell styledCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(CARD_BG);
        cell.setBorderColor(BORDER);
        cell.setBorderWidth(0.5f);
        cell.setPadding(10);
        return cell;
    }
}
