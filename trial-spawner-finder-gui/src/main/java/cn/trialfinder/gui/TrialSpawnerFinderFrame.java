package cn.trialfinder.gui;

import cn.minecraftfinder.core.ProgressUpdate;
import cn.minecraftfinder.core.ResultFiles;
import cn.trialfinder.gui.TrialSearchRunner.FormattedHit;
import cn.trialfinder.gui.TrialSearchRunner.Outcome;
import cn.trialfinder.gui.TrialSearchRunner.SearchBoundsInput;
import cn.trialfinder.gui.TrialSearchRunner.TrialParams;
import cn.trialfinder.model.SearchResult;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.FontUIResource;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Taskbar;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TrialSpawnerFinder Swing 主界面。
 */
public final class TrialSpawnerFinderFrame extends JFrame {
    private static final float UI_FONT_SIZE = 13f;
    private static final float RESULT_FONT_SIZE = 12f;
    private static Font appFont;

    private static final int DEFAULT_MIN = -65536;
    private static final int DEFAULT_MAX = 65535;
    private static final int DEFAULT_SIDE = 131072;
    private static final int WORLD_SIDE = 60_000_001;
    private static final int WORLD_MIN = -30_000_000;
    private static final int WORLD_MAX = 30_000_000;
    private static final int LIST_DEFAULT_SIDE = 8192;
    private static final int LIST_DEFAULT_MIN = -4096;
    private static final int LIST_DEFAULT_MAX = 4095;

    private final TrialSearchRunner runner = new TrialSearchRunner();
    private final AtomicBoolean searchBusy = new AtomicBoolean(false);

    private JTabbedPane tabs;

    // ---- 单种子 ----
    private JTextField seedField;
    private JComboBox<String> versionCombo;
    private JTextField minXField;
    private JTextField maxXField;
    private JTextField minZField;
    private JTextField maxZField;
    private JCheckBox squareCheck;
    private JTextField squareSideField;
    private JCheckBox radiusCheck;
    private JTextField radiusField;
    private JTextField threadField;
    private JTextField clusterRadiusField;
    private JTextField simDistanceField;
    private JCheckBox trialCircleCheck;
    private JTextField minStructuresField;
    private JTextField minSpawnersField;
    private JTextField slowRatioField;
    private JButton startButton;
    private JButton pauseButton;
    private JButton stopButton;
    private JButton resetBoundsButton;
    private JButton worldBorderButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JTextArea resultArea;
    private JButton exportButton;
    private TrialParams singleExportParams;
    private List<SearchResult> singleExportResults = List.of();
    private boolean squareUpdating;
    private boolean simDistanceUpdating;
    private volatile boolean singlePaused;
    private volatile boolean singleRunning;

    // ---- 列表 ----
    private JTextField listSeedFileField;
    private JComboBox<String> listVersionCombo;
    private JTextField listMinXField;
    private JTextField listMaxXField;
    private JTextField listMinZField;
    private JTextField listMaxZField;
    private JCheckBox listSquareCheck;
    private JTextField listSquareSideField;
    private JCheckBox listRadiusCheck;
    private JTextField listRadiusField;
    private JTextField listThreadField;
    private JTextField listClusterRadiusField;
    private JTextField listSimDistanceField;
    private JCheckBox listTrialCircleCheck;
    private JTextField listMinStructuresField;
    private JTextField listMinSpawnersField;
    private JTextField listSlowRatioField;
    private JButton listStartButton;
    private JButton listPauseButton;
    private JButton listStopButton;
    private JButton listResetBoundsButton;
    private JProgressBar listProgressBar;
    private JLabel listStatusLabel;
    private JTextArea listResultArea;
    private JButton listExportButton;
    private final List<PendingExport> listExportBuffer = new ArrayList<>();
    private boolean listSquareUpdating;
    private boolean listSimDistanceUpdating;
    private volatile boolean listPaused;
    private volatile boolean listRunning;
    private final AtomicBoolean listStopRequested = new AtomicBoolean(false);

    private record PendingExport(TrialParams params, List<SearchResult> results) {
    }

