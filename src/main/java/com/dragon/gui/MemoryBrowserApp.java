package com.dragon.gui;

import com.dragon.utils.memory.MemoryEntry;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.*;
import java.nio.file.*;
import java.security.spec.KeySpec;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;

public class MemoryBrowserApp extends JFrame {

    // ── Crypto constants ───────────────────────────────────────────────────
    private static final String AES_ALGO       = "AES/GCM/NoPadding";
    private static final String KDF_ALGO       = "PBKDF2WithHmacSHA256";
    private static final int    GCM_TAG_BITS   = 128;
    private static final int    GCM_IV_BYTES   = 12;
    private static final int    SALT_BYTES     = 16;
    private static final int    KDF_ITERATIONS = 310_000;
    private static final int    KEY_BITS       = 256;
    private static final int    MAGIC          = 0x56454354;

    // ── Palette ────────────────────────────────────────────────────────────
    private static final Color BG_DARK      = new Color(0x0D, 0x0F, 0x17);
    private static final Color BG_PANEL     = new Color(0x13, 0x16, 0x23);
    private static final Color BG_CARD      = new Color(0x1A, 0x1E, 0x30);
    private static final Color BG_ROW_ALT   = new Color(0x1E, 0x23, 0x38);
    private static final Color ACCENT       = new Color(0x4F, 0xC3, 0xF7);
    private static final Color TEXT_PRIMARY = new Color(0xE8, 0xEA, 0xF6);
    private static final Color TEXT_MUTED   = new Color(0x6B, 0x72, 0x9E);
    private static final Color TAG_BG       = new Color(0x2D, 0x1F, 0x5E);
    private static final Color DANGER       = new Color(0xF4, 0x3F, 0x5E);
    private static final Color SUCCESS      = new Color(0x22, 0xC5, 0x5E);
    private static final Color BORDER_COLOR = new Color(0x2A, 0x30, 0x4D);

    // ── Fonts ──────────────────────────────────────────────────────────────
    private static final Font FONT_MONO   = new Font("JetBrains Mono", Font.PLAIN, 12);
    private static final Font FONT_SANS   = new Font("Segoe UI",        Font.PLAIN, 13);
    private static final Font FONT_SANS_B = new Font("Segoe UI",        Font.BOLD,  14);
    private static final Font FONT_TITLE  = new Font("Segoe UI",        Font.BOLD,  20);
    private static final Font FONT_SMALL  = new Font("Segoe UI",        Font.PLAIN, 11);

    // ── Placeholder sentinel ───────────────────────────────────────────────
    // The placeholder is drawn via paintComponent, NOT via setText, so it
    // never triggers the DocumentListener and never feeds into applyFilter.
    private static final String SEARCH_PLACEHOLDER = "Search memories...";

    // ── State ──────────────────────────────────────────────────────────────
    private List<MemoryEntry> allMemories = new ArrayList<>();
    private List<MemoryEntry> shown       = new ArrayList<>();
    private String vectorPath  = System.getProperty("user.home") + "/dragon/memory.vector";
    private String passphrase  = "";

    // ── UI refs ────────────────────────────────────────────────────────────
    private MemoryTableModel tableModel;
    private JTable           table;
    private JTextArea        userArea;
    private JTextArea        replyArea;
    private JLabel           tagLabel;
    private JLabel           dateLabel;
    private JLabel           idLabel;
    private JLabel           statusLabel;
    private JLabel           countLabel;
    private JTextField       searchField;
    private JTextField       pathField;

