import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.Duration;

class DatabaseManager {
    private final String dbUrl;
    private final String dbUsername;
    private final String dbPassword;

    public DatabaseManager(String dbUrl, String dbUsername, String dbPassword) {
        this.dbUrl = dbUrl;
        this.dbUsername = dbUsername;
        this.dbPassword = dbPassword;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
    }
}

class EmployeeTimesheetApp extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton, signUpButton;
    private final DatabaseManager dbManager;

    public EmployeeTimesheetApp(DatabaseManager dbManager) {
        this.dbManager = dbManager;

        setTitle("Employee Timesheet App");
        setSize(500, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JPanel loginPanel = new JPanel();
        loginPanel.setLayout(new BoxLayout(loginPanel, BoxLayout.Y_AXIS));
        loginPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel titleLabel = new JLabel("Employee Timesheet App");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginPanel.add(titleLabel);
        loginPanel.add(Box.createVerticalStrut(20));

        JPanel usernamePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        usernamePanel.add(new JLabel("Username:"));
        usernameField = new JTextField(15);
        usernamePanel.add(usernameField);
        loginPanel.add(usernamePanel);
        loginPanel.add(Box.createVerticalStrut(10));

        JPanel passwordPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        passwordPanel.add(new JLabel("Password:"));
        passwordField = new JPasswordField(15);
        passwordPanel.add(passwordField);
        loginPanel.add(passwordPanel);
        loginPanel.add(Box.createVerticalStrut(20));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        loginButton = new JButton("Login");
        signUpButton = new JButton("Sign Up");
        buttonPanel.add(loginButton);
        buttonPanel.add(signUpButton);
        loginPanel.add(buttonPanel);

        add(loginPanel, BorderLayout.CENTER);

        loginButton.addActionListener(e -> {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            authenticate(username, password);
        });

        signUpButton.addActionListener(e -> showSignUpScreen());

        setVisible(true);
    }

    private void authenticate(String username, String password) {
        try {
            Connection conn = dbManager.getConnection();
            String sql = "SELECT role FROM users WHERE username = ? AND password = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String role = rs.getString("role");
                System.out.println("Retrieved role: " + role); // Debug statement
                switch (role.toLowerCase()) { // Convert to lower case for case-insensitive comparison
                    case "employee":
                        // Check if the employee record exists in the employees table
                        String checkEmployeeQuery = "SELECT COUNT(*) FROM employees WHERE employee_id = ?";
                        PreparedStatement checkEmployeeStmt = conn.prepareStatement(checkEmployeeQuery);
                        checkEmployeeStmt.setString(1, username);
                        ResultSet employeeRS = checkEmployeeStmt.executeQuery();
                        employeeRS.next();
                        int employeeCount = employeeRS.getInt(1);

                        if (employeeCount == 0) {
                            // Employee record does not exist, add it to the employees table
                            String addEmployeeQuery = "INSERT INTO employees (employee_id, name, department) VALUES (?, 'Default Name', 'Default Department')";
                            PreparedStatement addEmployeeStmt = conn.prepareStatement(addEmployeeQuery);
                            addEmployeeStmt.setString(1, username);
                            addEmployeeStmt.executeUpdate();
                        }

                        new EmployeeTimesheet(dbManager, username).setVisible(true);
                        break;
                    case "manager":
                        new ManagerDashboard(dbManager).setVisible(true);
                        break;
                    default:
                        JOptionPane.showMessageDialog(this, "Unknown role: " + role, "Error", JOptionPane.ERROR_MESSAGE);
                        break;
                }
                this.dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Invalid login credentials.", "Error", JOptionPane.ERROR_MESSAGE);
            }

            conn.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Database error during authentication.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showSignUpScreen() {
        new SignUpScreen(dbManager, this);
    }
    private void addEmployeeToDatabase (String username, String name, String department){
        try {
            Connection conn = dbManager.getConnection();
            String sql = "INSERT INTO employees (employee_id, name, department) VALUES (?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);
            stmt.setString(2, name);
            stmt.setString(3, department);
            stmt.executeUpdate();
            conn.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
            // Handle the error
        }
    }

    class EmployeeTimesheet extends JFrame {
        private final DatabaseManager dbManager;
        private final String employeeUsername;
        private JTable timesheetTable;
        private JButton addTimeEntryButton, startTimeButton, endTimeButton;
        private LocalDateTime startTime;
        private LocalDateTime endTime;

        public EmployeeTimesheet(DatabaseManager dbManager, String employeeUsername) {
            this.dbManager = dbManager;
            this.employeeUsername = employeeUsername;

            setTitle("Employee Timesheet");
            setSize(400, 300);
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout(10, 10));

            timesheetTable = new JTable();
            JScrollPane scrollPane = new JScrollPane(timesheetTable);
            add(scrollPane, BorderLayout.CENTER);

            addTimeEntryButton = new JButton("Add Time Entry");

            double durationHours=0;
            addTimeEntryButton.addActionListener(e -> addTimeEntry(startTime, endTime, durationHours));

            startTimeButton = new JButton("Start Time");
            endTimeButton = new JButton("End Time");
            startTimeButton.addActionListener(e -> startTimeButtonClicked());
            endTimeButton.addActionListener(e -> endTimeButtonClicked());

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            buttonPanel.add(startTimeButton);
            buttonPanel.add(endTimeButton);
            //buttonPanel.add(addTimeEntryButton);
            add(buttonPanel, BorderLayout.SOUTH);

            loadTimesheetData();
        }

        private void loadTimesheetData() {
            try {
                Connection conn = dbManager.getConnection();
                String sql = "SELECT start_time, end_time, TIMESTAMPDIFF(MINUTE, start_time, end_time) / 60.0 AS duration FROM timesheet WHERE employee_id = ?";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setString(1, employeeUsername);
                ResultSet rs = stmt.executeQuery();

                DefaultTableModel model = new DefaultTableModel();
                model.addColumn("Start Time");
                model.addColumn("End Time");
                model.addColumn("Duration (hours)");

                while (rs.next()) {
                    String startTime = rs.getString("start_time");
                    String endTime = rs.getString("end_time");
                    double duration = rs.getDouble("duration");
                    model.addRow(new Object[]{startTime, endTime, duration});
                }

                timesheetTable.setModel(model);
                conn.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error loading timesheet data.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void startTimeButtonClicked() {
            if (startTime == null) {
                startTime = LocalDateTime.now();
                JOptionPane.showMessageDialog(this, "Start time recorded: " + startTime, "Start Time", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Start time has already been recorded.", "Start Time", JOptionPane.WARNING_MESSAGE);
            }
        }

        private void endTimeButtonClicked() {
            if (startTime != null && endTime == null) {
                endTime = LocalDateTime.now();
                Duration duration = Duration.between(startTime, endTime);
                double durationHours = (double) duration.toMinutes() / 60;
                JOptionPane.showMessageDialog(this, "End time recorded: " + endTime + "\nDuration: " + durationHours + " hours", "End Time", JOptionPane.INFORMATION_MESSAGE);
                addTimeEntry(startTime, endTime, durationHours);
                startTime = null;
                endTime = null;
            } else if (startTime == null) {
                JOptionPane.showMessageDialog(this, "Start time has not been recorded yet.", "End Time", JOptionPane.WARNING_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "End time has already been recorded.", "End Time", JOptionPane.WARNING_MESSAGE);
            }
        }
        private void addTimeEntry(LocalDateTime startTime, LocalDateTime endTime, double durationHours) {
            try {
                Connection conn = dbManager.getConnection();

                // Check if the employee exists
                String checkEmployeeQuery = "SELECT COUNT(*) FROM employees WHERE employee_id = ?";
                PreparedStatement checkEmployeeStmt = conn.prepareStatement(checkEmployeeQuery);
                checkEmployeeStmt.setString(1, employeeUsername);
                ResultSet rs = checkEmployeeStmt.executeQuery();
                rs.next();
                int employeeCount = rs.getInt(1);

                if (employeeCount > 0) {
                    // Employee exists, proceed with adding the time entry
                    String sql = "INSERT INTO timesheet (employee_id, start_time, end_time) VALUES (?, ?, ?)";
                    PreparedStatement stmt = conn.prepareStatement(sql);

                    stmt.setString(1, employeeUsername);
                    stmt.setString(2, startTime.toString());
                    stmt.setString(3, endTime.toString());

                    stmt.executeUpdate();
                    conn.close();

                    loadTimesheetData();
                    JOptionPane.showMessageDialog(this, "Time entry added successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    // Employee does not exist, display an error message
                    JOptionPane.showMessageDialog(this, "Employee does not exist. Cannot add time entry.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error adding time entry.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    class SignUpScreen extends JFrame {
        private final DatabaseManager dbManager;
        private final EmployeeTimesheetApp employeeTimesheetApp;
        private JTextField usernameField, nameField, departmentField;
        private JPasswordField passwordField;
        private JComboBox<String> roleComboBox;

        public SignUpScreen(DatabaseManager dbManager, EmployeeTimesheetApp employeeTimesheetApp) {
            this.dbManager = dbManager;
            this.employeeTimesheetApp = employeeTimesheetApp;

            setTitle("Sign Up");
            setSize(400, 300); // Increase size if needed
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout(10, 10)); // Ensure proper layout setup

            JPanel signUpPanel = new JPanel();
            signUpPanel.setLayout(new BoxLayout(signUpPanel, BoxLayout.Y_AXIS));
            signUpPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // Padding

            // Check component spacing and alignment
            signUpPanel.add(createLabeledPanel("Username:", usernameField = new JTextField(15)));
            signUpPanel.add(createLabeledPanel("Name:", nameField = new JTextField(15)));
            signUpPanel.add(createLabeledPanel("Password:", passwordField = new JPasswordField(15)));
            signUpPanel.add(createLabeledPanel("Department:", departmentField = new JTextField(15))); // Ensure it's not empty

            JPanel rolePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            roleComboBox = new JComboBox<>(new String[]{"Employee", "Manager"});
            rolePanel.add(new JLabel("Role:"));
            rolePanel.add(roleComboBox);
            signUpPanel.add(rolePanel);

            // Ensure sufficient space for the button
            signUpPanel.add(Box.createVerticalStrut(20)); // Add or remove space if needed

            JButton signUpButton = new JButton("Sign Up");
            signUpButton.addActionListener(e -> signUpUser());
            signUpPanel.add(signUpButton); // Ensure it's added to the correct panel

            add(signUpPanel, BorderLayout.CENTER);
            setVisible(true); // Ensure the frame is visible
        }

        // Helper method to create a panel with a label and a text field
        private JPanel createLabeledPanel(String labelText, JComponent field) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            panel.add(new JLabel(labelText));
            panel.add(field);
            return panel;
        }

        private void signUpUser() {
            String username = usernameField.getText();
            String name = nameField.getText();
            String password = new String(passwordField.getPassword());
            String role = roleComboBox.getSelectedItem().toString();
            String department = departmentField.getText();

            if (username.isEmpty() || name.isEmpty() || password.isEmpty() || (role.equals("Employee") && department.isEmpty())) {
                JOptionPane.showMessageDialog(this, "Please fill in all required fields.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                Connection conn = dbManager.getConnection();
                // Check if the username already exists
                String checkQuery = "SELECT COUNT(*) FROM users WHERE username = ?";
                PreparedStatement checkStmt = conn.prepareStatement(checkQuery);
                checkStmt.setString(1, username);
                ResultSet rs = checkStmt.executeQuery();
                rs.next();
                int count = rs.getInt(1);

                if (count > 0) {
                    JOptionPane.showMessageDialog(this, "Username already exists.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Insert into users table
                String insertQuery = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
                PreparedStatement insertStmt = conn.prepareStatement(insertQuery);
                insertStmt.setString(1, username);
                insertStmt.setString(2, password);
                insertStmt.setString(3, role);
                insertStmt.executeUpdate();

                if (role.equals("Employee")) {
                    employeeTimesheetApp.addEmployeeToDatabase(username, name, department); // Add employee to the database
                }

                conn.close();
                JOptionPane.showMessageDialog(this, "Sign-up successful.", "Success", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error during sign-up.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    class ManagerDashboard extends JFrame {
        private final DatabaseManager dbManager;
        private JTable employeeTable;
        private JButton addEmployeeButton, deleteEmployeeButton, updateEmployeeButton, generateReportButton;

        public ManagerDashboard(DatabaseManager dbManager) {
            this.dbManager = dbManager;

            setTitle("Manager Dashboard");
            setSize(500, 400);
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout(10, 10));

            employeeTable = new JTable();
            JScrollPane scrollPane = new JScrollPane(employeeTable);
            add(scrollPane, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            addEmployeeButton = new JButton("Add Employee");
            updateEmployeeButton = new JButton("Update Employee");
            deleteEmployeeButton = new JButton("Delete Employee");
            generateReportButton = new JButton("Generate Report");

            buttonPanel.add(addEmployeeButton);
            buttonPanel.add(updateEmployeeButton);
            buttonPanel.add(deleteEmployeeButton);
            buttonPanel.add(generateReportButton);

            add(buttonPanel, BorderLayout.SOUTH);


            addEmployeeButton.addActionListener(e -> addEmployee());
            updateEmployeeButton.addActionListener(e -> updateEmployee());
            deleteEmployeeButton.addActionListener(e -> deleteEmployee());
            generateReportButton.addActionListener(e -> generateReport());

            loadEmployeeData();
        }

        private void loadEmployeeData() {
            try {
                Connection conn = dbManager.getConnection();
                String sql = "SELECT * FROM employees";
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);

                DefaultTableModel model = new DefaultTableModel();
                model.addColumn("Employee ID");
                model.addColumn("Name");
                model.addColumn("Department");

                while (rs.next()) {
                    String employeeId = rs.getString("employee_id");
                    String name = rs.getString("name");
                    String department = rs.getString("department");
                    model.addRow(new Object[]{employeeId, name, department});
                }

                employeeTable.setModel(model);
                conn.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error loading employee data.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void addEmployee() {
            String employeeId = JOptionPane.showInputDialog(this, "Enter Employee ID:");
            String employeeName = JOptionPane.showInputDialog(this, "Enter Employee Name:");
            String employeeDepartment = JOptionPane.showInputDialog(this, "Enter Employee Department:");

            if (employeeId != null && employeeName != null && employeeDepartment != null) {
                try {
                    Connection conn = dbManager.getConnection();
                    String sql = "INSERT INTO employees (employee_id, name, department) VALUES (?, ?, ?)";
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    stmt.setString(1, employeeId);
                    stmt.setString(2, employeeName);
                    stmt.setString(3, employeeDepartment);
                    stmt.executeUpdate();

                    conn.close();

                    loadEmployeeData();
                    JOptionPane.showMessageDialog(this, "Employee added successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Error adding employee.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Invalid input. Please provide all employee details.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void updateEmployee() {
            int selectedRow = employeeTable.getSelectedRow();
            if (selectedRow >= 0) {
                String employeeId = (String) employeeTable.getValueAt(selectedRow, 0);
                String employeeName = (String) employeeTable.getValueAt(selectedRow, 1);
                String employeeDepartment = (String) employeeTable.getValueAt(selectedRow, 2);

                JPanel updatePanel = new JPanel(new GridLayout(3, 2));
                JTextField nameField = new JTextField(employeeName);
                JTextField departmentField = new JTextField(employeeDepartment);

                updatePanel.add(new JLabel("Employee Name:"));
                updatePanel.add(nameField);
                updatePanel.add(new JLabel("Employee Department:"));
                updatePanel.add(departmentField);

                int result = JOptionPane.showConfirmDialog(
                        this,
                        updatePanel,
                        "Update Employee",
                        JOptionPane.OK_CANCEL_OPTION
                );

                if (result == JOptionPane.OK_OPTION) {
                    try {
                        Connection conn = dbManager.getConnection();
                        String sql = "UPDATE employees SET name = ?, department = ? WHERE employee_id = ?";
                        PreparedStatement stmt = conn.prepareStatement(sql);
                        stmt.setString(1, nameField.getText());
                        stmt.setString(2, departmentField.getText());
                        stmt.setString(3, employeeId);
                        stmt.executeUpdate();

                        loadEmployeeData(); // Refresh the table with updated data
                        JOptionPane.showMessageDialog(this, "Employee updated successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);

                        conn.close();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(this, "Error updating employee.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } else {
                JOptionPane.showMessageDialog(this, "No employee selected.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }


        private void deleteEmployee() {
            int selectedRow = employeeTable.getSelectedRow();
            if (selectedRow >= 0) {
                String employeeId = (String) employeeTable.getValueAt(selectedRow, 0);

                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        "Are you sure you want to delete this employee?",
                        "Delete Confirmation",
                        JOptionPane.YES_NO_OPTION
                );

                if (confirm == JOptionPane.YES_OPTION) {
                    try {
                        Connection conn = dbManager.getConnection();
                        String sql = "DELETE FROM employees WHERE employee_id = ?";
                        PreparedStatement stmt = conn.prepareStatement(sql);
                        stmt.setString(1, employeeId);
                        stmt.executeUpdate();

                        conn.close();

                        loadEmployeeData();
                        JOptionPane.showMessageDialog(this, "Employee deleted successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(this, "Error deleting employee.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } else {
                JOptionPane.showMessageDialog(this, "No employee selected.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void generateReport() {
            try {
                Connection conn = dbManager.getConnection();
                String sql = "SELECT employee_id, SUM(TIMESTAMPDIFF(MINUTE, start_time, end_time)) / 60.0 AS total_hours "
                        + "FROM timesheet "
                        + "WHERE end_time IS NOT NULL "
                        + "GROUP BY employee_id";
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);

                if (!rs.isBeforeFirst()) { // No data found
                    JOptionPane.showMessageDialog(this, "No report data available.", "Info", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                StringBuilder report = new StringBuilder("Employee Timesheet Report:\n");
                while (rs.next()) {
                    String employeeId = rs.getString("employee_id");
                    double totalHours = rs.getDouble("total_hours");
                    report.append("Employee ID: ").append(employeeId).append(", Total Hours Worked: ").append(totalHours).append("\n");
                }

                JOptionPane.showMessageDialog(this, report.toString(), "Timesheet Report", JOptionPane.INFORMATION_MESSAGE);
                conn.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error generating report.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

        public static void main(String[] args) {
        DatabaseManager dbManager = new DatabaseManager(
                "jdbc:mysql://localhost:3306/timesheet_db",
                "root",
                "Akash@123");

        new EmployeeTimesheetApp(dbManager); // Pass DatabaseManager to the app
}


}