    public TrialSpawnerFinderFrame() {
        super("TrialSpawnerFinder");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1500, 1000);
        setLocationRelativeTo(null);
        applyAppIcon();
        tabs = new JTabbedPane();
        tabs.addTab("单种子搜索", createSingleSeedPanel());
        tabs.addTab("从种子列表搜索", createListSearchPanel());
        setContentPane(tabs);
        applyAppFont(this);
        applyBoundFieldEnableState(true);
        applyListBoundFieldEnableState(true);
    }

    private void applyAppIcon() {
        Image icon = loadAppIcon();
        if (icon == null) {
            return;
        }
        setIconImage(icon);
        try {
            if (Taskbar.isTaskbarSupported()) {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.setIconImage(icon);
                }
            }
        } catch (Exception ignored) {
            // 部分桌面环境不允许设置任务栏图标
        }
    }

    /** 优先 classpath（打包后），其次项目根目录 / 工作目录下的 {@code icon.png}。 */
    static Image loadAppIcon() {
        URL resource = TrialSpawnerFinderFrame.class.getResource("/icon.png");
        if (resource != null) {
            Image image = new ImageIcon(resource).getImage();
            if (image.getWidth(null) > 0) {
                return image;
            }
        }
        for (Path candidate : List.of(
                Path.of("icon.png").toAbsolutePath().normalize(),
                TrialSearchRunner.resolveWorkDirectory().resolve("icon.png"))) {
            if (Files.isRegularFile(candidate)) {
                Image image = new ImageIcon(candidate.toString()).getImage();
                if (image.getWidth(null) > 0) {
                    return image;
                }
            }
        }
        return null;
    }

    /** 加载根目录 / classpath 的 {@code font.ttf}，并写入 UIManager 默认字体。 */
    static void installAppFont() {
        appFont = loadAppFont();
        if (appFont == null) {
            return;
        }
        FontUIResource ui = new FontUIResource(appFont.deriveFont(Font.PLAIN, UI_FONT_SIZE));
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource original) {
                UIManager.put(key, new FontUIResource(appFont.deriveFont(
                        original.getStyle(), original.getSize2D() > 0 ? original.getSize2D() : UI_FONT_SIZE)));
            } else if (value instanceof Font original) {
                UIManager.put(key, appFont.deriveFont(
                        original.getStyle(), original.getSize2D() > 0 ? original.getSize2D() : UI_FONT_SIZE));
            }
        }
        UIManager.put("defaultFont", ui);
    }

    static Font loadAppFont() {
        try (InputStream in = TrialSpawnerFinderFrame.class.getResourceAsStream("/font.ttf")) {
            if (in != null) {
                Font font = Font.createFont(Font.TRUETYPE_FONT, in);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                return font;
            }
        } catch (Exception ignored) {
            // fall through to file path
        }
        for (Path candidate : List.of(
                Path.of("font.ttf").toAbsolutePath().normalize(),
                TrialSearchRunner.resolveWorkDirectory().resolve("font.ttf"))) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try (InputStream in = Files.newInputStream(candidate)) {
                Font font = Font.createFont(Font.TRUETYPE_FONT, in);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                return font;
            } catch (Exception ignored) {
                // try next candidate
            }
        }
        return null;
    }

    private static Font uiFont(float size) {
        if (appFont != null) {
            return appFont.deriveFont(Font.PLAIN, size);
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(size));
    }

    private static void applyAppFont(Component root) {
        if (appFont == null) {
            return;
        }
        float size = root instanceof JTextArea ? RESULT_FONT_SIZE : UI_FONT_SIZE;
        if (!(root instanceof JTextArea)
                && root.getFont() != null
                && root.getFont().getSize2D() > 0) {
            size = root.getFont().getSize2D();
        }
        root.setFont(appFont.deriveFont(Font.PLAIN, size));
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyAppFont(child);
            }
        }
    }

    private JPanel createSingleSeedPanel() {
        JPanel left = buildFormPanel(false);
        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(uiFont(RESULT_FONT_SIZE));
        resultArea.setBorder(BorderFactory.createTitledBorder("搜索结果"));
        exportButton = new JButton("导出结果");
        exportButton.setEnabled(false);
        exportButton.addActionListener(e -> exportSingleResults());
        JPanel right = new JPanel(new BorderLayout(0, 4));
        right.add(new JScrollPane(resultArea), BorderLayout.CENTER);
        JPanel exportBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        exportBar.add(exportButton);
        right.add(exportBar, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(left), right);
        split.setDividerLocation(620);
        split.setResizeWeight(0.45);
        JPanel root = new JPanel(new BorderLayout());
        root.add(split, BorderLayout.CENTER);
        return root;
    }

    private JPanel createListSearchPanel() {
        JPanel left = buildFormPanel(true);
        listResultArea = new JTextArea();
        listResultArea.setEditable(false);
        listResultArea.setFont(uiFont(RESULT_FONT_SIZE));
        listResultArea.setBorder(BorderFactory.createTitledBorder("搜索结果"));
        listExportButton = new JButton("导出结果");
        listExportButton.setEnabled(false);
        listExportButton.addActionListener(e -> exportListResults());
        JPanel right = new JPanel(new BorderLayout(0, 4));
        right.add(new JScrollPane(listResultArea), BorderLayout.CENTER);
        JPanel exportBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        exportBar.add(listExportButton);
        right.add(exportBar, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(left), right);
        split.setDividerLocation(620);
        split.setResizeWeight(0.45);
        JPanel root = new JPanel(new BorderLayout());
        root.add(split, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildFormPanel(boolean listMode) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        // 种子
        if (listMode) {
            listSeedFileField = new JTextField();
            JButton browse = new JButton("导入种子文件…");
            browse.addActionListener(e -> selectSeedFile());
            c.gridx = 0;
            c.gridy = row;
            c.weightx = 0;
            panel.add(new JLabel("种子文件:"), c);
            c.gridx = 1;
            c.weightx = 1;
            panel.add(listSeedFileField, c);
            c.gridx = 2;
            c.weightx = 0;
            panel.add(browse, c);
            row++;
        } else {
            seedField = new JTextField();
            row = addLabeled(panel, c, row, "种子:", seedField);
        }

        // 线程数
        String threads = String.valueOf(Runtime.getRuntime().availableProcessors());
        if (listMode) {
            listThreadField = new JTextField(threads);
            row = addLabeled(panel, c, row, "线程数:", listThreadField);
        } else {
            threadField = new JTextField(threads);
            row = addLabeled(panel, c, row, "线程数:", threadField);
        }

        // 试炼刷怪笼专用
        if (listMode) {
            listClusterRadiusField = new JTextField("160");
            listSimDistanceField = new JTextField("10");
            listTrialCircleCheck = new JCheckBox("是否圆形区域", false);
            listMinStructuresField = new JTextField("1");
            listMinSpawnersField = new JTextField("20");
            listSlowRatioField = new JTextField("1/6");
            row = addLabeled(panel, c, row, "统计半径(方块):", listClusterRadiusField);
            row = addLabeled(panel, c, row, "模拟距离区块数:", listSimDistanceField);
            c.gridx = 0;
            c.gridy = row++;
            c.gridwidth = 3;
            panel.add(listTrialCircleCheck, c);
            c.gridwidth = 1;
            row = addLabeled(panel, c, row, "最少密室数:", listMinStructuresField);
            row = addLabeled(panel, c, row, "最少刷怪笼数:", listMinSpawnersField);
            row = addLabeled(panel, c, row, "慢速笼折算比例:", listSlowRatioField);
            listSimDistanceField.getDocument().addDocumentListener(simpleListener(this::onListSimDistanceChanged));
        } else {
            clusterRadiusField = new JTextField("160");
            simDistanceField = new JTextField("10");
            trialCircleCheck = new JCheckBox("是否圆形区域", false);
            minStructuresField = new JTextField("1");
            minSpawnersField = new JTextField("20");
            slowRatioField = new JTextField("1/6");
            row = addLabeled(panel, c, row, "统计半径(方块):", clusterRadiusField);
            row = addLabeled(panel, c, row, "模拟距离区块数:", simDistanceField);
            c.gridx = 0;
            c.gridy = row++;
            c.gridwidth = 3;
            panel.add(trialCircleCheck, c);
            c.gridwidth = 1;
            row = addLabeled(panel, c, row, "最少密室数:", minStructuresField);
            row = addLabeled(panel, c, row, "最少刷怪笼数:", minSpawnersField);
            row = addLabeled(panel, c, row, "慢速笼折算比例:", slowRatioField);
            simDistanceField.getDocument().addDocumentListener(simpleListener(this::onSimDistanceChanged));
        }

        // 公共搜索范围
        if (listMode) {
            listMinXField = new JTextField(String.valueOf(LIST_DEFAULT_MIN));
            listMaxXField = new JTextField(String.valueOf(LIST_DEFAULT_MAX));
            listMinZField = new JTextField(String.valueOf(LIST_DEFAULT_MIN));
            listMaxZField = new JTextField(String.valueOf(LIST_DEFAULT_MAX));
            row = addLabeled(panel, c, row, "MinX:", listMinXField);
            row = addLabeled(panel, c, row, "MaxX:", listMaxXField);
            row = addLabeled(panel, c, row, "MinZ:", listMinZField);
            row = addLabeled(panel, c, row, "MaxZ:", listMaxZField);
            listSquareCheck = new JCheckBox("正方形区域", true);
            listSquareSideField = new JTextField(String.valueOf(LIST_DEFAULT_SIDE));
            row = addCheckField(panel, c, row, listSquareCheck, listSquareSideField);
            listRadiusCheck = new JCheckBox("半径", false);
            listRadiusField = new JTextField("65536");
            row = addCheckField(panel, c, row, listRadiusCheck, listRadiusField);
            listSquareCheck.addActionListener(e -> onListSquareToggled());
            listRadiusCheck.addActionListener(e -> onListRadiusToggled());
            listSquareSideField.getDocument().addDocumentListener(simpleListener(() -> {
                if (!listSquareUpdating) {
                    applyListSquareSideToBounds();
                }
            }));
            listResetBoundsButton = new JButton("重置搜索范围为默认");
            listResetBoundsButton.addActionListener(e -> applyListSearchBounds(
                    LIST_DEFAULT_MIN, LIST_DEFAULT_MAX, LIST_DEFAULT_MIN, LIST_DEFAULT_MAX));
        } else {
            minXField = new JTextField(String.valueOf(DEFAULT_MIN));
            maxXField = new JTextField(String.valueOf(DEFAULT_MAX));
            minZField = new JTextField(String.valueOf(DEFAULT_MIN));
            maxZField = new JTextField(String.valueOf(DEFAULT_MAX));
            row = addLabeled(panel, c, row, "MinX:", minXField);
            row = addLabeled(panel, c, row, "MaxX:", maxXField);
            row = addLabeled(panel, c, row, "MinZ:", minZField);
            row = addLabeled(panel, c, row, "MaxZ:", maxZField);
            squareCheck = new JCheckBox("正方形区域", true);
            squareSideField = new JTextField(String.valueOf(DEFAULT_SIDE));
            row = addCheckField(panel, c, row, squareCheck, squareSideField);
            radiusCheck = new JCheckBox("半径", false);
            radiusField = new JTextField("65536");
            row = addCheckField(panel, c, row, radiusCheck, radiusField);
            squareCheck.addActionListener(e -> onSquareToggled());
            radiusCheck.addActionListener(e -> onRadiusToggled());
            squareSideField.getDocument().addDocumentListener(simpleListener(() -> {
                if (!squareUpdating) {
                    applySquareSideToBounds();
                }
            }));
            resetBoundsButton = new JButton("重置搜索范围为默认");
            resetBoundsButton.addActionListener(e ->
                    applySearchBounds(DEFAULT_MIN, DEFAULT_MAX, DEFAULT_MIN, DEFAULT_MAX));
            worldBorderButton = new JButton("重置搜索范围为世界边界");
            worldBorderButton.addActionListener(e -> resetSearchToWorldBorder());
        }

        // MC 版本
        if (listMode) {
            listVersionCombo = new JComboBox<>(TrialSearchRunner.SUPPORTED_MC_VERSIONS);
            row = addLabeled(panel, c, row, "MC版本:", listVersionCombo);
        } else {
            versionCombo = new JComboBox<>(TrialSearchRunner.SUPPORTED_MC_VERSIONS);
            row = addLabeled(panel, c, row, "MC版本:", versionCombo);
        }

        // 弹性空白：把重置与控制按钮顶到列底
        c.gridx = 0;
        c.gridy = row++;
        c.weighty = 1;
        c.gridwidth = 3;
        c.fill = GridBagConstraints.BOTH;
        panel.add(new JPanel(), c);
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;

        // 重置范围 + 开始/暂停/停止（整行居中）
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        if (listMode) {
            buttons.add(listResetBoundsButton);
            listStartButton = new JButton("开始");
            listPauseButton = new JButton("暂停");
            listStopButton = new JButton("停止");
            listPauseButton.setEnabled(false);
            listStopButton.setEnabled(false);
            listStartButton.addActionListener(e -> startListSearch());
            listPauseButton.addActionListener(e -> toggleListPause());
            listStopButton.addActionListener(e -> stopListSearch());
            buttons.add(listStartButton);
            buttons.add(listPauseButton);
            buttons.add(listStopButton);
        } else {
            buttons.add(resetBoundsButton);
            buttons.add(worldBorderButton);
            startButton = new JButton("开始");
            pauseButton = new JButton("暂停");
            stopButton = new JButton("停止");
            pauseButton.setEnabled(false);
            stopButton.setEnabled(false);
            startButton.addActionListener(e -> startSingleSearch());
            pauseButton.addActionListener(e -> toggleSinglePause());
            stopButton.addActionListener(e -> stopSingleSearch());
            buttons.add(startButton);
            buttons.add(pauseButton);
            buttons.add(stopButton);
        }
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = 3;
        panel.add(buttons, c);

        if (listMode) {
            listProgressBar = new JProgressBar(0, 1000);
            listProgressBar.setStringPainted(true);
            listProgressBar.setString("待命");
            c.gridy = row++;
            panel.add(listProgressBar, c);
            listStatusLabel = new JLabel(" ");
            c.gridy = row;
            panel.add(listStatusLabel, c);
        } else {
            progressBar = new JProgressBar(0, 1000);
            progressBar.setStringPainted(true);
            progressBar.setString("待命");
            c.gridy = row++;
            panel.add(progressBar, c);
            statusLabel = new JLabel(" ");
            c.gridy = row;
            panel.add(statusLabel, c);
        }

        return panel;
    }

    private static int addLabeled(JPanel panel, GridBagConstraints c, int row, String label, java.awt.Component field) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        c.gridwidth = 1;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        c.gridwidth = 2;
        panel.add(field, c);
        c.gridwidth = 1;
        return row + 1;
    }

    private static int addCheckField(JPanel panel, GridBagConstraints c, int row,
                                     JCheckBox check, JTextField field) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        c.gridwidth = 1;
        panel.add(check, c);
        c.gridx = 1;
        c.weightx = 1;
        c.gridwidth = 2;
        panel.add(field, c);
        c.gridwidth = 1;
        return row + 1;
    }

    private static javax.swing.event.DocumentListener simpleListener(Runnable action) {
        return new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                action.run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                action.run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                action.run();
            }
        };
    }

    // ---------------- 范围控件 ----------------

    private void onSquareToggled() {
        if (squareCheck.isSelected()) {
            radiusCheck.setSelected(false);
            applySquareSideToBounds();
        }
        applyBoundFieldEnableState(!singleRunning || singlePaused);
        // 运行中暂停时不允许改范围：再次收紧
        if (singleRunning) {
            applySearchParamLock(singlePaused);
        }
    }

    private void onRadiusToggled() {
        if (radiusCheck.isSelected()) {
            squareCheck.setSelected(false);
        }
        applyBoundFieldEnableState(!singleRunning || singlePaused);
        if (singleRunning) {
            applySearchParamLock(singlePaused);
        }
    }

    private void onListSquareToggled() {
        if (listSquareCheck.isSelected()) {
            listRadiusCheck.setSelected(false);
            applyListSquareSideToBounds();
        }
        applyListBoundFieldEnableState(!listRunning || listPaused);
        if (listRunning) {
            applyListSearchParamLock(listPaused);
        }
    }

    private void onListRadiusToggled() {
        if (listRadiusCheck.isSelected()) {
            listSquareCheck.setSelected(false);
        }
        applyListBoundFieldEnableState(!listRunning || listPaused);
        if (listRunning) {
            applyListSearchParamLock(listPaused);
        }
    }

    private void applySquareSideToBounds() {
        if (squareCheck == null || !squareCheck.isSelected()) {
            return;
        }
        try {
            long side = Long.parseLong(squareSideField.getText().trim());
            if (side < 1) {
                return;
            }
            int min = (int) -Math.floorDiv(side, 2);
            int max = (int) (min + side - 1);
            squareUpdating = true;
            minXField.setText(String.valueOf(min));
            maxXField.setText(String.valueOf(max));
            minZField.setText(String.valueOf(min));
            maxZField.setText(String.valueOf(max));
        } catch (NumberFormatException ignored) {
            // ignore while typing
        } finally {
            squareUpdating = false;
        }
    }

    private void applyListSquareSideToBounds() {
        if (listSquareCheck == null || !listSquareCheck.isSelected()) {
            return;
        }
        try {
            long side = Long.parseLong(listSquareSideField.getText().trim());
            if (side < 1) {
                return;
            }
            int min = (int) -Math.floorDiv(side, 2);
            int max = (int) (min + side - 1);
            listSquareUpdating = true;
            listMinXField.setText(String.valueOf(min));
            listMaxXField.setText(String.valueOf(max));
            listMinZField.setText(String.valueOf(min));
            listMaxZField.setText(String.valueOf(max));
        } catch (NumberFormatException ignored) {
        } finally {
            listSquareUpdating = false;
        }
    }

    private void applySearchBounds(int minX, int maxX, int minZ, int maxZ) {
        long side = (long) maxX - minX + 1;
        squareCheck.setSelected(true);
        radiusCheck.setSelected(false);
        squareUpdating = true;
        squareSideField.setText(String.valueOf(side));
        squareUpdating = false;
        minXField.setText(String.valueOf(minX));
        maxXField.setText(String.valueOf(maxX));
        minZField.setText(String.valueOf(minZ));
        maxZField.setText(String.valueOf(maxZ));
        applyBoundFieldEnableState(!singleRunning);
    }

    private void applyListSearchBounds(int minX, int maxX, int minZ, int maxZ) {
        long side = (long) maxX - minX + 1;
        listSquareCheck.setSelected(true);
        listRadiusCheck.setSelected(false);
        listSquareUpdating = true;
        listSquareSideField.setText(String.valueOf(side));
        listSquareUpdating = false;
        listMinXField.setText(String.valueOf(minX));
        listMaxXField.setText(String.valueOf(maxX));
        listMinZField.setText(String.valueOf(minZ));
        listMaxZField.setText(String.valueOf(maxZ));
        applyListBoundFieldEnableState(!listRunning);
    }

    private void resetSearchToWorldBorder() {
        squareCheck.setSelected(true);
        radiusCheck.setSelected(false);
        squareUpdating = true;
        squareSideField.setText(String.valueOf(WORLD_SIDE));
        squareUpdating = false;
        minXField.setText(String.valueOf(WORLD_MIN));
        maxXField.setText(String.valueOf(WORLD_MAX));
        minZField.setText(String.valueOf(WORLD_MIN));
        maxZField.setText(String.valueOf(WORLD_MAX));
        applyBoundFieldEnableState(!singleRunning);
    }

    private void applyBoundFieldEnableState(boolean allowEdit) {
        boolean squareMode = squareCheck != null && squareCheck.isSelected();
        boolean radiusMode = radiusCheck != null && radiusCheck.isSelected();
        if (squareCheck != null) {
            squareCheck.setEnabled(allowEdit);
        }
        if (squareSideField != null) {
            squareSideField.setEnabled(allowEdit && squareMode);
        }
        if (radiusCheck != null) {
            radiusCheck.setEnabled(allowEdit);
        }
        if (radiusField != null) {
            radiusField.setEnabled(allowEdit && radiusMode);
        }
        boolean boundsEditable = allowEdit && !squareMode && !radiusMode;
        if (minXField != null) {
            minXField.setEnabled(boundsEditable);
            maxXField.setEnabled(boundsEditable);
            minZField.setEnabled(boundsEditable);
            maxZField.setEnabled(boundsEditable);
        }
    }

    private void applyListBoundFieldEnableState(boolean allowEdit) {
        boolean squareMode = listSquareCheck != null && listSquareCheck.isSelected();
        boolean radiusMode = listRadiusCheck != null && listRadiusCheck.isSelected();
        if (listSquareCheck != null) {
            listSquareCheck.setEnabled(allowEdit);
        }
        if (listSquareSideField != null) {
            listSquareSideField.setEnabled(allowEdit && squareMode);
        }
        if (listRadiusCheck != null) {
            listRadiusCheck.setEnabled(allowEdit);
        }
        if (listRadiusField != null) {
            listRadiusField.setEnabled(allowEdit && radiusMode);
        }
        boolean boundsEditable = allowEdit && !squareMode && !radiusMode;
        if (listMinXField != null) {
            listMinXField.setEnabled(boundsEditable);
            listMaxXField.setEnabled(boundsEditable);
            listMinZField.setEnabled(boundsEditable);
            listMaxZField.setEnabled(boundsEditable);
        }
    }

    private void onSimDistanceChanged() {
        if (simDistanceUpdating || simDistanceField == null) {
            return;
        }
        String text = simDistanceField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.contains(".") || text.contains(",")) {
            return;
        }
        try {
            int chunks = Integer.parseInt(text);
            simDistanceUpdating = true;
            clusterRadiusField.setText(String.valueOf(chunks * 16));
            trialCircleCheck.setSelected(false);
        } catch (NumberFormatException ignored) {
        } finally {
            simDistanceUpdating = false;
        }
    }

    private void onListSimDistanceChanged() {
        if (listSimDistanceUpdating || listSimDistanceField == null) {
            return;
        }
        String text = listSimDistanceField.getText().trim();
        if (text.isEmpty() || text.contains(".") || text.contains(",")) {
            return;
        }
        try {
            int chunks = Integer.parseInt(text);
            listSimDistanceUpdating = true;
            listClusterRadiusField.setText(String.valueOf(chunks * 16));
            listTrialCircleCheck.setSelected(false);
        } catch (NumberFormatException ignored) {
        } finally {
            listSimDistanceUpdating = false;
        }
    }

    // ---------------- 参数锁定（暂停时仅线程数/折算比例可改） ----------------

    private void applySearchParamLock(boolean paused) {
        boolean idle = !singleRunning;
        boolean allowSearchEdit = idle;
        boolean allowNonSearch = idle || paused;

        seedField.setEnabled(allowSearchEdit);
        versionCombo.setEnabled(allowSearchEdit);
        applyBoundFieldEnableState(allowSearchEdit);
        if (resetBoundsButton != null) {
            resetBoundsButton.setEnabled(allowSearchEdit);
        }
        if (worldBorderButton != null) {
            worldBorderButton.setEnabled(allowSearchEdit);
        }
        clusterRadiusField.setEnabled(allowSearchEdit);
        simDistanceField.setEnabled(allowSearchEdit);
        trialCircleCheck.setEnabled(allowSearchEdit);
        minStructuresField.setEnabled(allowSearchEdit);
        minSpawnersField.setEnabled(allowSearchEdit);

        threadField.setEnabled(allowNonSearch);
        slowRatioField.setEnabled(allowNonSearch);
    }

    private void applyListSearchParamLock(boolean paused) {
        boolean idle = !listRunning;
        boolean allowSearchEdit = idle;
        boolean allowNonSearch = idle || paused;

        listSeedFileField.setEnabled(allowSearchEdit);
        listVersionCombo.setEnabled(allowSearchEdit);
        applyListBoundFieldEnableState(allowSearchEdit);
        if (listResetBoundsButton != null) {
            listResetBoundsButton.setEnabled(allowSearchEdit);
        }
        listClusterRadiusField.setEnabled(allowSearchEdit);
        listSimDistanceField.setEnabled(allowSearchEdit);
        listTrialCircleCheck.setEnabled(allowSearchEdit);
        listMinStructuresField.setEnabled(allowSearchEdit);
        listMinSpawnersField.setEnabled(allowSearchEdit);

        listThreadField.setEnabled(allowNonSearch);
        listSlowRatioField.setEnabled(allowNonSearch);
    }

    private void enableSingleControlsIdle() {
        singleRunning = false;
        singlePaused = false;
        startButton.setEnabled(true);
        pauseButton.setEnabled(false);
        pauseButton.setText("暂停");
        stopButton.setEnabled(false);
        applySearchParamLock(false);
    }

    private void enableListControlsIdle() {
        listRunning = false;
        listPaused = false;
        listStartButton.setEnabled(true);
        listPauseButton.setEnabled(false);
        listPauseButton.setText("暂停");
        listStopButton.setEnabled(false);
        applyListSearchParamLock(false);
    }

    // ---------------- 解析 / 校验 ----------------

    private TrialParams parseSingleParams() throws IllegalArgumentException {
        String seedText = seedField.getText().trim();
        if (seedText.isEmpty()) {
            throw new IllegalArgumentException("请填写种子，或改用「从种子列表搜索」");
        }
        long seed = Long.parseLong(seedText);
        return parseParams(seed, false);
    }

    private TrialParams parseParams(long seed, boolean listMode) {
        SearchBoundsInput bounds = listMode ? readListBounds() : readSingleBounds();
        int threads = Integer.parseInt((listMode ? listThreadField : threadField).getText().trim());
        if (threads < 1) {
            throw new IllegalArgumentException("线程数必须 >= 1");
        }
        JTextField simField = listMode ? listSimDistanceField : simDistanceField;
        String simText = simField.getText().trim();
        if (simText.contains(".") || simText.contains(",")) {
            throw new IllegalArgumentException("模拟距离区块数不得是小数");
        }
        Integer.parseInt(simText); // validate integer
        int cluster = Integer.parseInt((listMode ? listClusterRadiusField : clusterRadiusField).getText().trim());
        if (cluster <= 0) {
            throw new IllegalArgumentException("统计半径必须大于 0");
        }
        boolean trialCircle = (listMode ? listTrialCircleCheck : trialCircleCheck).isSelected();
        int minStructures = Integer.parseInt((listMode ? listMinStructuresField : minStructuresField).getText().trim());
        int minSpawners = Integer.parseInt((listMode ? listMinSpawnersField : minSpawnersField).getText().trim());
        double ratio = TrialSearchRunner.parseRatio((listMode ? listSlowRatioField : slowRatioField).getText());
        TrialSearchRunner.validateRatio(ratio);
        return new TrialParams(seed, bounds, threads, cluster, trialCircle, minStructures, minSpawners, ratio);
    }

    private SearchBoundsInput readSingleBounds() {
        boolean square = squareCheck.isSelected();
        boolean circle = radiusCheck.isSelected();
        int minX = Integer.parseInt(minXField.getText().trim());
        int maxX = Integer.parseInt(maxXField.getText().trim());
        int minZ = Integer.parseInt(minZField.getText().trim());
        int maxZ = Integer.parseInt(maxZField.getText().trim());
        int side = square ? Integer.parseInt(squareSideField.getText().trim()) : Math.max(1, maxX - minX + 1);
        int radius = circle ? Integer.parseInt(radiusField.getText().trim()) : 1;
        if (circle && radius <= 0) {
            throw new IllegalArgumentException("半径必须大于 0");
        }
        if (square && side <= 0) {
            throw new IllegalArgumentException("正方形边长必须大于 0");
        }
        return new SearchBoundsInput(square, circle, minX, maxX, minZ, maxZ, side, radius);
    }

    private SearchBoundsInput readListBounds() {
        boolean square = listSquareCheck.isSelected();
        boolean circle = listRadiusCheck.isSelected();
        int minX = Integer.parseInt(listMinXField.getText().trim());
        int maxX = Integer.parseInt(listMaxXField.getText().trim());
        int minZ = Integer.parseInt(listMinZField.getText().trim());
        int maxZ = Integer.parseInt(listMaxZField.getText().trim());
        int side = square ? Integer.parseInt(listSquareSideField.getText().trim()) : Math.max(1, maxX - minX + 1);
        int radius = circle ? Integer.parseInt(listRadiusField.getText().trim()) : 1;
        if (circle && radius <= 0) {
            throw new IllegalArgumentException("半径必须大于 0");
        }
        if (square && side <= 0) {
            throw new IllegalArgumentException("正方形边长必须大于 0");
        }
        return new SearchBoundsInput(square, circle, minX, maxX, minZ, maxZ, side, radius);
    }

    // ---------------- 单种子搜索 ----------------

    private void startSingleSearch() {
        if (!searchBusy.compareAndSet(false, true)) {
            JOptionPane.showMessageDialog(this, "已有搜索在运行", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final TrialParams params;
        try {
            params = parseSingleParams();
        } catch (Exception ex) {
            searchBusy.set(false);
            JOptionPane.showMessageDialog(this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        resultArea.setText("");
        singleExportParams = null;
        singleExportResults = List.of();
        exportButton.setEnabled(false);
        singleRunning = true;
        singlePaused = false;
        startButton.setEnabled(false);
        pauseButton.setEnabled(true);
        pauseButton.setText("暂停");
        stopButton.setEnabled(true);
        applySearchParamLock(false);
        progressBar.setValue(0);
        progressBar.setString("启动中…");
        statusLabel.setText(" ");

        Path workDir = TrialSearchRunner.resolveWorkDirectory();
        Thread worker = new Thread(() -> {
            Outcome outcome = runner.runSearch(
                    params,
                    workDir,
                    update -> SwingUtilities.invokeLater(() -> updateSingleProgress(update)),
                    detail -> SwingUtilities.invokeLater(() -> statusLabel.setText(detail)));
            List<SearchResult> results = runner.takeCompletedResults();
            finishSingleSearchAfterRun(outcome, params, results);
        }, "trial-single-search");
        worker.setDaemon(true);
        worker.start();
    }

    /** 工作线程：完成后先提示「整理结果中」，再排序/统计，最后回 EDT 刷新界面。 */
    private void finishSingleSearchAfterRun(
            Outcome outcome, TrialParams params, List<SearchResult> results) {
        if (outcome == Outcome.COMPLETED) {
            showOrganizingProgress(progressBar, statusLabel);
            List<FormattedHit> hits = runner.formatResults(params, results);
            SwingUtilities.invokeLater(() -> applySingleSearchCompleted(params, results, hits));
            return;
        }
        SwingUtilities.invokeLater(() -> finishSingleSearch(outcome, params, results));
    }

    private void applySingleSearchCompleted(
            TrialParams params, List<SearchResult> results, List<FormattedHit> hits) {
        searchBusy.set(false);
        StringBuilder sb = new StringBuilder();
        for (FormattedHit hit : hits) {
            sb.append(hit.toLine()).append('\n');
        }
        if (sb.isEmpty()) {
            sb.append("（无达标结果）\n");
        }
        resultArea.setText(sb.toString());
        singleExportParams = params;
        singleExportResults = results == null ? List.of() : List.copyOf(results);
        exportButton.setEnabled(true);
        progressBar.setValue(progressBar.getMaximum());
        progressBar.setString("完成");
        statusLabel.setText("搜索完成，显示前 " + hits.size() + " 条（可导出）");
        enableSingleControlsIdle();
    }

    private void finishSingleSearch(Outcome outcome, TrialParams params, List<SearchResult> results) {
        searchBusy.set(false);
        if (outcome == Outcome.PAUSED) {
            singlePaused = true;
            singleRunning = true;
            pauseButton.setEnabled(true);
            pauseButton.setText("继续");
            stopButton.setEnabled(true);
            startButton.setEnabled(false);
            applySearchParamLock(true);
            progressBar.setString("已暂停");
            statusLabel.setText("已暂停，进度已写入 checkpoint");
            searchBusy.set(false);
        } else if (outcome == Outcome.CANCELLED) {
            runner.discardActiveCheckpoint();
            progressBar.setString("已停止");
            statusLabel.setText("已停止，进度已清除");
            enableSingleControlsIdle();
        } else {
            progressBar.setString("失败");
            enableSingleControlsIdle();
        }
    }

    private static void showOrganizingProgress(JProgressBar bar, JLabel status) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                bar.setValue(bar.getMaximum());
                bar.setString("整理结果中");
                if (status != null) {
                    status.setText("整理结果中…");
                }
                bar.paintImmediately(0, 0, bar.getWidth(), bar.getHeight());
            });
        } catch (Exception ignored) {
            // 中断或调度失败时仍继续整理
        }
    }

    private void toggleSinglePause() {
        if (!singleRunning) {
            return;
        }
        if (singlePaused) {
            // 继续：用当前可改参数重新进入搜索（checkpoint 续扫）
            final TrialParams params;
            try {
                params = parseSingleParams();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!searchBusy.compareAndSet(false, true)) {
                return;
            }
            singlePaused = false;
            pauseButton.setText("暂停");
            applySearchParamLock(false);
            progressBar.setString("继续中…");
            Path workDir = TrialSearchRunner.resolveWorkDirectory();
            Thread worker = new Thread(() -> {
                Outcome outcome = runner.runSearch(
                        params,
                        workDir,
                        update -> SwingUtilities.invokeLater(() -> updateSingleProgress(update)),
                        detail -> SwingUtilities.invokeLater(() -> statusLabel.setText(detail)));
                List<SearchResult> results = runner.takeCompletedResults();
                finishSingleSearchAfterRun(outcome, params, results);
            }, "trial-single-resume");
            worker.setDaemon(true);
            worker.start();
        } else {
            runner.pause();
            pauseButton.setText("暂停中…");
            pauseButton.setEnabled(false);
        }
    }

    private void stopSingleSearch() {
        if (!singleRunning && !singlePaused) {
            return;
        }
        if (singlePaused && !searchBusy.get()) {
            runner.discardActiveCheckpoint();
            progressBar.setString("已停止");
            statusLabel.setText("已停止，进度已清除");
            enableSingleControlsIdle();
            return;
        }
        runner.requestStop();
    }

    private void updateSingleProgress(ProgressUpdate update) {
        // 只吃「总进度」，忽略粗筛等子阶段，避免进度条来回跳
        if (!"总进度".equals(update.phase())) {
            return;
        }
        if (update.total() > 0) {
            int value = (int) Math.min(1000, 1000L * update.completed() / update.total());
            progressBar.setValue(value);
        }
        progressBar.setString(TrialSearchRunner.formatProgress(update));
    }

    // ---------------- 列表搜索 ----------------

    private void selectSeedFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("文本文件 (*.txt)", "txt"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            listSeedFileField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private List<Long> readSeedList(File file) throws IOException {
        List<Long> seeds = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                try {
                    seeds.add(Long.parseLong(line));
                } catch (NumberFormatException ex) {
                    System.err.println("跳过非法种子行: " + line);
                }
            }
        }
        return seeds;
    }

    private void startListSearch() {
        if (!searchBusy.compareAndSet(false, true)) {
            JOptionPane.showMessageDialog(this, "已有搜索在运行", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String pathText = listSeedFileField.getText().trim();
        if (pathText.isEmpty()) {
            searchBusy.set(false);
            JOptionPane.showMessageDialog(this, "请先导入种子文件", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        final List<Long> seeds;
        try {
            seeds = readSeedList(new File(pathText));
            if (seeds.isEmpty()) {
                throw new IllegalArgumentException("种子文件为空");
            }
            // 预校验参数
            parseParams(seeds.get(0), true);
        } catch (Exception ex) {
            searchBusy.set(false);
            JOptionPane.showMessageDialog(this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        listResultArea.setText("");
        listExportBuffer.clear();
        listExportButton.setEnabled(false);
        listStopRequested.set(false);
        listRunning = true;
        listPaused = false;
        listStartButton.setEnabled(false);
        listPauseButton.setEnabled(true);
        listPauseButton.setText("暂停");
        listStopButton.setEnabled(true);
        applyListSearchParamLock(false);

        Path workDir = TrialSearchRunner.resolveWorkDirectory();
        Thread worker = new Thread(() -> {
            StringBuilder all = new StringBuilder();
            int index = 0;
            for (Long seed : seeds) {
                if (listStopRequested.get()) {
                    break;
                }
                final int seedIndex = ++index;
                SwingUtilities.invokeLater(() -> listStatusLabel.setText(
                        "种子 %d/%d: %d".formatted(seedIndex, seeds.size(), seed)));
                TrialParams params;
                try {
                    params = parseParams(seed, true);
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE));
                    break;
                }
                Outcome outcome;
                do {
                    outcome = runner.runSearch(
                            params,
                            workDir,
                            update -> SwingUtilities.invokeLater(() -> updateListProgress(update)),
                            detail -> SwingUtilities.invokeLater(() -> listStatusLabel.setText(
                                    "种子 %d/%d: %s".formatted(seedIndex, seeds.size(), detail))));
                    if (outcome == Outcome.PAUSED) {
                        listPaused = true;
                        SwingUtilities.invokeLater(() -> {
                            listPauseButton.setText("继续");
                            listPauseButton.setEnabled(true);
                            applyListSearchParamLock(true);
                            listProgressBar.setString("已暂停");
                        });
                        // 等待继续或停止
                        while (listPaused && !listStopRequested.get()) {
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                listStopRequested.set(true);
                                break;
                            }
                        }
                        if (listStopRequested.get()) {
                            runner.requestStop();
                            runner.discardActiveCheckpoint();
                            outcome = Outcome.CANCELLED;
                            break;
                        }
                        try {
                            params = parseParams(seed, true);
                        } catch (Exception ex) {
                            SwingUtilities.invokeLater(() ->
                                    JOptionPane.showMessageDialog(this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE));
                            outcome = Outcome.FAILED;
                            break;
                        }
                        SwingUtilities.invokeLater(() -> {
                            listPaused = false;
                            listPauseButton.setText("暂停");
                            applyListSearchParamLock(false);
                        });
                    }
                } while (outcome == Outcome.PAUSED);

                if (outcome == Outcome.COMPLETED) {
                    List<SearchResult> results = runner.takeCompletedResults();
                    List<SearchResult> resultsCopy = List.copyOf(results);
                    TrialParams completedParams = params;
                    showOrganizingProgress(listProgressBar, listStatusLabel);
                    List<FormattedHit> hits = runner.formatResults(params, results);
                    all.append("种子 ").append(seed).append('\n');
                    for (FormattedHit hit : hits) {
                        all.append(hit.toLine()).append('\n');
                    }
                    all.append('\n');
                    String snapshot = all.toString();
                    SwingUtilities.invokeLater(() -> {
                        listExportBuffer.add(new PendingExport(completedParams, resultsCopy));
                        listResultArea.setText(snapshot);
                        listExportButton.setEnabled(true);
                    });
                } else if (outcome == Outcome.CANCELLED || listStopRequested.get()) {
                    runner.discardActiveCheckpoint();
                    break;
                }
            }
            SwingUtilities.invokeLater(() -> {
                searchBusy.set(false);
                if (listStopRequested.get()) {
                    runner.discardActiveCheckpoint();
                    listProgressBar.setString("已停止");
                    listStatusLabel.setText("已停止，进度已清除");
                } else {
                    listProgressBar.setValue(listProgressBar.getMaximum());
                    listProgressBar.setString("完成");
                    listStatusLabel.setText("列表搜索完成");
                }
                enableListControlsIdle();
            });
        }, "trial-list-search");
        worker.setDaemon(true);
        worker.start();
    }

    private void toggleListPause() {
        if (!listRunning) {
            return;
        }
        if (listPaused) {
            listPaused = false;
            listPauseButton.setText("暂停");
            applyListSearchParamLock(false);
        } else {
            runner.pause();
            listPauseButton.setText("暂停中…");
            listPauseButton.setEnabled(false);
        }
    }

    private void stopListSearch() {
        listStopRequested.set(true);
        listPaused = false;
        runner.requestStop();
    }

    private void updateListProgress(ProgressUpdate update) {
        if (!"总进度".equals(update.phase())) {
            return;
        }
        if (update.total() > 0) {
            int value = (int) Math.min(1000, 1000L * update.completed() / update.total());
            listProgressBar.setValue(value);
        }
        listProgressBar.setString(TrialSearchRunner.formatProgress(update));
    }

    private void exportSingleResults() {
        if (singleExportParams == null) {
            JOptionPane.showMessageDialog(this, "没有可导出的搜索结果", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Path suggested = TrialSearchRunner.resolveWorkDirectory()
                .resolve("results-trial-spawner.csv");
        JFileChooser chooser = new JFileChooser(suggested.getParent().toFile());
        chooser.setSelectedFile(suggested.toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("CSV 结果 (*.csv)", "csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = chooser.getSelectedFile();
        Path path = chosen.toPath();
        String fileName = path.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            path = path.resolveSibling(fileName + ".csv");
        }
        try {
            Path written = runner.exportResults(singleExportParams, singleExportResults, path);
            Path textPath = ResultFiles.textPath(written);
            statusLabel.setText("导出完成！");
            JOptionPane.showMessageDialog(this,
                    "已导出：\n" + written.toAbsolutePath() + "\n" + textPath.toAbsolutePath(),
                    "导出完成", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "导出失败：" + ex.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportListResults() {
        if (listExportBuffer.isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有可导出的搜索结果", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser(
                TrialSearchRunner.resolveWorkDirectory().toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择导出目录");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path directory = chooser.getSelectedFile().toPath();
        try {
            List<Path> written = new ArrayList<>();
            for (PendingExport pending : listExportBuffer) {
                Path target = directory.resolve(
                        "results-trial-spawner-seed-" + pending.params().seed() + ".csv");
                written.add(runner.exportResults(pending.params(), pending.results(), target));
            }
            listStatusLabel.setText("已导出 " + written.size() + " 个结果文件");
            StringBuilder message = new StringBuilder("已导出 ")
                    .append(written.size()).append(" 个文件到：\n")
                    .append(directory.toAbsolutePath());
            JOptionPane.showMessageDialog(this, message.toString(),
                    "导出完成", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "导出失败：" + ex.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            installAppFont();
            new TrialSpawnerFinderFrame().setVisible(true);
        });
    }
}
