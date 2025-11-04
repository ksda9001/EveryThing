package org.everything;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.util.List;
import java.util.Map;

public class SearchFrame extends JFrame {
    private final IndexStore store;
    private final JTextField query = new JTextField();
    private final ResultModel model = new ResultModel();
    private final JTable table = new JTable(model);
    private final TableRowSorter<ResultModel> sorter = new TableRowSorter<>(model);
    private final JLabel statusBar = new JLabel("Ready");

    public SearchFrame(IndexStore store) {
        super("Everything-like Search");
        this.store = store;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 600);
        setLayout(new BorderLayout());

        // 顶部搜索框
        add(query, BorderLayout.NORTH);

        // 表格 + 排序器
        table.setRowSorter(sorter);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // 底部状态栏
        add(statusBar, BorderLayout.SOUTH);

        // 输入监听
        query.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { search(); }
            public void removeUpdate(DocumentEvent e) { search(); }
            public void changedUpdate(DocumentEvent e) { search(); }
        });

        // 双击打开
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openSelected();
            }
        });

        // 右键菜单
        JPopupMenu popup = new JPopupMenu();
        JMenuItem openItem = new JMenuItem("打开");
        JMenuItem showItem = new JMenuItem("在资源管理器中显示");
        JMenuItem copyItem = new JMenuItem("复制路径");

        openItem.addActionListener(e -> openSelected());
        showItem.addActionListener(e -> showInExplorer());
        copyItem.addActionListener(e -> copyPath());

        popup.add(openItem);
        popup.add(showItem);
        popup.add(copyItem);
        table.setComponentPopupMenu(popup);

        // 键盘快捷键
        table.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    openSelected();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_C && e.isControlDown()) {
                    copyPath();
                    e.consume();
                }
            }
        });

        // 定时刷新状态栏（显示索引进度/完成情况）
        new javax.swing.Timer(1000, e -> updateStatusBar()).start();
    }

    private void search() {
        String q = query.getText().trim();
        if (q.isEmpty()) {
            model.setResults(java.util.Collections.<IndexStore.Node>emptyList());
            statusBar.setText("0 / " + store.getTotalCount());
            return;
        }

        // 解析过滤器
        String keyword = null, extFilter = null, typeFilter = null, pathFilter = null;
        String[] parts = q.split("\\s+");
        for (String p : parts) {
            if (p.startsWith("ext:")) extFilter = p.substring(4);
            else if (p.startsWith("type:")) typeFilter = p.substring(5).toLowerCase();
            else if (p.startsWith("path:")) pathFilter = p.substring(5);
            else keyword = (keyword == null ? p : keyword + " " + p);
        }

        List<IndexStore.Node> results = store.searchWithFilters(keyword, extFilter, typeFilter, pathFilter, 500);
        model.setResults(results);

        statusBar.setText(results.size() + " / " + store.getTotalCount());
    }

    private IndexStore.Node getSelectedNode() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        int modelRow = table.convertRowIndexToModel(row);
        return model.getNodeAt(modelRow);
    }

    private void openSelected() {
        IndexStore.Node n = getSelectedNode();
        if (n == null) return;
        String path = store.resolvePath(n);
        try {
            Desktop.getDesktop().open(new File(path));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "无法打开: " + path + "\n" + ex.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showInExplorer() {
        IndexStore.Node n = getSelectedNode();
        if (n == null) return;
        String path = store.resolvePath(n);
        try {
            Runtime.getRuntime().exec(new String[]{"explorer.exe", "/select,", path});
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "无法在资源管理器中显示: " + path,
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void copyPath() {
        IndexStore.Node n = getSelectedNode();
        if (n == null) return;
        String path = store.resolvePath(n);
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(path), null);
    }

    private void updateStatusBar() {
        Map<String, Integer> snapshot = store.getProgressSnapshot();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : snapshot.entrySet()) {
            if (sb.length() > 0) sb.append(" | ");
            String vol = e.getKey();
            if (store.isComplete(vol)) {
                sb.append(vol).append(": 索引完成");
            } else {
                sb.append(vol).append(": ").append(e.getValue()).append(" 项");
            }
        }
        if (sb.length() == 0) {
            sb.append(model.getRowCount()).append(" / ").append(store.getTotalCount());
        }
        statusBar.setText(sb.toString());
    }

    static class ResultModel extends AbstractTableModel {
        private List<IndexStore.Node> results = java.util.Collections.emptyList();
        private final String[] cols = {"Name", "Path", "Type", "Volume"};

        public void setResults(List<IndexStore.Node> r) {
            this.results = r;
            fireTableDataChanged();
        }

        public int getRowCount() { return results.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int column) { return cols[column]; }

        public Object getValueAt(int rowIndex, int columnIndex) {
            IndexStore.Node n = results.get(rowIndex);
            switch (columnIndex) {
                case 0: return n.name;
                case 1: return Main.globalStore.resolvePath(n);
                case 2: return n.isDir ? "Folder" : "File";
                case 3: return n.volume;
                default: return "";
            }
        }

        public IndexStore.Node getNodeAt(int row) {
            return results.get(row);
        }
    }
}
