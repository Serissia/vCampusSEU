package com.vcampus.client.view.academic;

import com.vcampus.client.controller.AcademicController;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/**
 * 成绩查询与录入面板。
 */
public class GradePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /**
     * 构建成绩面板，当前先保留成绩表格与刷新按钮骨架。
     */
    public GradePanel(AcademicController controller) {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JButton("刷新成绩"));
        add(topPanel, BorderLayout.NORTH);

        JTable table = new JTable();
        add(new JScrollPane(table), BorderLayout.CENTER);
    }
}