    // ──────────────────────────────────────────────────────────────────────
    // Entry points
    // ──────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MemoryBrowserApp().setVisible(true));
    }

    public static void launch(List<MemoryEntry> memories) {
        SwingUtilities.invokeLater(() -> {
            MemoryBrowserApp app = new MemoryBrowserApp();
            app.allMemories.addAll(memories);
            app.applyFilter(""); // empty = show all immediately
            app.setVisible(true);
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────────────

    public MemoryBrowserApp() {
        super("Dragon AI - Memory Reader");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 780);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}

        applyGlobalUiDefaults();
        buildUi();
    }

    // ──────────────────────────────────────────────────────────────────────
    // UI construction
    // ──────────────────────────────────────────────────────────────────────

    private void buildUi() {
        JPanel root = darkPanel(new BorderLayout());
        setContentPane(root);
        root.add(buildTopBar(),    BorderLayout.NORTH);
        root.add(buildCenter(),    BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
    }

    // ── Top bar ────────────────────────────────────────────────────────────

    private JPanel buildTopBar() {
        JPanel bar = darkPanel(new BorderLayout(16, 0));
        bar.setBackground(BG_PANEL);
        bar.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, BORDER_COLOR),
                new EmptyBorder(14, 20, 14, 20)));

        // Left: ASCII title — no emoji font dependency
        JPanel left = darkPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JLabel title = new JLabel("Memory Reader");
        title.setFont(FONT_TITLE);
        title.setForeground(ACCENT);
        left.add(title);

        // Center: search field with painted placeholder (no setText trick)
        JPanel center = darkPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        searchField = buildSearchField();
        center.add(searchField);

        // Right: load button
        JPanel right = darkPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton loadBtn = accentButton("[R] Load File", ACCENT);
        loadBtn.addActionListener(e -> showLoadDialog());
        right.add(loadBtn);

        bar.add(left,   BorderLayout.WEST);
        bar.add(center, BorderLayout.CENTER);
        bar.add(right,  BorderLayout.EAST);
        return bar;
    }

    /**
     * Search field whose placeholder is painted, not set as text.
     * This means the DocumentListener never sees the placeholder string,
     * so applyFilter("Search memories...") is never accidentally called.
     */
    private JTextField buildSearchField() {
        JTextField f = new JTextField(34) {
            @Override
            protected void paintComponent(Graphics g) {
                // Draw rounded background
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 8, 8));
                super.paintComponent(g);

                // Draw placeholder only when empty and unfocused
                if (getText().isEmpty() && !isFocusOwner()) {
                    g2.setFont(getFont());
                    g2.setColor(TEXT_MUTED);
                    FontMetrics fm = g2.getFontMetrics();
                    Insets ins = getInsets();
                    int y = ins.top + (getHeight() - ins.top - ins.bottom - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString(SEARCH_PLACEHOLDER, ins.left + 4, y);
                }
                g2.dispose();
            }
        };
        f.setOpaque(false);
        f.setBackground(BG_CARD);
        f.setForeground(TEXT_PRIMARY);
        f.setCaretColor(ACCENT);
        f.setFont(FONT_SANS);
        f.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(6, 12, 6, 12)));

        // Filter on every keystroke — text is always real content, never placeholder
        f.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { applyFilter(f.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { applyFilter(f.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(f.getText()); }
        });

        // Repaint to show/hide placeholder on focus change
        f.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) { f.repaint(); }
            public void focusLost(FocusEvent e)   { f.repaint(); }
        });

        return f;
    }

    // ── Center split ───────────────────────────────────────────────────────

    private JSplitPane buildCenter() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildMemoryList(), buildDetailPanel());
        split.setDividerLocation(520);
        split.setDividerSize(4);
        split.setBackground(BORDER_COLOR);
        split.setBorder(null);
        split.setContinuousLayout(true);
        return split;
    }

    // ── Memory list ────────────────────────────────────────────────────────

    private JPanel buildMemoryList() {
        JPanel panel = darkPanel(new BorderLayout());
        panel.setBackground(BG_PANEL);

        JPanel header = darkPanel(new BorderLayout(8, 0));
        header.setBackground(BG_PANEL);
        header.setBorder(new EmptyBorder(10, 16, 10, 16));

        JLabel lbl = new JLabel("MEMORIES");
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lbl.setForeground(TEXT_MUTED);

        countLabel = new JLabel("0 entries");
        countLabel.setFont(FONT_SMALL);
        countLabel.setForeground(TEXT_MUTED);

        header.add(lbl,        BorderLayout.WEST);
        header.add(countLabel, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        tableModel = new MemoryTableModel();
        table = new JTable(tableModel);
        styleTable(table);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showDetail(table.getSelectedRow());
        });

        panel.add(darkScroll(table), BorderLayout.CENTER);
        return panel;
    }

    // ── Detail panel ───────────────────────────────────────────────────────

    private JPanel buildDetailPanel() {
        JPanel panel = darkPanel(new BorderLayout());
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel header = new JLabel("MEMORY DETAIL");
        header.setFont(new Font("Segoe UI", Font.BOLD, 10));
        header.setForeground(TEXT_MUTED);
        header.setBorder(new EmptyBorder(0, 0, 14, 0));
        panel.add(header, BorderLayout.NORTH);

        // Meta row — ASCII only
        JPanel meta = darkPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        idLabel   = metaLabel("--");
        dateLabel = metaLabel("--");
        tagLabel  = tagChip("--");
        JSeparator sep = new JSeparator(JSeparator.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 16));
        sep.setForeground(BORDER_COLOR);
        meta.add(idLabel);
        meta.add(sep);
        meta.add(dateLabel);
        meta.add(tagLabel);

        // Section labels — ASCII brackets, no emoji
        JLabel userLbl  = sectionLabel("[User]");
        JLabel replyLbl = sectionLabel("[Assistant]");
        userArea  = styledTextArea();
        replyArea = styledTextArea();

        JPanel body = darkPanel(new GridBagLayout());
        body.setBackground(BG_DARK);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.weightx = 1;

        gbc.gridy = 0; gbc.weighty = 0; gbc.insets = new Insets(0,  0, 8,  0); body.add(meta,                  gbc);
        gbc.gridy = 1; gbc.weighty = 0; gbc.insets = new Insets(10, 0, 4,  0); body.add(userLbl,               gbc);
        gbc.gridy = 2; gbc.weighty = 1; gbc.insets = new Insets(0,  0, 10, 0); body.add(darkScroll(userArea),  gbc);
        gbc.gridy = 3; gbc.weighty = 0; gbc.insets = new Insets(4,  0, 4,  0); body.add(replyLbl,              gbc);
        gbc.gridy = 4; gbc.weighty = 1; gbc.insets = new Insets(0,  0, 0,  0); body.add(darkScroll(replyArea), gbc);

        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    // ── Status bar ─────────────────────────────────────────────────────────

    private JPanel buildStatusBar() {
        JPanel bar = darkPanel(new BorderLayout());
        bar.setBackground(BG_PANEL);
        bar.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, BORDER_COLOR),
                new EmptyBorder(8, 20, 8, 20)));

        statusLabel = new JLabel("Ready -- load a .vector file to begin");
        statusLabel.setFont(FONT_SMALL);
        statusLabel.setForeground(TEXT_MUTED);
        bar.add(statusLabel, BorderLayout.WEST);

        JLabel hint = new JLabel("AES-256-GCM  |  PBKDF2-SHA256");
        hint.setFont(FONT_SMALL);
        hint.setForeground(new Color(0x3A, 0x40, 0x62));
        bar.add(hint, BorderLayout.EAST);
        return bar;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Load dialog
    // ──────────────────────────────────────────────────────────────────────

    private void showLoadDialog() {
        JDialog dlg = new JDialog(this, "Load Memory File", true);
        dlg.setSize(520, 220);
        dlg.setLocationRelativeTo(this);

        JPanel panel = darkPanel(new GridBagLayout());
        panel.setBackground(BG_PANEL);
        panel.setBorder(new EmptyBorder(20, 24, 20, 24));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 0, 6, 8);

        // Path row
        pathField = new JTextField(vectorPath, 28);
        styleInputField(pathField);

        JButton browse = smallButton("Browse...");
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Vector files (*.vector)", "vector"));
            if (fc.showOpenDialog(dlg) == JFileChooser.APPROVE_OPTION)
                pathField.setText(fc.getSelectedFile().getAbsolutePath());
        });

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0; panel.add(rowLabel("File:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;                 panel.add(pathField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;                 panel.add(browse, gbc);

        // Passphrase row
        JPasswordField passField = new JPasswordField(20);
        styleInputField(passField);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; gbc.gridwidth = 1; panel.add(rowLabel("Passphrase:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.gridwidth = 2;                panel.add(passField, gbc);
        gbc.gridwidth = 1;

        // Buttons
        JButton loadBtn   = accentButton("  Load  ", ACCENT);
        JButton cancelBtn = smallButton("Cancel");
        cancelBtn.addActionListener(e -> dlg.dispose());
        loadBtn.addActionListener(e -> {
            vectorPath = pathField.getText().trim();
            passphrase = new String(passField.getPassword());
            dlg.dispose();
            loadFromFile();
        });

        JPanel btns = darkPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.add(cancelBtn);
        btns.add(loadBtn);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 1; gbc.gridwidth = 3;
        gbc.insets = new Insets(14, 0, 0, 0);
        panel.add(btns, gbc);

        dlg.setContentPane(panel);
        dlg.setVisible(true);
    }

    // ──────────────────────────────────────────────────────────────────────
    // File loading + decryption
    // ──────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void loadFromFile() {
        Path path = Path.of(vectorPath);
        if (!Files.exists(path)) {
            setStatus("[ERROR] File not found: " + vectorPath, DANGER);
            return;
        }

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {

            int magic   = in.readInt();
            int version = in.readInt();

            if (magic != MAGIC) {
                setStatus("[ERROR] Invalid file format -- not a .vector file", DANGER);
                return;
            }

            byte[] salt       = in.readNBytes(SALT_BYTES);
            byte[] iv         = in.readNBytes(GCM_IV_BYTES);
            byte[] ciphertext = in.readAllBytes();

            SecretKey key       = deriveKey(passphrase.toCharArray(), salt);
            byte[]    plaintext = decrypt(ciphertext, key, iv);

            allMemories = (List<MemoryEntry>) deserialize(plaintext);

            // Pass empty string so ALL entries are shown immediately after load
            applyFilter("");

            setStatus("[OK] Loaded %d memories from '%s'  (v%d)"
                    .formatted(allMemories.size(), vectorPath, version), SUCCESS);

        } catch (Exception ex) {
            setStatus("[ERROR] " + ex.getMessage(), DANGER);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Filter
    // ──────────────────────────────────────────────────────────────────────

    private void applyFilter(String query) {
        String q = (query == null ? "" : query).trim().toLowerCase();
        shown = allMemories.stream()
                .filter(m -> q.isEmpty()
                        || contains(m.getUserMessage(),       q)
                        || contains(m.getAssistantResponse(), q)
                        || contains(m.getTag(),               q)
                        || contains(m.getId(),                q))
                .toList();

        tableModel.setData(shown);
        countLabel.setText(shown.size() + " / " + allMemories.size() + " entries");

        if (!shown.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
            showDetail(0);
        } else {
            clearDetail();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Detail display
    // ──────────────────────────────────────────────────────────────────────

    private void showDetail(int row) {
        if (row < 0 || row >= shown.size()) { clearDetail(); return; }
        MemoryEntry m = shown.get(row);

        userArea.setText(m.getUserMessage()        != null ? m.getUserMessage()        : "");
        replyArea.setText(m.getAssistantResponse() != null ? m.getAssistantResponse() : "");
        userArea.setCaretPosition(0);
        replyArea.setCaretPosition(0);

        idLabel.setText("ID: " + m.getId().substring(0, 8) + "...");
        dateLabel.setText(formatEpoch(m.getCreatedAt()));
        tagLabel.setText(m.getTag() != null && !m.getTag().isBlank() ? m.getTag() : "untagged");
    }

    private void clearDetail() {
        userArea.setText("");  replyArea.setText("");
        idLabel.setText("--"); dateLabel.setText("--"); tagLabel.setText("--");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Table model
    // ──────────────────────────────────────────────────────────────────────

    private static class MemoryTableModel extends AbstractTableModel {
        private final String[] COLS = {"#", "Date", "Tag", "User message"};
        private List<MemoryEntry> data = new ArrayList<>();

        void setData(List<MemoryEntry> d) { this.data = d; fireTableDataChanged(); }

        public int getRowCount()    { return data.size(); }
        public int getColumnCount() { return COLS.length; }
        public String getColumnName(int c) { return COLS[c]; }

        public Object getValueAt(int r, int c) {
            MemoryEntry m = data.get(r);
            return switch (c) {
                case 0 -> r + 1;
                case 1 -> formatEpoch(m.getCreatedAt());
                case 2 -> m.getTag() != null ? m.getTag() : "--";
                case 3 -> truncate(m.getUserMessage(), 70);
                default -> "";
            };
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Crypto
    // ──────────────────────────────────────────────────────────────────────

    private static SecretKey deriveKey(char[] pass, byte[] salt) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance(KDF_ALGO);
        KeySpec spec = new PBEKeySpec(pass, salt, KDF_ITERATIONS, KEY_BITS);
        return new SecretKeySpec(f.generateSecret(spec).getEncoded(), "AES");
    }

    private static byte[] decrypt(byte[] ct, SecretKey key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_ALGO);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(ct);
    }

    private static Object deserialize(byte[] b) throws Exception {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(b))) {
            return ois.readObject();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // UI helpers
    // ──────────────────────────────────────────────────────────────────────

    private void applyGlobalUiDefaults() {
        UIManager.put("ScrollBar.thumb",           new Color(0x2A, 0x30, 0x4D));
        UIManager.put("ScrollBar.track",           BG_DARK);
        UIManager.put("ScrollBar.thumbDarkShadow", BG_DARK);
        UIManager.put("ScrollBar.thumbHighlight",  BG_DARK);
        UIManager.put("ScrollBar.thumbShadow",     BG_DARK);
        UIManager.put("SplitPane.dividerSize",     4);
    }

    private void styleTable(JTable t) {
        t.setBackground(BG_PANEL);
        t.setForeground(TEXT_PRIMARY);
        t.setGridColor(BORDER_COLOR);
        t.setRowHeight(38);
        t.setFont(FONT_SANS);
        t.setSelectionBackground(new Color(0x1E, 0x3A, 0x5F));
        t.setSelectionForeground(TEXT_PRIMARY);
        t.setShowVerticalLines(false);
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setFillsViewportHeight(true);
        t.getTableHeader().setBackground(BG_CARD);
        t.getTableHeader().setForeground(TEXT_MUTED);
        t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        t.getTableHeader().setBorder(new MatteBorder(0, 0, 1, 0, BORDER_COLOR));
        t.getTableHeader().setReorderingAllowed(false);

        int[] widths = {40, 130, 90, -1};
        for (int i = 0; i < widths.length; i++) {
            if (widths[i] > 0) {
                t.getColumnModel().getColumn(i).setMaxWidth(widths[i]);
                t.getColumnModel().getColumn(i).setMinWidth(widths[i]);
            }
        }

        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object val,
                                                           boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(tbl, val, sel, foc, row, col);
                setBorder(new EmptyBorder(0, 12, 0, 12));
                setFont(col == 3 ? FONT_SANS : FONT_SMALL);
                if (!sel) {
                    setBackground(row % 2 == 0 ? BG_PANEL : BG_ROW_ALT);
                    setForeground(col == 2 ? ACCENT : TEXT_PRIMARY);
                }
                return this;
            }
        });
    }

    /** Styles a JTextField or JPasswordField consistently for dialog inputs. */
    private void styleInputField(JComponent f) {
        f.setBackground(BG_CARD);
        f.setForeground(TEXT_PRIMARY);
        f.setFont(FONT_SANS);
        f.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(6, 12, 6, 12)));
        if (f instanceof JTextField  tf) tf.setCaretColor(ACCENT);
        if (f instanceof JPasswordField pf) pf.setCaretColor(ACCENT);
    }

    private JButton accentButton(String text, Color color) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = getModel().isPressed()  ? color.darker()
                        : getModel().isRollover() ? color.brighter()
                        : color;
                g2.setColor(base);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 8, 8));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setForeground(Color.WHITE);
        b.setFont(FONT_SANS_B);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new EmptyBorder(8, 18, 8, 18));
        return b;
    }

    private JButton smallButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(BG_CARD);
        b.setForeground(TEXT_PRIMARY);
        b.setFont(FONT_SMALL);
        b.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(5, 12, 5, 12)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JTextArea styledTextArea() {
        JTextArea a = new JTextArea();
        a.setBackground(BG_CARD);
        a.setForeground(TEXT_PRIMARY);
        a.setFont(FONT_MONO);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setEditable(false);
        a.setBorder(new EmptyBorder(12, 14, 12, 14));
        a.setCaretColor(ACCENT);
        return a;
    }

    private JScrollPane darkScroll(Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(new LineBorder(BORDER_COLOR, 1));
        sp.getViewport().setBackground(BG_CARD);
        sp.setBackground(BG_DARK);
        sp.getVerticalScrollBar().setBackground(BG_DARK);
        sp.getHorizontalScrollBar().setBackground(BG_DARK);
        return sp;
    }

    private JPanel darkPanel(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setBackground(BG_DARK);
        p.setOpaque(true);
        return p;
    }

    private JLabel metaLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_SMALL);
        l.setForeground(TEXT_MUTED);
        return l;
    }

    private JLabel tagChip(String text) {
        JLabel l = new JLabel(" " + text + " ") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(TAG_BG);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 6, 6));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setFont(FONT_SMALL);
        l.setForeground(ACCENT);
        l.setOpaque(false);
        l.setBorder(new EmptyBorder(2, 6, 2, 6));
        return l;
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_SANS_B);
        l.setForeground(TEXT_MUTED);
        return l;
    }

    private JLabel rowLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_SANS);
        l.setForeground(TEXT_MUTED);
        return l;
    }

    private void setStatus(String msg, Color color) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(msg);
            statusLabel.setForeground(color);
        });
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static boolean contains(String s, String q) {
        return s != null && s.toLowerCase().contains(q);
    }

    private static String formatEpoch(long epochSeconds) {
        if (epochSeconds <= 0) return "--";
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .format(LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault()));
    }
}