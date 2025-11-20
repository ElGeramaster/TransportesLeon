package GestionSoftware;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import javax.swing.RowFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE;
import javax.swing.table.TableCellEditor;

public class Registros extends JFrame {

    // ==========================
    // Columnas (en mismo orden que la tabla y el modelo)
    // ==========================
    private static final String[] COLUMNAS = {
            "CARTA PORTE", "CLIENTE", "FACTURA", "FECHA FACTURA", "VALOR", "FECHA DE PAGO",
            "DESTINO", "REFERENCIA", "REMITENTE", "CONSIGNATORIO", "FACTURA",
            "OPERADOR", "PLACA CABEZAL", "PLACA DEL FURGON", "VALOR FLETE", "ANTICIPO",
            "A.CANCELACION", "FECHA DE PAGADO", "F. DE CARGA", "F. DE CRUCE", "F. SAL. T.U.",
            "F.F. DESTINO", "F. EN. DESTINO", "F. DESCARGA", "F.E. DE DOCTOS.", "CUSTODIO",
            "PAGADO", "OBSERVACIONES"};
    
// Índices útiles
    private static final int IDX_ID              = 0;
    private static final int IDX_CLIENTE         = 1;
    private static final int IDX_FECHA_FACTURA   = 3;
    private static final int IDX_VALOR           = 4;
    private static final int IDX_DESTINO         = 6;
    private static final int IDX_REMITENTE       = 8;
    private static final int IDX_CONSIGNATARIO   = 9;
    private static final int IDX_OPERADOR        = 11;
    private static final int IDX_PLACA_CABEZAL   = 12;
    private static final int IDX_PLACA_FURGON    = 13;
    private static final int IDX_VALOR_FLETE     = 14;
    private static final int IDX_ANTICIPO        = 15;
    private static final int IDX_A_CANCELACION   = 16;

    // Tabla
    public static DefaultTableModel modeloTabla;
    private JTable tabla;
    private JScrollPane scrollPane;

    // Buscador
    private JTextField txtBuscar;
    private JComboBox<String> cbFiltro; // opcional
    private TableRowSorter<DefaultTableModel> sorter;

    // Selector de visibilidad de columnas
    private JComboBox<String> cbMostrarColumnas;
    private final Map<String, int[]> FiltrarColumnas = new LinkedHashMap<>();
    private static final String CREAR_FILTRO_LABEL = "➕ Crear filtro…";
    private static final String ELIMINAR_FILTRO_LABEL = "🗑 Eliminar filtro actual…"; // NUEVO
    private String ultimaVistaSeleccionada = "Todos";
    private TableColumn[] columnasOriginales;

    // LOGO Y IMAGENES
    private JLabel logoLabel;
    private static final String LOGOImagen = "/GestionSoftware/imagenes/LogoLeon.PNG";
    private static final String CambiosIcono = "/GestionSoftware/imagenes/Cambios.PNG";

    // Locale ES-MX
    private static final java.util.Locale LOCALE_ES_MX = new java.util.Locale("es","MX");

    private static boolean esColumnaFecha(String col) {
        String c = col.toUpperCase();
        return c.startsWith("FECHA") || c.startsWith("F_");
    }

    // Formato de fecha "28 de Octubre del 2025"
    private static String formatearFechaBonita(java.util.Date d) {
        if (d == null) return "";
        java.text.DateFormatSymbols dfs = new java.text.DateFormatSymbols(LOCALE_ES_MX);
        String[] months = dfs.getMonths();
        for (int i = 0; i < months.length; i++) {
            if (months[i] != null && !months[i].isEmpty()) {
                months[i] = months[i].substring(0,1).toUpperCase(LOCALE_ES_MX) + months[i].substring(1);
            }
        }
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("d 'de' MMMM 'del' yyyy", dfs);
        return fmt.format(d);
    }

