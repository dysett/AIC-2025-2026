import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import zlagoda.Db;
import zlagoda.Session;
import zlagoda.ui.ReportPanel;

public final class CourseworkReportAssets {
    private static final String QUERY_1 = "Звіт 3Т: продажі категорій за період";
    private static final String QUERY_2 = "Звіт 3Т: клієнти, що купили всі категорії";

    private CourseworkReportAssets() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        Db.initialize();
        Files.createDirectories(Path.of("report_assets"));

        SwingUtilities.invokeAndWait(() -> {
            try {
                Session manager = new Session(1, "manager", "MANAGER", "E000000001", "Klymenko Iryna");
                saveReport(manager, QUERY_1, "2026-06-01", "2026-06-30", "query1");
                saveReport(manager, QUERY_2, "2026-06-01", "2026-06-30", "query2");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static void saveReport(Session session, String reportName, String fromDate, String toDate, String prefix) throws Exception {
        ReportPanel panel = new ReportPanel(session);
        configure(panel, reportName, fromDate, toDate);

        ImageIO.write(render(panel, 1160, 680), "png", Path.of("report_assets", prefix + "_interface.png").toFile());
        ImageIO.write(renderResultTable(table(panel), 1080, 210), "png", Path.of("report_assets", prefix + "_result.png").toFile());
    }

    private static void configure(ReportPanel panel, String reportName, String fromDate, String toDate) throws Exception {
        combo(panel).setSelectedItem(reportName);
        text(panel, "from").setText(fromDate);
        text(panel, "to").setText(toDate);
        runReport(panel);
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<String> combo(ReportPanel panel) throws Exception {
        Field field = ReportPanel.class.getDeclaredField("report");
        field.setAccessible(true);
        return (JComboBox<String>) field.get(panel);
    }

    private static JTextField text(ReportPanel panel, String name) throws Exception {
        Field field = ReportPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return (JTextField) field.get(panel);
    }

    private static JTable table(ReportPanel panel) throws Exception {
        Field field = ReportPanel.class.getDeclaredField("table");
        field.setAccessible(true);
        return (JTable) field.get(panel);
    }

    private static void runReport(ReportPanel panel) throws Exception {
        Method method = ReportPanel.class.getDeclaredMethod("runReport");
        method.setAccessible(true);
        method.invoke(panel);
    }

    private static BufferedImage render(Component component, int width, int height) {
        component.setSize(width, height);
        component.setBounds(0, 0, width, height);
        layout(component);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        component.paint(graphics);
        graphics.dispose();
        return image;
    }

    private static void layout(Component component) {
        component.setVisible(true);
        component.doLayout();
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                layout(child);
            }
        }
    }

    private static BufferedImage renderResultTable(JTable source, int width, int height) {
        JTable copy = new JTable(source.getModel());
        copy.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        copy.setRowHeight(28);
        copy.getTableHeader().setReorderingAllowed(false);
        int rows = Math.min(copy.getRowCount(), 5);
        int headerHeight = copy.getTableHeader().getPreferredSize().height;
        int tableHeight = Math.max(rows, 1) * copy.getRowHeight();
        int imageHeight = Math.min(height, headerHeight + tableHeight + 8);

        copy.setSize(width, tableHeight);
        copy.doLayout();
        copy.getTableHeader().setSize(width, headerHeight);
        copy.getTableHeader().doLayout();

        BufferedImage image = new BufferedImage(width, imageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, imageHeight);
        copy.getTableHeader().paint(graphics);
        graphics.translate(0, headerHeight);
        copy.paint(graphics);
        graphics.dispose();
        return image;
    }
}