    // Normaliza textos de fecha a ISO (yyyy-MM-dd) si es posible
    private static String normalizeFecha(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.isEmpty()) return "";
        final String[] patrones = {
                "yyyy-MM-dd",
                "yyyy-MM-dd HH:mm",
                "dd/MM/yyyy",
                "d 'de' MMMM 'del' yyyy"
        };
        for (String p : patrones) {
            try {
                java.text.SimpleDateFormat in = new java.text.SimpleDateFormat(p, LOCALE_ES_MX);
                in.setLenient(false);
                java.util.Date d = in.parse(t);
                java.text.SimpleDateFormat out = new java.text.SimpleDateFormat("yyyy-MM-dd");
                return out.format(d);
            } catch (Exception ignore) {}
        }
        return t; // si no se pudo parsear, regresa tal cual
    }

    private static String formatearMoneda(String s) {
        if (s == null || s.trim().isEmpty()) return "";
        try {
            java.math.BigDecimal bd = new java.math.BigDecimal(s.replace("$","").replace(",","").trim());
            java.text.NumberFormat nf = java.text.NumberFormat.getCurrencyInstance(LOCALE_ES_MX);
            return nf.format(bd);
        } catch (Exception ex) { return s; }
    }

    // ==========================
    // Estado y botones para inserción
    // ==========================
    private JButton btnGuardarFila;
    private JButton btnCancelarFila;
    private Integer filaEnInsercion = null;

    // Botones principales (para ocultar/mostrar)
    private JButton btnAgregar;
    private JButton btnModificar;
    private JButton btnEliminar;
    private JButton btnModificaciones;

    // ==========================
    // Sugerencias (autocompletado)
    // ==========================
    /** Map columna -> conjunto de sugerencias (case-insensitive, sin duplicados, orden estable). */
    private final Map<Integer, LinkedHashSet<String>> sugerencias = new HashMap<>();
    /** Map columna -> editor con popup de sugerencias */
    private final Map<Integer, TableCellEditor> editorsAuto = new HashMap<>();
    /** Columnas a las que se les aplica autocompletado */
    private final int[] COLS_AUTOCOMPLETE = {
            IDX_CLIENTE, IDX_OPERADOR, IDX_DESTINO, IDX_REMITENTE, IDX_CONSIGNATARIO,
            IDX_PLACA_CABEZAL, IDX_PLACA_FURGON
    };

    // Helpers
    private BigDecimal parseDecimal(String s) {
        if (s == null) return null;
        String x = s.trim().replace("$","").replace("Q","").replace(",","");
        if (x.isEmpty()) return null;
        try { return new BigDecimal(x); } catch (NumberFormatException ex) { return null; }
    }
    private String asStr(Object o) { return (o == null) ? "" : String.valueOf(o).trim(); }

    public Registros () {
        setTitle("Registros");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1400, 800);
        getContentPane().setLayout(null);
        setLocationRelativeTo(null);
        getContentPane().setBackground(new Color(211,211,211));
        setResizable(false);

        // Título
        JLabel T1 = new JLabel("ARCHIVO GENERAL DE VIAJES", SwingConstants.CENTER);
        T1.setFont(new Font("ethnocentric", Font.BOLD, 30));
        T1.setBounds(380, 20, 660, 50);
        getContentPane().add(T1);

        // Botón de regreso
        JButton B1 = new JButton("<");
        B1.setBackground(Color.WHITE);
        B1.setBounds(10, 10, 50, 50);
        B1.setFocusPainted(false);
        getContentPane().add(B1);

        B1.addActionListener(e -> {
            try {
                Ingresar menu = new Ingresar();
                menu.setVisible(true);
                dispose();
            } catch (Throwable ex) {
                dispose();
            }
        });
        B1.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent evt) { B1.setBackground(Color.RED); }
            @Override public void mouseExited (MouseEvent evt) { B1.setBackground(Color.WHITE); }
        });
        // ESC -> back
        JRootPane raizEsc = getRootPane();
        KeyStroke TeclaSalir = KeyStroke.getKeyStroke("ESCAPE");
        raizEsc.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(TeclaSalir, "ESCAPE");
        raizEsc.getActionMap().put("ESCAPE", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { B1.doClick(); }
        });

        // MODELO
        modeloTabla = new DefaultTableModel() {
            @Override public boolean isCellEditable(int row, int column) {
                return (filaEnInsercion != null && row == filaEnInsercion);
            }
            @Override public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == IDX_ID) return Integer.class;
                if (columnIndex == IDX_VALOR || columnIndex == IDX_VALOR_FLETE
                        || columnIndex == IDX_ANTICIPO || columnIndex == IDX_A_CANCELACION) return BigDecimal.class;
                return String.class;
            }
        };
        for (String c : COLUMNAS) modeloTabla.addColumn(c);

        // TABLA
        tabla = new JTable(modeloTabla);
        tabla.setRowHeight(24);
        tabla.setFont(new Font("Arial", Font.BOLD, 12));
        tabla.setBackground(Color.WHITE);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tabla.putClientProperty("terminateEditOnFocusLost", Boolean.FALSE);

        JTableHeader header = tabla.getTableHeader();
        header.setFont(new Font("Poppins", Font.BOLD, 14));
        header.setBackground(new Color(135, 206, 235));
        header.setReorderingAllowed(true);
        header.setResizingAllowed(true);

        sorter = new TableRowSorter<>(modeloTabla);
        tabla.setRowSorter(sorter);

        scrollPane = new JScrollPane(tabla);
        scrollPane.setBounds(10, 120, 1340, 470);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        getContentPane().add(scrollPane);

        tabla.addMouseWheelListener(e -> {
            if (e.isShiftDown()) {
                JScrollBar h = scrollPane.getHorizontalScrollBar();
                int amount = e.getUnitsToScroll() * h.getUnitIncrement();
                h.setValue(h.getValue() + amount);
                e.consume();
            }
        });

        configurarAnchosColumnas();
        aplicarRendererNumericos();
        aplicarColoresColumnasTexto();   // <<< COLORES POR COLUMNA
        crearBarraBusqueda();
        crearSelectorMostrarColumnas();

        // Inicializa estructuras de sugerencias para columnas target
        initSugerencias();
        // Editor de calendario para columnas fecha
        aplicarEditoresFecha();
        // Asigna editores de autocompletado a columnas target
        AUTOCOMPLETADO();

        // Botones principales
        btnAgregar = new JButton("Agregar");
        btnAgregar.setFont(new Font("Poppins", Font.BOLD,16));
        btnAgregar.setBounds(300, 620, 150, 50);
        btnAgregar.setBackground(new Color(163, 231, 214));
        btnAgregar.setForeground(Color.BLACK);
        btnAgregar.setFocusPainted(false);
        getContentPane().add(btnAgregar);

        btnModificar = new JButton("Modificar");
        btnModificar.setFont(new Font("Poppins", Font.BOLD,16));
        btnModificar.setBounds(600, 620, 150, 50);
        btnModificar.setBackground(new Color(218, 194, 254));
        btnModificar.setForeground(Color.BLACK);
        btnModificar.setFocusPainted(false);
        getContentPane().add(btnModificar);

        btnEliminar = new JButton("Eliminar");
        btnEliminar.setFont(new Font("Poppins", Font.BOLD,16));
        btnEliminar.setBounds(900, 620, 150, 50);
        btnEliminar.setBackground(new Color(229, 115, 115));
        btnEliminar.setForeground(Color.BLACK);
        btnEliminar.setFocusPainted(false);
        getContentPane().add(btnEliminar);

        // --- Botón Modificaciones con icono PNG ---
        ImageIcon iconCambios      = scaledIcon(CambiosIcono, 44, 44);
        ImageIcon iconCambiosOver  = scaledIcon(CambiosIcono, 48, 48);
        ImageIcon iconCambiosPress = scaledIcon(CambiosIcono, 42, 42);

        // Si no se encuentra el PNG, cae a botón con texto
        if (iconCambios == null) {
            btnModificaciones = new JButton("Cambios");
            btnModificaciones.setFont(new Font("Poppins", Font.BOLD,16));
            btnModificaciones.setBackground(new Color(229, 115, 115));
            btnModificaciones.setForeground(Color.BLACK);
        } else {
            btnModificaciones = new JButton(iconCambios);
            if (iconCambiosOver  != null) btnModificaciones.setRolloverIcon(iconCambiosOver);
            if (iconCambiosPress != null) btnModificaciones.setPressedIcon(iconCambiosPress);

            // Estilo “icon-only”
            btnModificaciones.setBorder(BorderFactory.createEmptyBorder());
            btnModificaciones.setContentAreaFilled(false);
            btnModificaciones.setFocusPainted(false);
            btnModificaciones.setOpaque(false);
            btnModificaciones.setToolTipText("Ver modificaciones anteriores");
            btnModificaciones.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        // Mantén la misma posición; ajusta tamaño si deseas que sea ‘cuadrito’
        btnModificaciones.setBounds(1200, 620, (iconCambios != null ? 56 : 150), (iconCambios != null ? 56 : 50));
        getContentPane().add(btnModificaciones);

        // Acción: abrir la ventana de historial
        btnModificaciones.addActionListener(e -> {
            new Modificaciones_Anteriores().setVisible(true);
            dispose();
        });

        // Botones de inserción
        btnGuardarFila = new JButton("Guardar fila");
        btnGuardarFila.setFont(new Font("Poppins", Font.BOLD, 16));
        btnGuardarFila.setBounds(470, 620, 150, 50);
        btnGuardarFila.setBackground(new Color(120, 200, 120));
        btnGuardarFila.setVisible(false);
        getContentPane().add(btnGuardarFila);

        btnCancelarFila = new JButton("Cancelar");
        btnCancelarFila.setFont(new Font("Poppins", Font.BOLD, 16));
        btnCancelarFila.setBounds(770, 620, 150, 50);
        btnCancelarFila.setBackground(new Color(240, 200, 120));
        btnCancelarFila.setVisible(false);
        getContentPane().add(btnCancelarFila);

        // Eventos
        btnAgregar.addActionListener(e -> empezarInsercion());

        btnModificar.addActionListener(e -> {
            int filaSeleccionada = tabla.getSelectedRow();
            if (filaSeleccionada == -1) {
                JOptionPane.showMessageDialog(this, "Selecciona un registro para modificar.");
                return;
            }
            if (filaEnInsercion != null) {
                JOptionPane.showMessageDialog(this, "Primero guarda o cancela la fila nueva.");
                return;
            }
            int modelRow = tabla.convertRowIndexToModel(filaSeleccionada);
            String cartaPorteId = String.valueOf(modeloTabla.getValueAt(modelRow, IDX_ID));

            Object[] rowData = new Object[modeloTabla.getColumnCount()];
            for (int i = 0; i < rowData.length; i++) {
                Object v = modeloTabla.getValueAt(modelRow, i);
                rowData[i] = (v == null) ? "" : v.toString();
            }

            EditarRegistroDialog dlg = new EditarRegistroDialog(this, cartaPorteId, modelRow, rowData);
            dlg.setVisible(true);
        });

        btnEliminar.addActionListener(e -> {
            int filaSeleccionada = tabla.getSelectedRow();
            if (filaSeleccionada == -1) {
                JOptionPane.showMessageDialog(this, "Selecciona un registro para eliminar.");
                return;
            }
            if (filaEnInsercion != null) {
                JOptionPane.showMessageDialog(this, "Primero guarda o cancela la fila nueva.");
                return;
            }
            int modelRow = tabla.convertRowIndexToModel(filaSeleccionada);
            String nCaso = modeloTabla.getValueAt(modelRow, IDX_ID).toString();
            eliminarRegistro(nCaso);
        });

        btnGuardarFila.addActionListener(e -> guardarNuevaFila());
        btnCancelarFila.addActionListener(e -> cancelarInsercion());

        // Logo
        logoLabel = new JLabel();
        logoLabel.setOpaque(false);
        getContentPane().add(logoLabel);
        setLogoBounds(90, 30, 100, 90);

        ImageIcon appIcon = scaledIcon(LOGOImagen, 32, 32);
        if (appIcon != null) setIconImage(appIcon.getImage());

        snapshotColumnasOriginales();
        cargarDatos();          // carga datos y llena sugerencias de BD
        setModoInsercion(false);
    }

    // -------- Sugerencias: setup y helpers --------
    private void initSugerencias() {
        for (int col : COLS_AUTOCOMPLETE) {
            sugerencias.put(col, new LinkedHashSet<>());
        }
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        n = n.replaceAll("\\p{M}+","").toLowerCase(java.util.Locale.ROOT).trim();
        return n;
    }

    private void RegistrosGuardados(int col, String value) {
        LinkedHashSet<String> set = sugerencias.get(col);
        if (set == null) return;
        if (value == null) return;
        String v = value.trim();
        if (v.isEmpty()) return;

        String nv = normalize(v);
        boolean existe = false;
        for (String s : set) {
            if (normalize(s).equals(nv)) { existe = true; break; }
        }
        if (!existe) set.add(v);
    }

    private void addSuggestionsFromRow(Object[] row) {
        if (row == null) return;
        RegistrosGuardados(IDX_CLIENTE,       asStr(row[IDX_CLIENTE]));
        RegistrosGuardados(IDX_OPERADOR,      asStr(row[IDX_OPERADOR]));
        RegistrosGuardados(IDX_DESTINO,       asStr(row[IDX_DESTINO]));
        RegistrosGuardados(IDX_REMITENTE,     asStr(row[IDX_REMITENTE]));
        RegistrosGuardados(IDX_CONSIGNATARIO, asStr(row[IDX_CONSIGNATARIO]));
        RegistrosGuardados(IDX_PLACA_CABEZAL, asStr(row[IDX_PLACA_CABEZAL]));
        RegistrosGuardados(IDX_PLACA_FURGON,  asStr(row[IDX_PLACA_FURGON]));
    }

    private void AUTOCOMPLETADO() {
        TableColumnModel tcm = tabla.getColumnModel();
        for (int col : COLS_AUTOCOMPLETE) {
            int viewIdx = tabla.convertColumnIndexToView(col);
            if (viewIdx >= 0 && viewIdx < tcm.getColumnCount()) {
                SUGERENCIAS ed = new SUGERENCIAS(sugerencias.get(col));
                editorsAuto.put(col, ed);
                tcm.getColumn(viewIdx).setCellEditor(ed);
            }
        }
    }

    // Modo inserción (mostrar/ocultar)
    private void setModoInsercion(boolean enInsercion) {
        btnAgregar.setVisible(!enInsercion);
        btnModificar.setVisible(!enInsercion);
        btnEliminar.setVisible(!enInsercion);
        btnGuardarFila.setVisible(enInsercion);
        btnCancelarFila.setVisible(enInsercion);
    }

    // Renderer numérico con moneda
    private void aplicarRendererNumericos() {
        javax.swing.table.DefaultTableCellRenderer right = new javax.swing.table.DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                if (value == null) { super.setValue(""); return; }
                String s = value.toString();
                try {
                    new java.math.BigDecimal(s.replace("$","").replace(",","").trim());
                    super.setValue(formatearMoneda(s));
                } catch (Exception e) {
                    super.setValue(s);
                }
            }
        };
        right.setHorizontalAlignment(SwingConstants.RIGHT);

        int[] numCols = {IDX_VALOR, IDX_VALOR_FLETE, IDX_ANTICIPO, IDX_A_CANCELACION};
        TableColumnModel tcm = tabla.getColumnModel();
        for (int modelIdx : numCols) {
            int viewIdx = tabla.convertColumnIndexToView(modelIdx);
            if (viewIdx >= 0 && viewIdx < tcm.getColumnCount()) {
                tcm.getColumn(viewIdx).setCellRenderer(right);
            }
        }
    }

    // === Colores por columna (Carta_Porte_id y Cliente) ===
    private void aplicarColoresColumnasTexto() {
        if (tabla == null) return;
        TableColumnModel tcm = tabla.getColumnModel();

        // --- Carta_Porte_id en rojo ---
        int viewIdxCarta = tabla.convertColumnIndexToView(IDX_ID);
        if (viewIdxCarta >= 0 && viewIdxCarta < tcm.getColumnCount()) {
            tcm.getColumn(viewIdxCarta).setCellRenderer(
                new javax.swing.table.DefaultTableCellRenderer() {
                    @Override
                    public Component getTableCellRendererComponent(
                            JTable table, Object value,
                            boolean isSelected, boolean hasFocus,
                            int row, int column) {

                        Component c = super.getTableCellRendererComponent(
                                table, value, isSelected, hasFocus, row, column);

                        if (!isSelected) {
                            c.setForeground(Color.RED); // texto rojo
                        } else {
                            c.setForeground(table.getSelectionForeground());
                        }
                        return c;
                    }
                }
            );
        }

        // --- Cliente en azul marino oscuro ---
        int viewIdxCliente = tabla.convertColumnIndexToView(IDX_CLIENTE);
        if (viewIdxCliente >= 0 && viewIdxCliente < tcm.getColumnCount()) {
            tcm.getColumn(viewIdxCliente).setCellRenderer(
                new javax.swing.table.DefaultTableCellRenderer() {
                    @Override
                    public Component getTableCellRendererComponent(
                            JTable table, Object value,
                            boolean isSelected, boolean hasFocus,
                            int row, int column) {

                        Component c = super.getTableCellRendererComponent(
                                table, value, isSelected, hasFocus, row, column);

                        if (!isSelected) {
                            c.setForeground(new Color(0, 0, 128)); // azul marino oscuro
                        } else {
                            c.setForeground(table.getSelectionForeground());
                        }
                        return c;
                    }
                }
            );
        }
    }

    // Buscador
    private void crearBarraBusqueda() {
        JLabel lblBuscar = new JLabel("Buscar:");
        lblBuscar.setFont(new Font("Poppins", Font.BOLD, 14));
        lblBuscar.setBounds(970, 85, 60, 20);
        getContentPane().add(lblBuscar);

        txtBuscar = new JTextField();
        txtBuscar.setToolTipText("Escribe para filtrar filas… (ENTER aplica, ESC limpia)");
        txtBuscar.setBounds(1030, 80, 200, 30);
        getContentPane().add(txtBuscar);

        txtBuscar.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { aplicarFiltro(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { aplicarFiltro(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { aplicarFiltro(); }
        });

        txtBuscar.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) aplicarFiltro();
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) { txtBuscar.setText(""); aplicarFiltro(); }
            }
        });
    }

    private void aplicarFiltro() {
        if (sorter == null) return;

        final String query = (txtBuscar.getText() == null) ? "" : txtBuscar.getText().trim();
        if (query.isEmpty()) {
            sorter.setRowFilter(null);
            return;
        }

        final int[] columnas = columnasSegunFiltro();
        final String[] tokens = Arrays.stream(query.split("\\s+")).filter(s -> !s.isEmpty()).toArray(String[]::new);

        RowFilter<DefaultTableModel, Object> rf = new RowFilter<DefaultTableModel, Object>() {
            private String norm(String s) {
                if (s == null) return "";
                String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
                return n.replaceAll("\\p{M}+","").toLowerCase(java.util.Locale.ROOT);
            }
            private boolean containsNorm(String haystack, String needle) {
                return norm(haystack).contains(norm(needle));
            }
            @Override public boolean include(Entry<? extends DefaultTableModel, ? extends Object> entry) {
                int[] cols = (columnas == null || columnas.length == 0)
                        ? java.util.stream.IntStream.range(0, modeloTabla.getColumnCount()).toArray()
                        : columnas;
                for (String t : tokens) {
                    boolean okToken = false;
                    for (int c : cols) {
                        if (c >= 0 && c < entry.getValueCount()) {
                            Object v = entry.getValue(c);
                            if (v != null && containsNorm(v.toString(), t)) { okToken = true; break; }
                        }
                    }
                    if (!okToken) return false;
                }
                return true;
            }
        };
        sorter.setRowFilter(rf);
    }

    private int[] columnasSegunFiltro() {
        if (cbFiltro == null) return columnasDeVistaActual();
        String op = (String) cbFiltro.getSelectedItem();
        if (op == null || op.startsWith("Auto")) return columnasDeVistaActual();
        return columnasParaFiltrarFilas(op);
    }

    private int[] columnasDeVistaActual() {
        int[] keep = FiltrarColumnas.getOrDefault(ultimaVistaSeleccionada, FiltrarColumnas.get("Todos"));
        if (keep == null || keep.length == 0) {
            int cols = modeloTabla.getColumnCount();
            int[] all = new int[cols];
            for (int i = 0; i < cols; i++) all[i] = i;
            return all;
        }
        return keep;
    }

    private int[] columnasParaFiltrarFilas(String opcion) {
        if (opcion == null) opcion = "todos";
        String k = opcion.toLowerCase(java.util.Locale.ROOT);
        k = java.text.Normalizer.normalize(k, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+","")
                .replaceAll("\\s+"," ")
                .trim();

        switch (k) {
            case "cliente":
                return new int[]{1};
            case "datos del operador":
            case "datos del operador y custodio":
                return new int[]{11, 25}; // OPERADOR, CUSTODIO
            case "placas de camion y conductor":
            case "placas del camion y conductor":
                return new int[]{12, 13, 11}; // PLACAS + OPERADOR
            case "datos del furgon":
                return new int[]{13};
            case "referencia del cliente":
            case "referencia del cliente (viaje)":
                return new int[]{7};
            case "informacion del viaje":
            case "información del viaje":
                return new int[]{6, 18, 19, 20, 21, 22, 23}; // DESTINO + fechas
            case "medio de carta de poder":
                return new int[]{0, 2, 10};
            case "todos":
            default:
                int cols = modeloTabla.getColumnCount();
                int[] all = new int[cols];
                for (int i = 0; i < cols; i++) all[i] = i;
                return all;
        }
    }

    // Selector de vistas
    private void crearSelectorMostrarColumnas() {
        FiltrarColumnas.clear();
        FiltrarColumnas.put("Todos", new int[] {
                0,1,2,3,4,5,6,7,8,9,10,
                11,12,13,14,15,16,17,18,19,20,
                21,22,23,24,25,26,27
        });

        asegurarTablaFiltros();
        cargarFiltrosPersonalizadosDesdeDB();

        JLabel lblMostrar = new JLabel("Filtrar Columnas:");
        lblMostrar.setFont(new Font("Poppins", Font.BOLD, 14));
        lblMostrar.setBounds(620, 85, 150, 20);
        getContentPane().add(lblMostrar);

        cbMostrarColumnas = new JComboBox<>();
        cbMostrarColumnas.setBounds(750, 80, 200, 30);
        cbMostrarColumnas.setToolTipText("Cambia la vista de columnas mostradas o crea un filtro personalizado");
        getContentPane().add(cbMostrarColumnas);

        refrescarComboVistas("Todos");

        cbMostrarColumnas.addActionListener(e -> {
            String key = (String) cbMostrarColumnas.getSelectedItem();
            if (key == null) return;

            // Crear nuevo filtro
            if (CREAR_FILTRO_LABEL.equals(key)) {
                boolean creado = mostrarDialogoCrearFiltro();
                if (!creado) {
                    cbMostrarColumnas.setSelectedItem(ultimaVistaSeleccionada);
                }
                return;
            }

            // Eliminar filtro actual
            if (ELIMINAR_FILTRO_LABEL.equals(key)) {
                eliminarFiltroActual();
                return;
            }

            // Vista real
            ultimaVistaSeleccionada = key;
            int[] keep = FiltrarColumnas.getOrDefault(key, FiltrarColumnas.get("Todos"));
            aplicarVistaColumnas(keep);
        });
    }

    private void refrescarComboVistas(String seleccionar) {
        cbMostrarColumnas.removeAllItems();

        // Primero las vistas reales
        for (String k : FiltrarColumnas.keySet()) {
            cbMostrarColumnas.addItem(k);
        }

        // Opciones especiales
        cbMostrarColumnas.addItem(CREAR_FILTRO_LABEL);
        cbMostrarColumnas.addItem(ELIMINAR_FILTRO_LABEL);

        if (seleccionar == null || !FiltrarColumnas.containsKey(seleccionar)) {
            seleccionar = "Todos";
        }

        cbMostrarColumnas.setSelectedItem(seleccionar);
        ultimaVistaSeleccionada = seleccionar;
    }

    private boolean mostrarDialogoCrearFiltro() {
        JDialog dlg = new JDialog(this, "Nuevo Filtro", true);
        dlg.setLayout(new BorderLayout(10,10));
        dlg.setSize(520, 560);
        dlg.setLocationRelativeTo(this);

        // === PARTE SUPERIOR: NOMBRE DEL FILTRO ===
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8,8,8,8);
        gc.anchor = GridBagConstraints.WEST;

        gc.gridx = 0;
        gc.gridy = 0;
        top.add(new JLabel("Nombre del filtro:"), gc);

        JTextField tfNombre = new JTextField(26);
        gc.gridx = 1;
        gc.gridy = 0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        top.add(tfNombre, gc);

        // === CENTRO: CHECKS DE COLUMNAS + "SELECCIONAR TODO" ===
        JPanel center = new JPanel(new GridBagLayout());
        center.setBorder(BorderFactory.createTitledBorder("Selecciona las columnas a mostrar"));
        GridBagConstraints cc = new GridBagConstraints();
        cc.insets = new Insets(4,10,4,10);
        cc.anchor = GridBagConstraints.WEST;
        cc.fill = GridBagConstraints.HORIZONTAL;
        cc.weightx = 1.0;

        JCheckBox[] Casillas = new JCheckBox[COLUMNAS.length];
        int row = 0;

        // Check de "Seleccionar todo"
        JCheckBox chkSeleccionarTodo = new JCheckBox("Seleccionar todo");
        chkSeleccionarTodo.setFont(new Font("Poppins", Font.BOLD, 12));
        cc.gridx = 0;
        cc.gridy = row++;
        center.add(chkSeleccionarTodo, cc);

        // Checkboxes de columnas (por defecto DESMARCADAS)
        for (int i = 0; i < COLUMNAS.length; i++) {
            Casillas[i] = new JCheckBox("[" + i + "] " + COLUMNAS[i]);
            Casillas[i].setSelected(false);
            cc.gridx = 0;
            cc.gridy = row++;
            center.add(Casillas[i], cc);
        }

        // Scroll para la lista de columnas
        JScrollPane sp = new JScrollPane(center);
        sp.setPreferredSize(new Dimension(480, 380));

        // === BOTONES INFERIORES ===
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        JButton btnCancelar = new JButton("Cancelar");
        JButton btnGuardar = new JButton("Guardar filtro");
        bottom.add(btnCancelar);
        bottom.add(btnGuardar);

        dlg.add(top, BorderLayout.NORTH);
        dlg.add(sp, BorderLayout.CENTER);
        dlg.add(bottom, BorderLayout.SOUTH);

        final boolean[] ok = {false};

        // === LÓGICA "SELECCIONAR TODO" ===
        final boolean[] actualizando = {false};

        chkSeleccionarTodo.addActionListener(ev -> {
            actualizando[0] = true;
            boolean sel = chkSeleccionarTodo.isSelected();
            for (JCheckBox cb : Casillas) {
                if (cb != null) cb.setSelected(sel);
            }
            actualizando[0] = false;
        });

        // Si el usuario marca / desmarca manualmente, actualizamos el "Seleccionar todo"
        for (JCheckBox cb : Casillas) {
            cb.addItemListener(e -> {
                if (actualizando[0]) return; // viene desde chkSeleccionarTodo, no hacemos nada
                boolean todas = true;
                boolean alguna = false;
                for (JCheckBox cbox : Casillas) {
                    if (cbox != null) {
                        if (cbox.isSelected()) alguna = true;
                        else todas = false;
                    }
                }
                chkSeleccionarTodo.setSelected(todas && alguna);
            });
        }

        // === ACCIÓN CANCELAR ===
        btnCancelar.addActionListener(ev -> dlg.dispose());

        // === ACCIÓN GUARDAR ===
        btnGuardar.addActionListener(ev -> {
            String nombre = tfNombre.getText().trim();
            if (nombre.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Asigna un nombre al filtro.", "Falta nombre", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (CREAR_FILTRO_LABEL.equals(nombre) || ELIMINAR_FILTRO_LABEL.equals(nombre)) {
                JOptionPane.showMessageDialog(dlg, "Ese nombre está reservado. Elige otro.", "Nombre inválido", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (FiltrarColumnas.containsKey(nombre)) {
                int r2 = JOptionPane.showConfirmDialog(dlg,
                        "Ya existe un filtro llamado \""+nombre+"\".\n¿Deseas reemplazarlo?",
                        "Duplicado",
                        JOptionPane.YES_NO_OPTION);
                if (r2 != JOptionPane.YES_OPTION) return;
            }

            java.util.List<Integer> sel = new ArrayList<>();
            for (int i = 0; i < Casillas.length; i++) {
                if (Casillas[i].isSelected()) sel.add(i);
            }

            if (sel.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Selecciona al menos una columna.", "Nada seleccionado", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int[] keep = new int[sel.size()];
            for (int i = 0; i < sel.size(); i++) keep[i] = sel.get(i);

            FiltrarColumnas.put(nombre, keep);
            refrescarComboVistas(nombre);
            aplicarVistaColumnas(keep);
            guardarFiltroPersonalizadoEnDB(nombre, keep);

            ok[0] = true;
            dlg.dispose();
        });

        dlg.setVisible(true);
        return ok[0];
    }

    private void snapshotColumnasOriginales() {
        TableColumnModel tcm = tabla.getColumnModel();
        int count = tcm.getColumnCount();
        columnasOriginales = new TableColumn[modeloTabla.getColumnCount()];
        for (int viewIndex = 0; viewIndex < count; viewIndex++) {
            TableColumn tc = tcm.getColumn(viewIndex);
            int modelIdx = tabla.convertColumnIndexToModel(viewIndex);
            tc.setIdentifier(modelIdx);
            columnasOriginales[modelIdx] = tc;
        }
    }

    private void aplicarVistaColumnas(int[] keep) {
        if (columnasOriginales == null) snapshotColumnasOriginales();
        Set<Integer> mantener = new LinkedHashSet<>();
        for (int k : keep) if (k >= 0 && k < columnasOriginales.length) mantener.add(k);

        TableColumnModel tcm = tabla.getColumnModel();
        for (int i = tcm.getColumnCount() - 1; i >= 0; i--) tcm.removeColumn(tcm.getColumn(i));

        if (mantener.size() == columnasOriginales.length) {
            for (int i = 0; i < columnasOriginales.length; i++) if (columnasOriginales[i] != null) tcm.addColumn(columnasOriginales[i]);
        } else {
            for (int k : keep) {
                TableColumn col = columnasOriginales[k];
                if (col != null) tcm.addColumn(col);
            }
        }

        configurarAnchosColumnas();
        aplicarRendererNumericos();
        aplicarColoresColumnasTexto();  // <<< REAPLICAR COLORES AL CAMBIAR VISTA
        tabla.revalidate();
        tabla.repaint();
        aplicarFiltro();

        // re-aplicar editores (fecha y autocomplete) tras cambiar columnas visibles
        aplicarEditoresFecha();
        AUTOCOMPLETADO();
    }

    // CONEXIÓN A LA BD
    private Connection obtenerConexion() throws SQLException {
        String url = "jdbc:mysql://localhost:3306/EmpresaLog"
                + "?useSSL=false"
                + "&allowPublicKeyRetrieval=true"
                + "&useUnicode=true&characterEncoding=UTF-8"
                + "&serverTimezone=America/Merida";
        return DriverManager.getConnection(url, "admin", "12345ñ");
    }

    // Persistencia de filtros personalizados
    private void asegurarTablaFiltros() {
        String ddl = "CREATE TABLE IF NOT EXISTS Filtro_Columnas ("
                   + " nombre VARCHAR(60) PRIMARY KEY,"
                   + " columnas VARCHAR(512) NOT NULL,"
                   + " usuario VARCHAR(100) NULL,"
                   + " actualizado TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)";
        try (Connection c = obtenerConexion(); Statement st = c.createStatement()) {
            st.execute(ddl);
        } catch (SQLException ex) {
            System.err.println("[Filtro_Columnas] No se pudo asegurar tabla: " + ex.getMessage());
        }
    }

    private String serializeCols(int[] keep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keep.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(keep[i]);
        }
        return sb.toString();
    }

    private int[] parseCols(String s) {
        if (s == null || s.trim().isEmpty()) return new int[0];
        String[] parts = s.split(",");
        int[] arr = new int[parts.length];
        int k = 0;
        for (String p : parts) {
            try { arr[k++] = Integer.parseInt(p.trim()); } catch (Exception ignore) {}
        }
        return (k == arr.length) ? arr : java.util.Arrays.copyOf(arr, k);
    }

    private void guardarFiltroPersonalizadoEnDB(String nombre, int[] keep) {
        String upsert = "INSERT INTO Filtro_Columnas (nombre, columnas) VALUES (?,?) "
                      + "ON DUPLICATE KEY UPDATE columnas = VALUES(columnas)";
        try (Connection c = obtenerConexion(); PreparedStatement ps = c.prepareStatement(upsert)) {
            ps.setString(1, nombre);
            ps.setString(2, serializeCols(keep));
            ps.executeUpdate();
        } catch (SQLException ex) {
            System.err.println("[Filtro_Columnas] No se pudo guardar '" + nombre + "': " + ex.getMessage());
        }
    }

    // NUEVOS MÉTODOS: eliminar filtro
    private void eliminarFiltroPersonalizadoDeDB(String nombre) {
        String sql = "DELETE FROM Filtro_Columnas WHERE nombre = ?";
        try (Connection c = obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ps.executeUpdate();
        } catch (SQLException ex) {
            System.err.println("[Filtro_Columnas] No se pudo eliminar '" + nombre + "': " + ex.getMessage());
        }
    }

    private void eliminarFiltroActual() {
        String filtroAEliminar = ultimaVistaSeleccionada;

        if (filtroAEliminar == null || "Todos".equalsIgnoreCase(filtroAEliminar)) {
            JOptionPane.showMessageDialog(this,
                    "No puedes eliminar la vista \"Todos\".\n" +
                    "Primero selecciona un filtro personalizado.",
                    "Aviso",
                    JOptionPane.INFORMATION_MESSAGE);
            cbMostrarColumnas.setSelectedItem(ultimaVistaSeleccionada);
            return;
        }

        int r = JOptionPane.showConfirmDialog(this,
                "¿Eliminar el filtro \"" + filtroAEliminar + "\"?\n" +
                "Se quitará del combo y de la tabla Filtro_Columnas.",
                "Confirmar eliminación",
                JOptionPane.YES_NO_OPTION);

        if (r != JOptionPane.YES_OPTION) {
            cbMostrarColumnas.setSelectedItem(ultimaVistaSeleccionada);
            return;
        }

        // 1) Eliminar de la estructura en memoria
        FiltrarColumnas.remove(filtroAEliminar);

        // 2) Eliminar de la base de datos
        eliminarFiltroPersonalizadoDeDB(filtroAEliminar);

        // 3) Volver a "Todos"
        refrescarComboVistas("Todos");
        int[] keep = FiltrarColumnas.get("Todos");
        if (keep != null) {
            aplicarVistaColumnas(keep);
        }
    }

    private void cargarFiltrosPersonalizadosDesdeDB() {
        String sql = "SELECT nombre, columnas FROM Filtro_Columnas ORDER BY nombre";
        try (Connection c = obtenerConexion(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String nombre = rs.getString("nombre");
                int[] keep = parseCols(rs.getString("columnas"));
                if (keep != null && keep.length > 0) {
                    if (!FiltrarColumnas.containsKey(nombre)) FiltrarColumnas.put(nombre, keep);
                }
            }
        } catch (SQLException ex) {
            System.err.println("[Filtro_Columnas] No se pudieron cargar filtros: " + ex.getMessage());
        }
    }

    // Cargar datos (también llena sugerencias)
    private void cargarDatos() {
        String sql = "SELECT * FROM Carta_Porte";
        try (Connection conn = obtenerConexion(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            modeloTabla.setRowCount(0);

            // limpia sugerencias actuales y se van a recalcular de BD
            initSugerencias();

            while (rs.next()) {
                Object[] row = new Object[] {
                    rs.getInt("Carta_Porte_id"),
                    v(rs, "Cliente"),
                    v(rs, "FACTURA"),
                    v(rs, "FECHA_FACTURA"),
                    rs.getBigDecimal("VALOR"),
                    v(rs, "FECHA_DE_PAGO"),
                    v(rs, "DESTINO"),
                    v(rs, "REFERENCIA"),
                    v(rs, "REMITENTE"),
                    v(rs, "CONSIGNATORIO"),
                    v(rs, "FACTURA2"),
                    v(rs, "OPERADOR"),
                    v(rs, "PLACA_CABEZAL"),
                    v(rs, "PLACA_DEL_FURGON"),
                    rs.getBigDecimal("VALOR_FLETE"),
                    rs.getBigDecimal("ANTICIPO"),
                    rs.getBigDecimal("A_CANCELACION"),
                    v(rs, "FECHA_DE_PAGADO"),
                    v(rs, "F_DE_CARGA"),
                    v(rs, "F_DE_CRUCE"),
                    v(rs, "F_SAL_T_U"),
                    v(rs, "F_F_DESTINO"),
                    v(rs, "F_EN_DESTINO"),
                    v(rs, "F_DESCARGA"),
                    v(rs, "F_E_DE_DOCTOS"),
                    v(rs, "CUSTODIO"),
                    v(rs, "PAGADO"),
                    v(rs, "OBSERVACIONES")
                };
                modeloTabla.addRow(row);
                addSuggestionsFromRow(row);
            }

            // re-aplica editores de autocomplete con datos ya cargados
            AUTOCOMPLETADO();

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error al cargar los datos: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String v(ResultSet rs, String col) {
        try {
            Object o = rs.getObject(col);
            if (o == null) return "";
            if (o instanceof java.sql.Timestamp) {
                java.sql.Timestamp t = (java.sql.Timestamp) o;
                boolean tieneHora = (t.toLocalDateTime().getHour()
                        + t.toLocalDateTime().getMinute()
                        + t.toLocalDateTime().getSecond()) != 0;
                return new SimpleDateFormat(tieneHora ? "yyyy-MM-dd HH:mm" : "yyyy-MM-dd").format(t);
            }
            if (o instanceof java.sql.Date) return new SimpleDateFormat("yyyy-MM-dd").format((java.sql.Date) o);
            if (o instanceof java.sql.Time) return new SimpleDateFormat("HH:mm").format((java.sql.Time) o);
            return String.valueOf(o);
        } catch (SQLException ex) {
            return "";
        }
    }

    private void eliminarRegistro(String cartaPorteId) {
        int confirmacion = JOptionPane.showConfirmDialog(this, "¿Estás seguro de eliminar este registro?", "Confirmación", JOptionPane.YES_NO_OPTION);
        if (confirmacion != JOptionPane.YES_OPTION) return;

        String sql = "DELETE FROM Carta_Porte WHERE Carta_Porte_id = ?";
        try (Connection conn = obtenerConexion(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cartaPorteId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                JOptionPane.showMessageDialog(this, "Registro eliminado con éxito.");
                cargarDatos();
            } else {
                JOptionPane.showMessageDialog(this, "No se encontró el registro.");
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error al eliminar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Anchos de columnas
    private void configurarAnchosColumnas() {
        int[] anchos = {
                120, // Carta_Porte_id
                180, // Cliente
                130, // FACTURA
                160, // FECHA_FACTURA
                110, // VALOR
                140, // FECHA_DE_PAGO
                160, // DESTINO
                150, // REFERENCIA
                160, // REMITENTE
                160, // CONSIGNATORIO
                110, // FACTURA2
                150, // OPERADOR
                140, // PLACA_CABEZAL
                160, // PLACA_DEL_FURGON
                130, // VALOR_FLETE
                110, // ANTICIPO
                130, // A_CANCELACION
                150, // FECHA_DE_PAGADO
                140, // F_DE_CARGA
                140, // F_DE_CRUCE
                140, // F_SAL_T_U
                150, // F_F_DESTINO
                150, // F_EN_DESTINO
                150, // F_DESCARGA
                160, // F_E_DE_DOCTOS
                130, // CUSTODIO
                110, // PAGADO
                260  // OBSERVACIONES
        };
        TableColumnModel tcm = tabla.getColumnModel();
        for (int i = 0; i < anchos.length && i < tcm.getColumnCount(); i++) {
            tcm.getColumn(i).setPreferredWidth(anchos[i]);
            tcm.getColumn(i).setMinWidth(70);
        }
    }

    // Logo util
    private ImageIcon scaledIcon(String resourcePath, int w, int h) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("No se encontró el recurso: " + resourcePath);
                return null;
            }
            BufferedImage img = ImageIO.read(is);
            Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (IOException e) { e.printStackTrace(); return null; }
    }

    public void setLogoBounds(int x, int y, int w, int h) {
        if (logoLabel == null) return;
        logoLabel.setBounds(x, y, w, h);
        ImageIcon ic = scaledIcon(LOGOImagen, w, h);
        if (ic != null) logoLabel.setIcon(ic);
        logoLabel.revalidate();
        logoLabel.repaint();
    }

    // ===== Editor calendario para columnas de fecha =====
    private void aplicarEditoresFecha() {
        TableColumnModel tcm = tabla.getColumnModel();
        for (int modelIdx = 0; modelIdx < COLUMNAS.length; modelIdx++) {
            if (esColumnaFecha(COLUMNAS[modelIdx])) {
                int viewIdx = tabla.convertColumnIndexToView(modelIdx);
                if (viewIdx >= 0 && viewIdx < tcm.getColumnCount()) {
                    tcm.getColumn(viewIdx).setCellEditor(new DateCellEditor());
                }
            }
        }
    }

    private class DateCellEditor extends AbstractCellEditor implements javax.swing.table.TableCellEditor {
        private final JPanel panel = new JPanel(new BorderLayout(4,0));
        private final JTextField field = new JTextField();
        private final JButton btn = new JButton("📅");

        DateCellEditor() {
            field.setBorder(BorderFactory.createEmptyBorder(0,4,0,0));
            btn.setFocusable(false);
            btn.setMargin(new Insets(2,6,2,6));
            btn.setToolTipText("Elegir fecha (por defecto hoy)");
            btn.addActionListener(e -> {
                java.util.Date pre = new java.util.Date(); // hoy
                String actual = field.getText().trim();
                if (!actual.isEmpty()) {
                    try { pre = new java.text.SimpleDateFormat("yyyy-MM-dd").parse(actual); }
                    catch (Exception ignore) {
                        try { pre = new java.text.SimpleDateFormat("d 'de' MMMM 'del' yyyy", LOCALE_ES_MX).parse(actual); }
                        catch (Exception ignore2) { /* usa hoy */ }
                    }
                }
                JSpinner sp = new JSpinner(new SpinnerDateModel(pre, null, null, Calendar.DAY_OF_MONTH));
                JSpinner.DateEditor ed = new JSpinner.DateEditor(sp, "dd/MM/yyyy");
                sp.setEditor(ed);
                int r2 = JOptionPane.showConfirmDialog(Registros.this, sp, "Selecciona fecha", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (r2 == JOptionPane.OK_OPTION) {
                    java.util.Date sel = (java.util.Date) sp.getValue();
                    field.setText(formatearFechaBonita(sel));
                    stopCellEditing();
                }
            });
            panel.add(field, BorderLayout.CENTER);
            panel.add(btn, BorderLayout.EAST);
        }

        @Override public Object getCellEditorValue() { return field.getText(); }
        @Override public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            field.setText(value == null ? "" : value.toString());
            SwingUtilities.invokeLater(() -> field.requestFocusInWindow());
            return panel;
        }
    }

    // ===== Editor con autocompletado para columnas de texto =====
    private class SUGERENCIAS extends AbstractCellEditor implements javax.swing.table.TableCellEditor {
        private final JTextField field = new JTextField();
        private final JPopupMenu popup = new JPopupMenu();
        private final JList<String> list = new JList<>(new DefaultListModel<>());
        private final JScrollPane sp = new JScrollPane(list);
        private Set<String> base; // sugerencias base (columna)
        private String lastText = "";

        SUGERENCIAS(Set<String> base) {
            this.base = base;

            popup.setFocusable(false);
            popup.setBorder(BorderFactory.createLineBorder(new Color(180,180,180)));

            sp.setBorder(BorderFactory.createEmptyBorder());
            popup.add(sp);

            popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
                @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                    SwingUtilities.invokeLater(() -> field.requestFocusInWindow());
                }
                @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                    SwingUtilities.invokeLater(() -> field.requestFocusInWindow());
                }
                @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) { }
            });

            field.setBorder(BorderFactory.createEmptyBorder(0,4,0,4));
            field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
                @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
                @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
                private void changed() { updateSuggestions(); }
            });

            field.addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    if (popup.isVisible()) {
                        if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                            int n = list.getModel().getSize();
                            if (n > 0) { list.setSelectedIndex(0); list.requestFocusInWindow(); }
                            e.consume();
                        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                            popup.setVisible(false);
                            e.consume();
                        } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                            if (!list.isFocusOwner()) {
                                stopCellEditing();
                                e.consume();
                            }
                        }
                    }
                }
            });

            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) { applySelection(); }
                }
            });
            list.addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) { applySelection(); e.consume(); }
                    else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        popup.setVisible(false);
                        field.requestFocusInWindow();
                        e.consume();
                    }
                }
            });
        }

        private void applySelection() {
            String sel = list.getSelectedValue();
            if (sel != null) {
                field.setText(sel);
                popup.setVisible(false);
                stopCellEditing();
            }
        }

        private void updateSuggestions() {
            String txt = field.getText();
            if (Objects.equals(txt, lastText)) return;
            lastText = txt;

            DefaultListModel<String> model = (DefaultListModel<String>) list.getModel();
            model.clear();

            if (base == null || base.isEmpty()) { popup.setVisible(false); return; }
            String needle = normalize(txt);

            if (needle.isEmpty()) {
                int c = 0;
                for (String s : base) {
                    model.addElement(s);
                    if (++c >= 10) break;
                }
            } else {
                int count = 0;
                for (String s : base) {
                    if (normalize(s).contains(needle)) {
                        model.addElement(s);
                        if (++count >= 20) break;
                    }
                }
            }

            if (model.getSize() == 0) {
                popup.setVisible(false);
                return;
            }

            if (!popup.isVisible()) {
                popup.setPreferredSize(new Dimension(field.getWidth(), 160));
                popup.show(field, 0, field.getHeight());
            } else {
                popup.setPreferredSize(new Dimension(field.getWidth(), 160));
                popup.pack();
                popup.revalidate();
                popup.repaint();
            }
            SwingUtilities.invokeLater(() -> field.requestFocusInWindow());
        }

        @Override public Object getCellEditorValue() { return field.getText(); }

        @Override public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            field.setText(value == null ? "" : value.toString());
            SwingUtilities.invokeLater(() -> {
                field.requestFocusInWindow();
                field.selectAll();
                updateSuggestions();
            });
            return field;
        }
    }

    // Abrir formulario de ingreso (opcional)
    private void abrirFormularioDeIngreso() {
        String[] candidatos = { "GestionSoftware.Ingresar", "GestionSoftware.IngresarCartaPorteCompact" };
        for (String cls : candidatos) {
            try {
                Class<?> c = Class.forName(cls);
                if (!JFrame.class.isAssignableFrom(c)) continue;
                JFrame f = (JFrame) c.getDeclaredConstructor().newInstance();
                f.setVisible(true);
                dispose();
                return;
            } catch (Exception ignore) { }
        }
        JOptionPane.showMessageDialog(this,
                "No encontré la ventana de ingreso (clases 'Ingresar' o 'IngresarCartaPorteCompact').\n" +
                        "Verifica que estén en el package GestionSoftware y tengan constructor sin parámetros.",
                "Aviso", JOptionPane.WARNING_MESSAGE);
    }

    // ===========================
    // Inserción inline
    // ===========================
    private void empezarInsercion() {
        if (filaEnInsercion != null) {
            JOptionPane.showMessageDialog(this, "Ya estás agregando una fila. Guarda o cancela primero.");
            return;
        }
        if (txtBuscar != null) {
            txtBuscar.setText("");
            aplicarFiltro();
        }

        Object[] vacia = new Object[COLUMNAS.length];
        Arrays.fill(vacia, "");
        modeloTabla.addRow(vacia);
        filaEnInsercion = modeloTabla.getRowCount() - 1;

        activarEditoresParaInsercion(true);

        int viewRow = tabla.convertRowIndexToView(filaEnInsercion);
        if (viewRow >= 0) {
            tabla.changeSelection(viewRow, IDX_ID, false, false);
            tabla.editCellAt(viewRow, IDX_ID);
            tabla.requestFocusInWindow();
        }

        setModoInsercion(true);
    }

    private void activarEditoresParaInsercion(boolean on) {
        TableColumnModel tcm = tabla.getColumnModel();
        int[] decCols = {IDX_VALOR, IDX_VALOR_FLETE, IDX_ANTICIPO, IDX_A_CANCELACION};
        for (int c : decCols) {
            int viewIdx = tabla.convertColumnIndexToView(c);
            if (viewIdx >= 0 && viewIdx < tcm.getColumnCount()) {
                if (on) tcm.getColumn(viewIdx).setCellEditor(new DefaultCellEditor(new JTextField()));
                else    tcm.getColumn(viewIdx).setCellEditor(null);
            }
        }
        if (on) {
            aplicarEditoresFecha();
            AUTOCOMPLETADO();
        }
    }

    private void guardarNuevaFila() {
        if (filaEnInsercion == null) return;
        if (tabla.isEditing() && tabla.getCellEditor() != null) tabla.getCellEditor().stopCellEditing();

        int r = filaEnInsercion;

        String sId = asStr(modeloTabla.getValueAt(r, IDX_ID));
        if (sId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El campo Carta_Porte_id es obligatorio.");
            return;
        }
        int id;
        try { id = Integer.parseInt(sId); } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Carta_Porte_id debe ser numérico (INT).");
            return;
        }

        BigDecimal valor       = parseDecimal(asStr(modeloTabla.getValueAt(r, IDX_VALOR)));
        BigDecimal valorFlete  = parseDecimal(asStr(modeloTabla.getValueAt(r, IDX_VALOR_FLETE)));
        BigDecimal anticipo    = parseDecimal(asStr(modeloTabla.getValueAt(r, IDX_ANTICIPO)));
        BigDecimal aCancelar   = parseDecimal(asStr(modeloTabla.getValueAt(r, IDX_A_CANCELACION)));
        if (aCancelar == null && valorFlete != null && anticipo != null) {
            aCancelar = valorFlete.subtract(anticipo);
            modeloTabla.setValueAt(aCancelar, r, IDX_A_CANCELACION);
        }

        String cliente        = asStr(modeloTabla.getValueAt(r, IDX_CLIENTE));
        String factura        = asStr(modeloTabla.getValueAt(r, 2));
        String fechaFactura   = asStr(modeloTabla.getValueAt(r, IDX_FECHA_FACTURA));
        String fDePago        = asStr(modeloTabla.getValueAt(r, 5));
        String destino        = asStr(modeloTabla.getValueAt(r, IDX_DESTINO));
        String referencia     = asStr(modeloTabla.getValueAt(r, 7));
        String remitente      = asStr(modeloTabla.getValueAt(r, IDX_REMITENTE));
        String consignatario  = asStr(modeloTabla.getValueAt(r, IDX_CONSIGNATARIO));
        String factura2       = asStr(modeloTabla.getValueAt(r,10));
        String operador       = asStr(modeloTabla.getValueAt(r, IDX_OPERADOR));
        String placaCabeza    = asStr(modeloTabla.getValueAt(r, IDX_PLACA_CABEZAL));
        String placaFurgon    = asStr(modeloTabla.getValueAt(r, IDX_PLACA_FURGON));
        String fDePagado      = asStr(modeloTabla.getValueAt(r, 17));
        String fDeCarga       = asStr(modeloTabla.getValueAt(r, 18));
        String fDeCruce       = asStr(modeloTabla.getValueAt(r, 19));
        String fSalTU         = asStr(modeloTabla.getValueAt(r, 20));
        String fFDestino      = asStr(modeloTabla.getValueAt(r, 21));
        String fEnDestino     = asStr(modeloTabla.getValueAt(r, 22));
        String fDescarga      = asStr(modeloTabla.getValueAt(r, 23));
        String fEDoctos       = asStr(modeloTabla.getValueAt(r, 24));
        String custodio       = asStr(modeloTabla.getValueAt(r, 25));
        String pagado         = asStr(modeloTabla.getValueAt(r, 26));
        String observaciones  = asStr(modeloTabla.getValueAt(r, 27));

        String sql = "INSERT INTO Carta_Porte (" +
                "Carta_Porte_id, Cliente, FACTURA, FECHA_FACTURA, VALOR, FECHA_DE_PAGO, DESTINO, REFERENCIA, REMITENTE, CONSIGNATORIO, " +
                "FACTURA2, OPERADOR, PLACA_CABEZAL, PLACA_DEL_FURGON, VALOR_FLETE, ANTICIPO, A_CANCELACION, FECHA_DE_PAGADO, F_DE_CARGA, F_DE_CRUCE, " +
                "F_SAL_T_U, F_F_DESTINO, F_EN_DESTINO, F_DESCARGA, F_E_DE_DOCTOS, CUSTODIO, PAGADO, OBSERVACIONES" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (Connection conn = obtenerConexion(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int p = 1;
            ps.setInt(p++, id);
            ps.setString(p++, cliente);
            ps.setString(p++, factura);
            ps.setString(p++, normalizeFecha(fechaFactura));
            if (valor != null)       ps.setBigDecimal(p++, valor);       else ps.setNull(p++, Types.DECIMAL);
            ps.setString(p++, normalizeFecha(fDePago));
            ps.setString(p++, destino);
            ps.setString(p++, referencia);
            ps.setString(p++, remitente);
            ps.setString(p++, consignatario);
            ps.setString(p++, factura2);
            ps.setString(p++, operador);
            ps.setString(p++, placaCabeza);
            ps.setString(p++, placaFurgon);
            if (valorFlete != null)  ps.setBigDecimal(p++, valorFlete);  else ps.setNull(p++, Types.DECIMAL);
            if (anticipo != null)    ps.setBigDecimal(p++, anticipo);    else ps.setNull(p++, Types.DECIMAL);
            if (aCancelar != null)   ps.setBigDecimal(p++, aCancelar);   else ps.setNull(p++, Types.DECIMAL);
            ps.setString(p++, normalizeFecha(fDePagado));
            ps.setString(p++, normalizeFecha(fDeCarga));
            ps.setString(p++, normalizeFecha(fDeCruce));
            ps.setString(p++, normalizeFecha(fSalTU));
            ps.setString(p++, normalizeFecha(fFDestino));
            ps.setString(p++, normalizeFecha(fEnDestino));
            ps.setString(p++, normalizeFecha(fDescarga));
            ps.setString(p++, normalizeFecha(fEDoctos));
            ps.setString(p++, custodio);
            ps.setString(p++, pagado);
            ps.setString(p++, observaciones);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                JOptionPane.showMessageDialog(this, "Registro insertado correctamente.");

                RegistrosGuardados(IDX_CLIENTE, cliente);
                RegistrosGuardados(IDX_OPERADOR, operador);
                RegistrosGuardados(IDX_DESTINO, destino);
                RegistrosGuardados(IDX_REMITENTE, remitente);
                RegistrosGuardados(IDX_CONSIGNATARIO, consignatario);
                RegistrosGuardados(IDX_PLACA_CABEZAL, placaCabeza);
                RegistrosGuardados(IDX_PLACA_FURGON, placaFurgon);

                filaEnInsercion = null;
                activarEditoresParaInsercion(false);
                cargarDatos();
                setModoInsercion(false);
            } else {
                JOptionPane.showMessageDialog(this, "No se insertó el registro (verifica los datos).", "Aviso", JOptionPane.WARNING_MESSAGE);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error al insertar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cancelarInsercion() {
        if (filaEnInsercion == null) {
            setModoInsercion(false);
            return;
        }
        try { modeloTabla.removeRow(filaEnInsercion); } catch (Exception ignore) {}
        filaEnInsercion = null;
        activarEditoresParaInsercion(false);
        setModoInsercion(false);
    }

    // Editor de registro (dialog)
    private class EditarRegistroDialog extends JDialog {
        private final String id;
        private final int modelRow;
        private final JCheckBox[] checks;
        private final JTextField[] fields;

        private final String[] numericas = {"VALOR","VALOR_FLETE","ANTICIPO","A_CANCELACION"};

        EditarRegistroDialog(Frame owner, String cartaPorteId, int modelRow, Object[] rowData) {
            super(owner, "Editar registro: " + cartaPorteId, true);
            this.id = cartaPorteId;
            this.modelRow = modelRow;
            this.checks = new JCheckBox[COLUMNAS.length];
            this.fields = new JTextField[COLUMNAS.length];

            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setSize(800, 620);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout(10,10));

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JLabel pkLbl = new JLabel("Carta_Porte_id: " + id);
            pkLbl.setFont(new Font("Poppins", Font.BOLD, 16));
            top.add(pkLbl);
            add(top, BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridBagLayout());
            grid.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(6,6,6,6);
            c.anchor = GridBagConstraints.WEST;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1.0;

            int row = 0;
            for (int i = 1; i < COLUMNAS.length; i++) {
                final int colIndex = i;
                final String colName = COLUMNAS[i];
                checks[i] = new JCheckBox(colName);

                JPanel fieldPanel = new JPanel(new BorderLayout(6,0));
                fields[i] = new JTextField(rowData[i] == null ? "" : rowData[i].toString(), 24);
                if (isNumerica(colName)) {
                    fields[i].setToolTipText("Campo numérico decimal. Ej: 1234.56");
                    fields[i].setHorizontalAlignment(JTextField.RIGHT);
                }

                if (esColumnaFecha(colName)) {
                    JButton cal = new JButton("📅");
                    cal.setFocusable(false);
                    cal.setMargin(new Insets(2,6,2,6));
                    cal.setToolTipText("Elegir fecha");
                    cal.addActionListener(ev -> {
                        java.util.Date pre = new java.util.Date();
                        String actual = fields[colIndex].getText().trim();
                        try {
                            if (!actual.isEmpty()) {
                                try { pre = new java.text.SimpleDateFormat("yyyy-MM-dd").parse(actual); }
                                catch (Exception ignore) { pre = new java.text.SimpleDateFormat("d 'de' MMMM 'del' yyyy", LOCALE_ES_MX).parse(actual); }
                            }
                        } catch (Exception ignore) {}
                        JSpinner sp = new JSpinner(new SpinnerDateModel(pre, null, null, Calendar.DAY_OF_MONTH));
                        JSpinner.DateEditor ed = new JSpinner.DateEditor(sp, "dd/MM/yyyy");
                        sp.setEditor(ed);
                        int r2 = JOptionPane.showConfirmDialog(this, sp, "Selecciona fecha", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                        if (r2 == JOptionPane.OK_OPTION) {
                            java.util.Date sel = (java.util.Date) sp.getValue();
                            fields[colIndex].setText(formatearFechaBonita(sel));
                            checks[colIndex].setSelected(true);
                        }
                    });
                    fieldPanel.add(fields[i], BorderLayout.CENTER);
                    fieldPanel.add(cal, BorderLayout.EAST);
                } else {
                    fieldPanel.add(fields[i], BorderLayout.CENTER);
                }

                c.gridx = 0; c.gridy = row; c.weightx = 0; grid.add(checks[i], c);
                c.gridx = 1; c.gridy = row; c.weightx = 1; grid.add(fieldPanel, c);
                row++;
            }

            JScrollPane sp = new JScrollPane(grid);
            add(sp, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
            JButton btnGuardar = new JButton("Guardar cambios");
            JButton btnCancelar = new JButton("Cancelar");
            bottom.add(btnCancelar);
            bottom.add(btnGuardar);
            add(bottom, BorderLayout.SOUTH);

            btnCancelar.addActionListener(e -> dispose());

            int COL_VALOR_FLETE = Arrays.asList(COLUMNAS).indexOf("VALOR_FLETE");
            int COL_ANTICIPO    = Arrays.asList(COLUMNAS).indexOf("ANTICIPO");
            int COL_A_CANCEL    = Arrays.asList(COLUMNAS).indexOf("A_CANCELACION");

            KeyAdapter recalc = new KeyAdapter() {
                @Override public void keyReleased(KeyEvent e) {
                    BigDecimal vf = parseDecimal(fields[COL_VALOR_FLETE] != null ? fields[COL_VALOR_FLETE].getText() : null);
                    BigDecimal an = parseDecimal(fields[COL_ANTICIPO]    != null ? fields[COL_ANTICIPO].getText()    : null);
                    if (vf == null || an == null) return;
                    BigDecimal res = vf.subtract(an);
                    if (fields[COL_A_CANCEL] != null) {
                        fields[COL_A_CANCEL].setText(res.toPlainString());
                        if (checks[COL_A_CANCEL] != null) checks[COL_A_CANCEL].setSelected(true);
                    }
                }
            };
            if (COL_VALOR_FLETE >= 0 && fields[COL_VALOR_FLETE] != null) fields[COL_VALOR_FLETE].addKeyListener(recalc);
            if (COL_ANTICIPO    >= 0 && fields[COL_ANTICIPO]    != null) fields[COL_ANTICIPO].addKeyListener(recalc);

            btnGuardar.addActionListener(e -> {
                try { guardarCambios(); }
                catch (SQLException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Error al actualizar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
        }

        private boolean isNumerica(String col) {
            for (String n : numericas) if (n.equalsIgnoreCase(col)) return true;
            return false;
        }
        private BigDecimal parseDecimal(String s) {
            if (s == null) return null;
            String x = s.trim().replace("$","").replace("Q","").replace(",","");
            if (x.isEmpty()) return null;
            try { return new BigDecimal(x); } catch (NumberFormatException ex) { return null; }
        }
        private void guardarCambios() throws SQLException {
            StringBuilder set = new StringBuilder();
            List<Integer> idxs = new ArrayList<>();
            for (int i = 1; i < COLUMNAS.length; i++) {
                if (checks[i] != null && checks[i].isSelected()) {
                    if (set.length() > 0) set.append(", ");
                    set.append(COLUMNAS[i]).append(" = ?");
                    idxs.add(i);
                }
            }
            if (idxs.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Marca al menos un campo para actualizar.");
                return;
            }

            String sql = "UPDATE Carta_Porte SET " + set + " WHERE Carta_Porte_id = ?";
            try (Connection conn = obtenerConexion(); PreparedStatement ps = conn.prepareStatement(sql)) {
                int p = 1;
                for (int colIndex : idxs) {
                    String col = COLUMNAS[colIndex];
                    String val = fields[colIndex].getText();
                    if (isNumerica(col)) {
                        BigDecimal bd = parseDecimal(val);
                        if (bd == null) ps.setNull(p++, Types.DECIMAL);
                        else ps.setBigDecimal(p++, bd);
                    } else if (esColumnaFecha(col)) {
                        ps.setString(p++, normalizeFecha(val));
                    } else {
                        if (val == null) val = "";
                        ps.setString(p++, val.trim());
                    }
                }
                ps.setString(p, id);
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    for (int colIndex : idxs) modeloTabla.setValueAt(fields[colIndex].getText(), modelRow, colIndex);

                    RegistrosGuardados(IDX_CLIENTE,       fields[IDX_CLIENTE]       != null ? fields[IDX_CLIENTE].getText()       : null);
                    RegistrosGuardados(IDX_OPERADOR,      fields[IDX_OPERADOR]      != null ? fields[IDX_OPERADOR].getText()      : null);
                    RegistrosGuardados(IDX_DESTINO,       fields[IDX_DESTINO]       != null ? fields[IDX_DESTINO].getText()       : null);
                    RegistrosGuardados(IDX_REMITENTE,     fields[IDX_REMITENTE]     != null ? fields[IDX_REMITENTE].getText()     : null);
                    RegistrosGuardados(IDX_CONSIGNATARIO, fields[IDX_CONSIGNATARIO] != null ? fields[IDX_CONSIGNATARIO].getText() : null);
                    RegistrosGuardados(IDX_PLACA_CABEZAL, fields[IDX_PLACA_CABEZAL] != null ? fields[IDX_PLACA_CABEZAL].getText() : null);
                    RegistrosGuardados(IDX_PLACA_FURGON,  fields[IDX_PLACA_FURGON]  != null ? fields[IDX_PLACA_FURGON].getText()  : null);

                    AUTOCOMPLETADO();

                    JOptionPane.showMessageDialog(this, "Registro actualizado correctamente.");
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(this, "No se actualizó ningún registro (¿cambió el ID?).", "Aviso", JOptionPane.WARNING_MESSAGE);
                }
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Registros registros = new Registros();
            registros.setVisible(true);
        });
    }
}
